package com.baryobenta.community;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Sales and purchases recorded in the transactions table. */
public class TransactionsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transactions);
        findViewById(R.id.button_transactions_back).setOnClickListener(v -> finish());

        final TextView empty = findViewById(R.id.text_empty_transactions);
        RecyclerView recycler = findViewById(R.id.recycler_transactions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        final TransactionAdapter adapter = new TransactionAdapter();
        recycler.setAdapter(adapter);

        ApiHelper.call(this, RetrofitClient.getApi(this).transactions(),
                new ApiHelper.Result<List<TransactionItem>>() {
                    @Override
                    public void onSuccess(List<TransactionItem> data, String message) {
                        adapter.setItems(data);
                        empty.setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(String message) {
                        empty.setText(message);
                        empty.setVisibility(View.VISIBLE);
                    }
                });
    }
}
