package com.baryobenta.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.TxHolder> {

    private final List<TransactionItem> items = new ArrayList<>();

    public void setItems(List<TransactionItem> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TxHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new TxHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TxHolder holder, int position) {
        TransactionItem t = items.get(position);
        holder.title.setText(t.getTitle());
        holder.amount.setText(Fmt.peso(t.getAmount()));
        holder.detail.setText(holder.itemView.getContext().getString(
                t.isSold() ? R.string.tx_sold_to : R.string.tx_bought_from,
                t.getOtherName(), Fmt.dateOnly(t.getCreatedAt())));
        holder.role.setText(t.isSold() ? R.string.tx_role_sold : R.string.tx_role_bought);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class TxHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView amount;
        final TextView detail;
        final TextView role;

        TxHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.text_tx_title);
            amount = itemView.findViewById(R.id.text_tx_amount);
            detail = itemView.findViewById(R.id.text_tx_detail);
            role = itemView.findViewById(R.id.text_tx_role);
        }
    }
}
