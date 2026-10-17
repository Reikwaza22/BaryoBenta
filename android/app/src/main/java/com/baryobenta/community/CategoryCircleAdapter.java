package com.baryobenta.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for the horizontal row of category circles shown on the
 * Home and Browse Listings screens (item_category_circle.xml).
 */
public class CategoryCircleAdapter extends RecyclerView.Adapter<CategoryCircleAdapter.CircleViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    private final List<Category> categories = new ArrayList<>();
    private final OnCategoryClickListener listener;
    /** 0 (or negative) = nothing selected, show every circle at full strength. */
    private int selectedId = 0;

    public CategoryCircleAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Category> items) {
        categories.clear();
        if (items != null) categories.addAll(items);
        notifyDataSetChanged();
    }

    public void setSelectedId(int selectedId) {
        this.selectedId = selectedId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public CircleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_circle, parent, false);
        return new CircleViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CircleViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.icon.setImageResource(category.getIconResId());
        holder.label.setText(category.getName());
        boolean dim = selectedId > 0 && category.getId() != selectedId;
        holder.itemView.setAlpha(dim ? 0.45f : 1f);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class CircleViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        CircleViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_category_icon);
            label = itemView.findViewById(R.id.text_category_label);
        }
    }
}
