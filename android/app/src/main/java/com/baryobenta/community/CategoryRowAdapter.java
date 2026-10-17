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
 * Adapter for the full-width category rows on the Categories screen
 * (item_category_row.xml).
 */
public class CategoryRowAdapter extends RecyclerView.Adapter<CategoryRowAdapter.RowViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    private final List<Category> categories = new ArrayList<>();
    private final OnCategoryClickListener listener;

    public CategoryRowAdapter(OnCategoryClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<Category> items) {
        categories.clear();
        if (items != null) categories.addAll(items);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_row, parent, false);
        return new RowViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        Category category = categories.get(position);
        holder.icon.setImageResource(category.getIconResId());
        holder.name.setText(category.getName());
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

    static class RowViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name;

        RowViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.image_cat_row_icon);
            name = itemView.findViewById(R.id.text_cat_row_name);
        }
    }
}
