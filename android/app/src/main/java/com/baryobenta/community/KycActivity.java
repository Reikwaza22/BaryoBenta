package com.baryobenta.community;

import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;

import okhttp3.MultipartBody;

/**
 * Identity verification. The ID photo + details are stored on the server and
 * reviewed by an admin (admin.php). The badge only appears after approval.
 */
public class KycActivity extends AppCompatActivity {

    private Uri idPhotoUri;
    private ImageView idPreview;
    private TextView uploadStatus;
    private View formGroup;
    private View stateGroup;

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    idPhotoUri = uri;
                    idPreview.setImageURI(uri);
                    idPreview.setVisibility(View.VISIBLE);
                    uploadStatus.setText(R.string.photo_added);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kyc);

        findViewById(R.id.button_kyc_back).setOnClickListener(v -> finish());

        formGroup = findViewById(R.id.group_kyc_form);
        stateGroup = findViewById(R.id.group_kyc_verified);
        idPreview = findViewById(R.id.image_id_preview);
        uploadStatus = findViewById(R.id.text_upload_id_status);

        final EditText idType = findViewById(R.id.input_id_type);
        final EditText idNumber = findViewById(R.id.input_id_number);
        final View submit = findViewById(R.id.button_submit_verification);

        findViewById(R.id.button_upload_id).setOnClickListener(v -> pickPhoto.launch("image/*"));

        submit.setOnClickListener(v -> {
            if (idPhotoUri == null) {
                Toast.makeText(this, R.string.kyc_photo_required, Toast.LENGTH_SHORT).show();
                return;
            }
            final String type = idType.getText().toString().trim();
            final String number = idNumber.getText().toString().trim();
            if (TextUtils.isEmpty(type)) { idType.setError(getString(R.string.error_required_field)); return; }
            if (TextUtils.isEmpty(number)) { idNumber.setError(getString(R.string.error_required_field)); return; }

            submit.setEnabled(false);
            final android.content.Context app = getApplicationContext();
            new Thread(() -> {
                File f = ImageUtil.prepareJpeg(app, idPhotoUri, "kyc_upload");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    if (f == null) {
                        submit.setEnabled(true);
                        Toast.makeText(this, R.string.kyc_photo_required, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MultipartBody.Part part = ImageUtil.photoPart("id_photo", f);
                    ApiHelper.call(this, RetrofitClient.getApi(this).submitKyc(
                                    ImageUtil.text(type), ImageUtil.text(number), part),
                            new ApiHelper.Result<Object>() {
                                @Override
                                public void onSuccess(Object data, String message) {
                                    Toast.makeText(KycActivity.this, message, Toast.LENGTH_LONG).show();
                                    finish();
                                }

                                @Override
                                public void onError(String message) {
                                    submit.setEnabled(true);
                                    Toast.makeText(KycActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            });
                });
            }).start();
        });

        checkStatus();
    }

    private void checkStatus() {
        ApiHelper.call(this, RetrofitClient.getApi(this).profile(null), new ApiHelper.Result<ProfileData>() {
            @Override
            public void onSuccess(ProfileData p, String message) {
                TextView title = findViewById(R.id.text_kyc_state_title);
                TextView body = findViewById(R.id.text_kyc_state_body);
                if (p.isVerified()) {
                    title.setText(R.string.kyc_already_verified_title);
                    body.setText(R.string.kyc_already_verified_body);
                    formGroup.setVisibility(View.GONE);
                    stateGroup.setVisibility(View.VISIBLE);
                } else if ("pending".equals(p.getKycStatus())) {
                    title.setText(R.string.kyc_pending_title);
                    body.setText(R.string.kyc_pending_body);
                    formGroup.setVisibility(View.GONE);
                    stateGroup.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                Toast.makeText(KycActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
