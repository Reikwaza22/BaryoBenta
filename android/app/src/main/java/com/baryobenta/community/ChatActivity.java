package com.baryobenta.community;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Buyer-seller chat for one listing. New messages are pulled every few seconds
 * while the screen is open. Messages can be text, photos (camera or gallery)
 * or voice notes, and any message can carry one emoji reaction.
 */
public class ChatActivity extends AppCompatActivity implements ChatAdapter.Listener {

    public static final String EXTRA_LISTING_ID = "extra_listing_id";
    public static final String EXTRA_OTHER_ID = "extra_other_id";
    public static final String EXTRA_OTHER_NAME = "extra_other_name";
    public static final String EXTRA_LISTING_TITLE = "extra_listing_title";

    private static final long POLL_MS = 4000;
    private static final String[] REACTIONS = {"👍", "❤️", "😂", "😮", "😢", "🙏", "🔥"};
    private static final String[] REACTION_LABELS = {"Like", "Love", "Haha", "Wow", "Sad", "Thanks", "Fire"};

    private int listingId;
    private int otherId;
    private ChatAdapter adapter;
    private RecyclerView recycler;
    private TextView emptyView;
    private EditText input;
    private ImageView voiceButton;
    private TextView recordingHint;
    private boolean sending = false;

    private MediaRecorder recorder;
    private File recordingFile;
    private long recordStartMs;
    private boolean recording = false;

    private MediaPlayer player;
    private boolean playingVoice = false;

