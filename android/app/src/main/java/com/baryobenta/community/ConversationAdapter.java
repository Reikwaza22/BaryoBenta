package com.baryobenta.community;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conversation list on the Messages screen (one row per listing + person). */
public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.MessageViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(Conversation conversation);
    }

    private final List<Conversation> all = new ArrayList<>();
    private final List<Conversation> shown = new ArrayList<>();
    private final OnConversationClickListener listener;

    public ConversationAdapter(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Conversation> items) {
        all.clear();
        if (items != null) all.addAll(items);
        filter("");
    }

    public void filter(String query) {
        shown.clear();
        String q = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (Conversation c : all) {
            if (q.isEmpty()
                    || c.getOtherName().toLowerCase(Locale.US).contains(q)
                    || c.getListingTitle().toLowerCase(Locale.US).contains(q)
                    || c.getLastMessage().toLowerCase(Locale.US).contains(q)) {
                shown.add(c);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new MessageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        final Conversation c = shown.get(position);
        holder.name.setText(c.getOtherName());
        holder.listing.setText(c.getListingTitle());
        holder.preview.setText(c.getLastMessage());
        holder.time.setText(Fmt.timeAgo(c.getCreatedAt()));

        boolean unread = c.getUnread() > 0;
        holder.preview.setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
        holder.unread.setVisibility(unread ? View.VISIBLE : View.GONE);
        holder.unread.setText(String.valueOf(c.getUnread()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(c);
        });
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    static class MessageViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView listing;
        final TextView preview;
        final TextView time;
        final TextView unread;

        MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.text_message_name);
            listing = itemView.findViewById(R.id.text_message_listing);
            preview = itemView.findViewById(R.id.text_message_preview);
            time = itemView.findViewById(R.id.text_message_time);
            unread = itemView.findViewById(R.id.text_message_unread);
        }
    }
}
