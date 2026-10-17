package com.baryobenta.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Rows for the "My Listings" / product logs screen with a status chip (Available / Pending / Sold). */
public class MyListingAdapter extends RecyclerView.Adapter<MyListingAdapter.MyListingViewHolder> {

    public interface OnListingClickListener {
        void onListingClick(Listing listing);
    }

    private final List<Listing> listings = new ArrayList<>();
    private final OnListingClickListener listener;

    public MyListingAdapter(OnListingClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Listing> items) {
        listings.clear();
        if (items != null) listings.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MyListingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_my_listing, parent, false);
        return new MyListingViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MyListingViewHolder holder, int position) {
        final Listing listing = listings.get(position);
        holder.title.setText(listing.getTitle());
        holder.price.setText(listing.getPriceLabel());
        Fmt.styleStatus(holder.status, listing.getStatus());

        if (listing.getImageUrl() != null) {
            holder.placeholder.setVisibility(View.GONE);
            Glide.with(holder.itemView).load(listing.getImageUrl()).centerCrop().into(holder.photo);
        } else {
            Glide.with(holder.itemView).clear(holder.photo);
            holder.photo.setImageDrawable(null);
            holder.placeholder.setVisibility(View.VISIBLE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onListingClick(listing);
        });
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static class MyListingViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView price;
        final TextView status;
        final ImageView photo;
        final ImageView placeholder;

        MyListingViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_mylisting_title);
            price = itemView.findViewById(R.id.text_mylisting_price);
            status = itemView.findViewById(R.id.text_mylisting_status);
            photo = itemView.findViewById(R.id.image_mylisting_photo);
            placeholder = itemView.findViewById(R.id.image_mylisting_placeholder);
        }
    }
}
