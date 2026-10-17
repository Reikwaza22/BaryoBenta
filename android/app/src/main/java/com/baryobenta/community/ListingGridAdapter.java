package com.baryobenta.community;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/** Binds real listings to the item_listing_grid.xml card (Home, Browse, Favorites, Seller profile). */
public class ListingGridAdapter extends RecyclerView.Adapter<ListingGridAdapter.GridViewHolder> {

    public interface OnListingClickListener {
        void onListingClick(Listing listing);

        /** Called after the heart was toggled on the server. */
        default void onFavoriteChanged(Listing listing) {
        }
    }

    private final List<Listing> listings = new ArrayList<>();
    private final OnListingClickListener listener;

    public ListingGridAdapter(OnListingClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Listing> items) {
        listings.clear();
        if (items != null) listings.addAll(items);
        notifyDataSetChanged();
    }

    public List<Listing> getItems() {
        return listings;
    }

    public void removeItem(Listing listing) {
        int index = listings.indexOf(listing);
        if (index >= 0) {
            listings.remove(index);
            notifyItemRemoved(index);
        }
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_listing_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull final GridViewHolder holder, int position) {
        final Listing listing = listings.get(position);
        final Context context = holder.itemView.getContext();

        holder.title.setText(listing.getTitle());
        holder.price.setText(listing.getPriceLabel());

        if (listing.getReviewCount() > 0) {
            holder.rating.setVisibility(View.VISIBLE);
            holder.rating.setText(context.getString(
                    R.string.rating_format, listing.getRating(), listing.getReviewCount()));
        } else {
            holder.rating.setVisibility(View.GONE);
        }
        holder.verifiedBadge.setVisibility(listing.isSellerVerified() ? View.VISIBLE : View.GONE);

        if ("available".equals(listing.getStatus())) {
            holder.status.setVisibility(View.GONE);
        } else {
            holder.status.setVisibility(View.VISIBLE);
            Fmt.styleStatus(holder.status, listing.getStatus());
        }

        if (listing.getImageUrl() != null) {
            holder.placeholder.setVisibility(View.GONE);
            Glide.with(holder.itemView).load(listing.getImageUrl()).centerCrop().into(holder.photo);
        } else {
            Glide.with(holder.itemView).clear(holder.photo);
            holder.photo.setImageDrawable(null);
            holder.placeholder.setVisibility(View.VISIBLE);
        }

        holder.favoriteIcon.setImageResource(listing.isFavorite()
                ? R.drawable.ic_favorite
                : R.drawable.ic_favorite_border);

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onListingClick(listing);
        });

        holder.favoriteIcon.setOnClickListener(v ->
                ApiHelper.call(context, RetrofitClient.getApi(context).toggleFavorite(listing.getId()),
                        new ApiHelper.Result<FavoriteState>() {
                            @Override
                            public void onSuccess(FavoriteState data, String message) {
                                listing.setFavorite(data != null && data.isFavorite());
                                int pos = holder.getBindingAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
                                if (listener != null) listener.onFavoriteChanged(listing);
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                            }
                        }));
    }

    @Override
    public int getItemCount() {
        return listings.size();
    }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView price;
        final TextView rating;
        final TextView status;
        final ImageView photo;
        final ImageView placeholder;
        final ImageView verifiedBadge;
        final ImageView favoriteIcon;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_grid_title);
            price = itemView.findViewById(R.id.text_grid_price);
            rating = itemView.findViewById(R.id.text_grid_rating);
            status = itemView.findViewById(R.id.text_grid_status);
            photo = itemView.findViewById(R.id.image_grid_photo);
            placeholder = itemView.findViewById(R.id.image_grid_item);
            verifiedBadge = itemView.findViewById(R.id.icon_grid_verified);
            favoriteIcon = itemView.findViewById(R.id.icon_grid_favorite);
        }
    }
}
