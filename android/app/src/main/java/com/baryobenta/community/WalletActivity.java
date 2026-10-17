package com.baryobenta.community;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Digital credits wallet: sellers top up credits here. Credits are never used to
 * pay buyers online -- buyers pay cash at meetups. Every listing a seller posts
 * costs a flat fee (10 digital credits) deducted when the listing is published;
 * recording a sale is free.
 */
public class WalletActivity extends AppCompatActivity {

    private TextView textBalance;
    private TextView textCommission;
    private TextView textEmpty;
    private TextView textInstructions;
    private TextView textRequests;
    private EditText inputAmount;
    private EditText inputPaymentReference;
    private Spinner inputPaymentMethod;
    private View buttonTopUp;
    private LedgerAdapter adapter;
    private double postingFee = 10;
    private boolean toppingUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        textBalance = findViewById(R.id.text_wallet_balance);
        textCommission = findViewById(R.id.text_wallet_commission);
        textEmpty = findViewById(R.id.text_empty_wallet);
        textInstructions = findViewById(R.id.text_wallet_payment_instructions);
        textRequests = findViewById(R.id.text_wallet_requests);
        inputAmount = findViewById(R.id.input_topup_amount);
        inputPaymentReference = findViewById(R.id.input_payment_reference);
        inputPaymentMethod = findViewById(R.id.input_payment_method);
        buttonTopUp = findViewById(R.id.button_topup);

        findViewById(R.id.button_wallet_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler_wallet);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new LedgerAdapter();
        recycler.setAdapter(adapter);

        buttonTopUp.setOnClickListener(v -> topUp());
        load();
    }

    private void load() {
        ApiHelper.call(this, RetrofitClient.getApi(this).wallet(),
                new ApiHelper.Result<WalletData>() {
                    @Override
                    public void onSuccess(WalletData data, String message) {
                        bind(data);
                    }

                    @Override
                    public void onError(String message) {
                        textEmpty.setText(message);
                        textEmpty.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void bind(WalletData data) {
        postingFee = data.getPostingFee();
        textBalance.setText(Fmt.peso(data.getBalance()));
        textCommission.setText(getString(R.string.wallet_fee_note, (int) Math.round(postingFee)));
        List<WalletData.LedgerEntry> ledger = data.getLedger();
        adapter.setItems(ledger);
        String instructions = data.getPaymentInstructions();
        if (!instructions.isEmpty()) textInstructions.setText(instructions);
        StringBuilder requests = new StringBuilder();
        for (WalletData.TopupRequest request : data.getTopupRequests()) {
            if (requests.length() > 0) requests.append('\n');
            requests.append(getString(R.string.wallet_request_row,
                    Fmt.peso(request.getAmount()), request.getPaymentMethod(), request.getStatus(),
                    request.getPaymentReference()));
        }
        textRequests.setText(requests.toString());
        textRequests.setVisibility(requests.length() == 0 ? View.GONE : View.VISIBLE);
        textEmpty.setVisibility(ledger.isEmpty() && requests.length() == 0 ? View.VISIBLE : View.GONE);
    }

    private void topUp() {
        if (toppingUp) return;
        String amount = inputAmount.getText().toString().trim();
        if (!amount.matches("^\\d+(\\.\\d{1,2})?$")) {
            Toast.makeText(this, R.string.error_topup_amount, Toast.LENGTH_SHORT).show();
            return;
        }
        String paymentReference = inputPaymentReference.getText().toString().trim();
        if (paymentReference.length() < 3 || paymentReference.length() > 120) {
            inputPaymentReference.setError(getString(R.string.error_payment_reference));
            return;
        }
        toppingUp = true;
        buttonTopUp.setEnabled(false);
        String paymentMethod = inputPaymentMethod.getSelectedItem().toString();
        ApiHelper.call(this, RetrofitClient.getApi(this).topUp(amount, paymentMethod, paymentReference),
                new ApiHelper.Result<WalletData>() {
                    @Override
                    public void onSuccess(WalletData data, String message) {
                        toppingUp = false;
                        buttonTopUp.setEnabled(true);
                        inputAmount.setText("");
                        inputPaymentReference.setText("");
                        Toast.makeText(WalletActivity.this, message, Toast.LENGTH_SHORT).show();
                        bind(data);
                    }

                    @Override
                    public void onError(String message) {
                        toppingUp = false;
                        buttonTopUp.setEnabled(true);
                        Toast.makeText(WalletActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private class LedgerAdapter extends RecyclerView.Adapter<LedgerAdapter.Holder> {

        private final List<WalletData.LedgerEntry> items = new ArrayList<>();

        void setItems(List<WalletData.LedgerEntry> list) {
            items.clear();
            items.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_wallet_ledger, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            WalletData.LedgerEntry e = items.get(position);
            holder.note.setText(e.getNote());
            holder.date.setText(Fmt.dateOnly(e.getCreatedAt()));
            String sign = e.isCredit() ? "+" : "-";
            holder.amount.setText(String.format(Locale.US, "%s%s", sign, Fmt.peso(Math.abs(e.getAmount()))));
            holder.amount.setTextColor(getColor(e.isCredit() ? R.color.primary_dark : R.color.text_secondary));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView note;
            final TextView date;
            final TextView amount;

            Holder(@NonNull View itemView) {
                super(itemView);
                note = itemView.findViewById(R.id.text_ledger_note);
                date = itemView.findViewById(R.id.text_ledger_date);
                amount = itemView.findViewById(R.id.text_ledger_amount);
            }
        }
    }
}
