package com.baryobenta.community;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Chat bubbles: mine on the right (orange), theirs on the left (light orange).
 * Bubbles can be a text body, a photo or a voice note, and each message can
 * carry one emoji reaction shown in a chip under the bubble.
 */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.BubbleHolder> {

    /** React (long-press) and voice playback are handled by the activity. */
    public interface Listener {
        void onReactRequested(ChatItem item);
        void onPlayVoice(ChatItem item);
    }

    private final List<ChatItem> items = new ArrayList<>();
    private final Listener listener;
    private int playingMessageId = 0;

    public ChatAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<ChatItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    public int lastMessageId() {
        return items.isEmpty() ? 0 : items.get(items.size() - 1).getId();
    }

    public void setPlaying(int messageId) {
        this.playingMessageId = messageId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BubbleHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_bubble, parent, false);
        return new BubbleHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BubbleHolder holder, int position) {
        ChatItem item = items.get(position);
        boolean mine = item.isMine();
        android.content.Context ctx = holder.itemView.getContext();

        holder.body.setText(item.getBody());
        holder.time.setText(Fmt.timeAgo(item.getCreatedAt()));

        holder.column.setBackgroundResource(mine ? R.drawable.bg_bubble_mine : R.drawable.bg_bubble_theirs);
        int textColor = ctx.getResources().getColor(mine ? R.color.white : R.color.text_primary);
        holder.body.setTextColor(textColor);
        holder.time.setTextColor(ctx.getResources().getColor(mine ? R.color.primary_light : R.color.text_secondary));

        holder.body.setVisibility(!item.isPhoto() && !item.isVoice() ? View.VISIBLE : View.GONE);
        holder.photo.setVisibility(item.isPhoto() ? View.VISIBLE : View.GONE);
        holder.voiceRow.setVisibility(item.isVoice() ? View.VISIBLE : View.GONE);

        if (item.isPhoto()) {
            Glide.with(holder.photo).load(item.getPhotoUrl()).centerCrop().into(holder.photo);
        } else {
            Glide.with(holder.photo).clear(holder.photo);
            holder.photo.setImageDrawable(null);
        }

        if (item.isVoice()) {
            holder.duration.setText(String.format(Locale.US, "%d:%02d",
                    item.getDuration() / 60, item.getDuration() % 60));
            holder.duration.setTextColor(textColor);
            boolean playing = playingMessageId == item.getId();
            holder.play.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
            holder.play.setColorFilter(ctx.getResources().getColor(
                    mine ? R.color.white : R.color.primary_dark));
            holder.play.setOnClickListener(v -> listener.onPlayVoice(item));
        } else {
            holder.play.setOnClickListener(null);
        }

        String reaction = item.getReaction();
        boolean hasReaction = !reaction.isEmpty();
        holder.reaction.setVisibility(hasReaction ? View.VISIBLE : View.GONE);
        if (hasReaction) holder.reaction.setText(reaction);

        int gravity = mine ? Gravity.END : Gravity.START;
        LinearLayout.LayoutParams columnLp = (LinearLayout.LayoutParams) holder.column.getLayoutParams();
        columnLp.gravity = gravity;
        holder.column.setLayoutParams(columnLp);
        LinearLayout.LayoutParams reactionLp = (LinearLayout.LayoutParams) holder.reaction.getLayoutParams();
        reactionLp.gravity = gravity;
        holder.reaction.setLayoutParams(reactionLp);

        holder.itemView.setOnLongClickListener(v -> {
            listener.onReactRequested(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class BubbleHolder extends RecyclerView.ViewHolder {
        final LinearLayout column;
        final TextView body;
        final TextView time;
        final ImageView photo;
        final LinearLayout voiceRow;
        final ImageView play;
        final TextView duration;
        final TextView reaction;

        BubbleHolder(@NonNull View itemView) {
            super(itemView);
            column = itemView.findViewById(R.id.bubble_column);
            body = itemView.findViewById(R.id.text_bubble_body);
            time = itemView.findViewById(R.id.text_bubble_time);
            photo = itemView.findViewById(R.id.image_bubble_photo);
            voiceRow = itemView.findViewById(R.id.layout_bubble_voice);
            play = itemView.findViewById(R.id.button_bubble_play);
            duration = itemView.findViewById(R.id.text_bubble_duration);
            reaction = itemView.findViewById(R.id.text_bubble_reaction);
        }
    }
}
