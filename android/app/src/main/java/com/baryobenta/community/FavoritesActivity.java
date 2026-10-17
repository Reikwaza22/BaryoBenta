package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Items the user saved with the heart button. */
public class FavoritesActivity extends AppCompatActivity implements ListingGridAdapter.OnListingClickListener {

    private ListingGridAdapter adapter;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);
        findViewById(R.id.button_favorites_back).setOnClickListener(v -> finish());
        emptyView = findViewById(R.id.text_empty_favorites);

        RecyclerView recycler = findViewById(R.id.recycler_favorites);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ListingGridAdapter(this);
        recycler.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        ApiHelper.call(this, RetrofitClient.getApi(this).listings(null, null, null, 1, null),
                new ApiHelper.Result<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> data, String message) {
                        adapter.setItems(data);
                        emptyView.setText(R.string.empty_favorites);
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
        Intent i = new Intent(this, ListingDetailsActivity.class);
        i.putExtra(ListingDetailsActivity.EXTRA_LISTING_ID, listing.getId());
        startActivity(i);
    }

    @Override
    public void onFavoriteChanged(Listing listing) {
        if (!listing.isFavorite()) {
            adapter.removeItem(listing);
            emptyView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
        }
    }
}