    private Uri cameraUri;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    private final ActivityResultLauncher<String> pickGallery =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) sendPhoto(uri);
            });

    private final ActivityResultLauncher<Uri> takePicture =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), ok -> {
                if (ok && cameraUri != null) sendPhoto(cameraUri);
            });

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startRecording();
                else Toast.makeText(this, R.string.error_voice_permission, Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        listingId = getIntent().getIntExtra(EXTRA_LISTING_ID, 0);
        otherId = getIntent().getIntExtra(EXTRA_OTHER_ID, 0);

        ((TextView) findViewById(R.id.text_chat_name)).setText(getIntent().getStringExtra(EXTRA_OTHER_NAME));
        ((TextView) findViewById(R.id.text_chat_listing)).setText(getIntent().getStringExtra(EXTRA_LISTING_TITLE));
        findViewById(R.id.button_chat_back).setOnClickListener(v -> finish());

        emptyView = findViewById(R.id.text_chat_empty);
        input = findViewById(R.id.input_chat_message);
        voiceButton = findViewById(R.id.button_chat_voice);
        recordingHint = findViewById(R.id.text_recording_hint);

        recycler = findViewById(R.id.recycler_chat);
        LinearLayoutManager lm = new LinearLayoutManager(this);
        lm.setStackFromEnd(true);
        recycler.setLayoutManager(lm);
        adapter = new ChatAdapter(this);
        recycler.setAdapter(adapter);

        findViewById(R.id.button_chat_send).setOnClickListener(v -> send());
        findViewById(R.id.button_chat_photo).setOnClickListener(v -> showPhotoChooser());
        voiceButton.setOnClickListener(v -> toggleVoice());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(poll);
        if (recording) discardRecording();
        if (playingVoice) stopPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releaseRecorder();
        stopPlayback();
    }

    private void show(List<ChatItem> items) {
        int before = adapter.lastMessageId();
        adapter.setItems(items);
        boolean empty = items == null || items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (!empty && adapter.lastMessageId() != before) {
            recycler.scrollToPosition(adapter.getItemCount() - 1);
        }
    }

    private void refresh(boolean showErrors) {
        if (sending) return;
        ApiHelper.call(this, RetrofitClient.getApi(this).messages(listingId, otherId),
                new ApiHelper.Result<List<ChatItem>>() {
                    @Override
                    public void onSuccess(List<ChatItem> data, String message) {
                        if (!sending) show(data);
                    }

                    @Override
                    public void onError(String message) {
                        if (showErrors) Toast.makeText(ChatActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void send() {
        String body = input.getText().toString().trim();
        if (TextUtils.isEmpty(body) || sending) return;
        sending = true;
        final View sendBtn = findViewById(R.id.button_chat_send);
        sendBtn.setEnabled(false);
        ApiHelper.call(this, RetrofitClient.getApi(this).sendMessage(listingId, otherId, body),
                new ApiHelper.Result<List<ChatItem>>() {
                    @Override
                    public void onSuccess(List<ChatItem> data, String message) {
                        sending = false;
                        sendBtn.setEnabled(true);
                        input.setText("");
                        show(data);
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        sendBtn.setEnabled(true);
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---- reactions ----

    @Override
    public void onReactRequested(ChatItem item) {
        final boolean canRemove = !item.getReaction().isEmpty();
        String[] rows = new String[REACTIONS.length + (canRemove ? 1 : 0)];
        for (int i = 0; i < REACTIONS.length; i++) rows[i] = REACTIONS[i] + "  " + REACTION_LABELS[i];
        if (canRemove) rows[rows.length - 1] = getString(R.string.reaction_remove);

        new AlertDialog.Builder(this)
                .setTitle(R.string.reaction_title)
                .setItems(rows, (d, which) -> {
                    String reaction;
                    if (which < REACTIONS.length) {
                        reaction = REACTIONS[which].equals(item.getReaction()) ? "" : REACTIONS[which];
                    } else {
                        reaction = "";
                    }
                    sendReaction(item.getId(), reaction);
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void sendReaction(int messageId, String reaction) {
        ApiHelper.call(this, RetrofitClient.getApi(this)
                        .react(listingId, otherId, "react", messageId, reaction),
                new ApiHelper.Result<List<ChatItem>>() {
                    @Override
                    public void onSuccess(List<ChatItem> data, String message) {
                        show(data);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    // ---- photos ----

    private void showPhotoChooser() {
        String[] options = {getString(R.string.chat_take_photo), getString(R.string.chat_choose_gallery)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.chat_send_photo_title)
                .setItems(options, (d, which) -> {
                    if (which == 0) launchCamera();
                    else pickGallery.launch("image/*");
                })
                .setNegativeButton(R.string.btn_cancel, null)
                .show();
    }

    private void launchCamera() {
        File dir = new File(getCacheDir(), "chat");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        File photo = new File(dir, "camera_" + System.currentTimeMillis() + ".jpg");
        cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
        takePicture.launch(cameraUri);
    }

    private void sendPhoto(Uri uri) {
        if (sending) return;
        sending = true;
        new Thread(() -> {
            File prepared = ImageUtil.prepareJpeg(ChatActivity.this, uri,
                    "chat_" + System.currentTimeMillis());
            runOnUiThread(() -> {
                if (prepared == null || !prepared.exists()) {
                    sending = false;
                    Toast.makeText(ChatActivity.this, R.string.chat_photo_error, Toast.LENGTH_SHORT).show();
                } else {
                    uploadMedia("photo", prepared, 0);
                }
            });
        }).start();
    }

    // ---- voice notes ----

    private void toggleVoice() {
        if (sending) return;
        if (recording) {
            stopAndSendRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        try {
            File dir = new File(getCacheDir(), "voice");
            if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            recordingFile = new File(dir, "note_" + System.currentTimeMillis() + ".m4a");

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setOutputFile(recordingFile.getAbsolutePath());
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(44100);
            recorder.setAudioChannels(1);
            recorder.setAudioEncodingBitRate(64000);
            recorder.prepare();
            recorder.start();
            recordStartMs = System.currentTimeMillis();
            recording = true;
            voiceButton.setColorFilter(getColor(R.color.accent));
            recordingHint.setVisibility(View.VISIBLE);
        } catch (Exception e) {
            releaseRecorder();
            Toast.makeText(this, R.string.error_recording, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopAndSendRecording() {
        long elapsed = System.currentTimeMillis() - recordStartMs;
        try {
            recorder.stop();
        } catch (RuntimeException ignored) {
            // stop() throws when nothing was captured
        }
        releaseRecorder();
        recording = false;
        voiceButton.setColorFilter(getColor(R.color.text_secondary));
        recordingHint.setVisibility(View.GONE);

        int duration = (int) (elapsed / 1000L);
        if (recordingFile == null || !recordingFile.exists() || duration < 1
                || recordingFile.length() < 500) {
            if (recordingFile != null) //noinspection ResultOfMethodCallIgnored
                recordingFile.delete();
            Toast.makeText(this, R.string.voice_too_short, Toast.LENGTH_SHORT).show();
            return;
        }
        uploadMedia("voice", recordingFile, Math.min(120, duration));
    }

    private void discardRecording() {
        try {
            if (recorder != null) recorder.stop();
        } catch (RuntimeException ignored) {
        }
        releaseRecorder();
        recording = false;
        voiceButton.setColorFilter(getColor(R.color.text_secondary));
        recordingHint.setVisibility(View.GONE);
        if (recordingFile != null && recordingFile.exists()) //noinspection ResultOfMethodCallIgnored
            recordingFile.delete();
    }

    private void releaseRecorder() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            recorder = null;
        }
    }

    private void uploadMedia(String type, File file, int duration) {
        sending = true;
        String mime = "voice".equals(type) ? "audio/mp4" : "image/jpeg";
        RequestBody fileBody = RequestBody.create(MediaType.parse(mime), file);
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), fileBody);
        ApiHelper.call(this, RetrofitClient.getApi(this).sendMedia(
                        ImageUtil.text(String.valueOf(listingId)),
                        ImageUtil.text(String.valueOf(otherId)),
                        ImageUtil.text(type),
                        ImageUtil.text(String.valueOf(duration)),
                        part),
                new ApiHelper.Result<List<ChatItem>>() {
                    @Override
                    public void onSuccess(List<ChatItem> data, String message) {
                        sending = false;
                        show(data);
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }

                    @Override
                    public void onError(String message) {
                        sending = false;
                        Toast.makeText(ChatActivity.this, message, Toast.LENGTH_LONG).show();
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                    }
                });
    }

    // ---- voice playback ----

    @Override
    public void onPlayVoice(ChatItem item) {
        if (playingVoice) {
            stopPlayback();
            return;
        }
        String url = item.getPhotoUrl();
        if (TextUtils.isEmpty(url)) return;
        try {
            player = new MediaPlayer();
            player.setDataSource(url);
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> stopPlayback());
            player.setOnErrorListener((mp, what, extra) -> {
                stopPlayback();
                return true;
            });
            player.prepareAsync();
            playingVoice = true;
            adapter.setPlaying(item.getId());
        } catch (IOException e) {
            stopPlayback();
            Toast.makeText(this, R.string.error_recording, Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPlayback() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            player.release();
            player = null;
        }
        playingVoice = false;
        adapter.setPlaying(0);
    }
}
