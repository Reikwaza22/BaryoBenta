package com.baryobenta.community;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Browse: live search + category filter + sorting, all backed by PostgreSQL. */
public class ListingActivity extends AppCompatActivity implements
        ListingGridAdapter.OnListingClickListener,
        CategoryCircleAdapter.OnCategoryClickListener {

    public static final String EXTRA_CATEGORY_ID = "extra_category_id";
    public static final String EXTRA_QUERY = "extra_query";

    private ListingGridAdapter listingAdapter;
    private CategoryCircleAdapter categoryAdapter;
    private TextView emptyView;
    private EditText searchInput;

    private int categoryId = 0;
    private int sortMode = 0; // 0 newest, 1 price low, 2 price high
    private int requestCounter = 0;
    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listing);

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar_listing);
        toolbar.setNavigationOnClickListener(v -> finish());

        categoryId = getIntent().getIntExtra(EXTRA_CATEGORY_ID, 0);
        String initialQuery = getIntent().getStringExtra(EXTRA_QUERY);

        emptyView = findViewById(R.id.text_empty_browse);
        searchInput = findViewById(R.id.input_search_listing);
        if (initialQuery != null) searchInput.setText(initialQuery);

        RecyclerView categoryRecycler = findViewById(R.id.recycler_listing_categories);
        categoryRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryCircleAdapter(this);
        categoryAdapter.setSelectedId(categoryId);
        categoryRecycler.setAdapter(categoryAdapter);

        RecyclerView listingRecycler = findViewById(R.id.recycler_listings);
        listingRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        listingAdapter = new ListingGridAdapter(this);
        listingRecycler.setAdapter(listingAdapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (pendingSearch != null) handler.removeCallbacks(pendingSearch);
                pendingSearch = ListingActivity.this::loadListings;
                handler.postDelayed(pendingSearch, 350);
            }
        });

        findViewById(R.id.button_filter).setOnClickListener(v -> {
            String[] options = {getString(R.string.sort_newest), getString(R.string.sort_price_low), getString(R.string.sort_price_high)};
            new AlertDialog.Builder(this)
                    .setTitle(R.string.sort_title)
                    .setSingleChoiceItems(options, sortMode, (d, which) -> {
                        sortMode = which;
                        d.dismiss();
                        applySort();
                    })
                    .show();
        });

        BottomNavHelper.setup(this, BottomNavHelper.Tab.BROWSE);

        CategoryRepo.load(this, true, new CategoryRepo.Listener() {
            @Override
            public void onLoaded(List<Category> categories) {
                categoryAdapter.setItems(categories);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ListingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadListings();
    }

    private void loadListings() {
        final int myRequest = ++requestCounter;
        String q = searchInput.getText().toString().trim();
        ApiHelper.call(this, RetrofitClient.getApi(this).listings(
                        q.isEmpty() ? null : q, categoryId > 0 ? categoryId : null, null, null, null),
                new ApiHelper.Result<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> data, String message) {
                        if (myRequest != requestCounter) return; // a newer search is in flight
                        listingAdapter.setItems(data);
                        applySort();
                    }

                    @Override
                    public void onError(String message) {
                        if (myRequest != requestCounter) return;
                        emptyView.setText(message);
                        emptyView.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void applySort() {
        List<Listing> items = new ArrayList<>(listingAdapter.getItems());
        if (sortMode == 1) {
            Collections.sort(items, Comparator.comparingDouble(Listing::getPrice));
        } else if (sortMode == 2) {
            Collections.sort(items, (a, b) -> Double.compare(b.getPrice(), a.getPrice()));
        } else {
            Collections.sort(items, (a, b) -> Integer.compare(b.getId(), a.getId()));
        }
        listingAdapter.setItems(items);
        emptyView.setText(R.string.empty_browse);
        emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onListingClick(Listing listing) {
        Intent intent = new Intent(this, ListingDetailsActivity.class);
        intent.putExtra(ListingDetailsActivity.EXTRA_LISTING_ID, listing.getId());
        startActivity(intent);
    }

    @Override
    public void onCategoryClick(Category category) {
        // Tapping the selected category again (or "All") clears the filter.
        categoryId = (category.getId() == categoryId) ? 0 : category.getId();
        categoryAdapter.setSelectedId(categoryId);
        loadListings();
    }
}
