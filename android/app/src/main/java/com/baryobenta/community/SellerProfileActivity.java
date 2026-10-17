package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Public profile of a seller: rating, sales count and their active listings. */
public class SellerProfileActivity extends AppCompatActivity implements ListingGridAdapter.OnListingClickListener {

    public static final String EXTRA_USER_ID = "extra_user_id";

    private ListingGridAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seller_profile);
        findViewById(R.id.button_seller_back).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recycler_seller_listings);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new ListingGridAdapter(this);
        recycler.setAdapter(adapter);

        final int userId = getIntent().getIntExtra(EXTRA_USER_ID, 0);

        ApiHelper.call(this, RetrofitClient.getApi(this).profile(userId), new ApiHelper.Result<ProfileData>() {
            @Override
            public void onSuccess(ProfileData p, String message) {
                ((TextView) findViewById(R.id.text_seller_name)).setText(p.getFullName());
                ((TextView) findViewById(R.id.text_seller_location)).setText(p.getLocation());
                findViewById(R.id.icon_seller_verified).setVisibility(p.isVerified() ? View.VISIBLE : View.GONE);
                ((TextView) findViewById(R.id.text_seller_stats)).setText(p.getReviewCount() > 0
                        ? getString(R.string.stats_rating_format, p.getListingCount(), p.getSoldCount(), p.getRating(), p.getReviewCount())
                        : getString(R.string.stats_format, p.getListingCount(), p.getSoldCount()));
            }

            @Override
            public void onError(String message) {
                Toast.makeText(SellerProfileActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });

        ApiHelper.call(this, RetrofitClient.getApi(this).listings(null, null, null, null, userId),
                new ApiHelper.Result<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> data, String message) {
                        adapter.setItems(data);
                        findViewById(R.id.text_empty_seller).setVisibility(data == null || data.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(SellerProfileActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent i = new Intent(this, ListingDetailsActivity.class);
        i.putExtra(ListingDetailsActivity.EXTRA_LISTING_ID, listing.getId());
        startActivity(i);
    }
}
