package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Product logs: the signed-in user's own listings with their status. */
public class MyListingsActivity extends AppCompatActivity implements MyListingAdapter.OnListingClickListener {

    private MyListingAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_listings);

        findViewById(R.id.button_my_listings_back).setOnClickListener(v -> finish());
        emptyView = findViewById(R.id.text_empty_my_listings);

        RecyclerView recyclerView = findViewById(R.id.recycler_my_listings);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MyListingAdapter(this);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.fab_add_listing).setOnClickListener(v ->
                startActivity(new Intent(this, AddListingActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApiHelper.call(this, RetrofitClient.getApi(this).listings(null, null, 1, null, null),
                new ApiHelper.Result<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> data, String message) {
                        adapter.setItems(data);
                        emptyView.setText(R.string.empty_my_listings);
                        emptyView.setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(String message) {
                        emptyView.setText(message);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                });
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(this, ListingDetailsActivity.class);
        intent.putExtra(ListingDetailsActivity.EXTRA_LISTING_ID, listing.getId());
        startActivity(intent);
    }
}
