package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/** Home feed: search, categories and the newest available listings from PostgreSQL. */
public class DashboardActivity extends AppCompatActivity implements
        ListingGridAdapter.OnListingClickListener,
        CategoryCircleAdapter.OnCategoryClickListener {

    private ListingGridAdapter listingAdapter;
    private CategoryCircleAdapter categoryAdapter;
    private TextView emptyView;
    private DrawerLayout drawer;
    private View drawerPanel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        drawer = findViewById(R.id.dashboard_drawer);
        drawerPanel = findViewById(R.id.navigation_drawer);
        DrawerLayout.LayoutParams drawerParams = (DrawerLayout.LayoutParams) drawerPanel.getLayoutParams();
        drawerParams.width = getResources().getDisplayMetrics().widthPixels / 2;
        drawerPanel.setLayoutParams(drawerParams);
        findViewById(R.id.button_menu).setOnClickListener(v -> drawer.openDrawer(drawerPanel));
        findViewById(R.id.button_notifications).setOnClickListener(v ->
                BottomNavHelper.navigate(this, BottomNavHelper.Tab.HOME,
                        BottomNavHelper.Tab.MESSAGES, MessagesActivity.class));
        findViewById(R.id.row_location).setOnClickListener(v ->
                startActivity(new Intent(this, CategoriesActivity.class)));

        EditText search = findViewById(R.id.input_search_dashboard);
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                Intent i = new Intent(this, ListingActivity.class);
                i.putExtra(ListingActivity.EXTRA_QUERY, v.getText().toString().trim());
                startActivity(i);
                return true;
            }
            return false;
        });

        emptyView = findViewById(R.id.text_empty_listings);

        RecyclerView categoryRecycler = findViewById(R.id.recycler_categories);
        categoryRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        categoryAdapter = new CategoryCircleAdapter(this);
        categoryRecycler.setAdapter(categoryAdapter);

        RecyclerView listingRecycler = findViewById(R.id.recycler_nearby_listings);
        listingRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        listingRecycler.setNestedScrollingEnabled(false);
        listingAdapter = new ListingGridAdapter(this);
        listingRecycler.setAdapter(listingAdapter);

        BottomNavHelper.setup(this, BottomNavHelper.Tab.HOME);
        setupDrawerItems();

        CategoryRepo.load(this, false, new CategoryRepo.Listener() {
            @Override
            public void onLoaded(List<Category> categories) {
                categoryAdapter.setItems(categories);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(DashboardActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupDrawerItems() {
        findViewById(R.id.drawer_home).setOnClickListener(v -> drawer.closeDrawer(drawerPanel));
        findViewById(R.id.drawer_browse).setOnClickListener(v -> drawerNavigate(BottomNavHelper.Tab.BROWSE, ListingActivity.class));
        findViewById(R.id.drawer_sell).setOnClickListener(v -> drawerNavigate(BottomNavHelper.Tab.SELL, AddListingActivity.class));
        findViewById(R.id.drawer_messages).setOnClickListener(v -> drawerNavigate(BottomNavHelper.Tab.MESSAGES, MessagesActivity.class));
        findViewById(R.id.drawer_profile).setOnClickListener(v -> drawerNavigate(BottomNavHelper.Tab.PROFILE, ProfileActivity.class));
        findViewById(R.id.drawer_wallet).setOnClickListener(v -> {
            drawer.closeDrawer(drawerPanel);
            startActivity(new Intent(this, WalletActivity.class));
            overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out);
        });
    }

    private void drawerNavigate(BottomNavHelper.Tab destination, Class<? extends android.app.Activity> activityClass) {
        drawer.closeDrawer(drawerPanel);
        BottomNavHelper.navigate(this, BottomNavHelper.Tab.HOME, destination, activityClass);
    }

    @Override
    protected void onResume() {
        super.onResume();
        String name = SessionManager.getName(this);
        TextView welcome = findViewById(R.id.text_welcome);
        welcome.setText(name.isEmpty() ? getString(R.string.welcome_message)
                : getString(R.string.welcome_named, name.split(" ")[0]));
        ((TextView) findViewById(R.id.text_location)).setText(SessionManager.getLocation(this));
        loadListings();
    }

    private void loadListings() {
        ApiHelper.call(this, RetrofitClient.getApi(this).listings(null, null, null, null, null),
                new ApiHelper.Result<List<Listing>>() {
                    @Override
                    public void onSuccess(List<Listing> data, String message) {
                        listingAdapter.setItems(data);
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

    @Override
    public void onCategoryClick(Category category) {
        Intent i = new Intent(this, ListingActivity.class);
        i.putExtra(ListingActivity.EXTRA_CATEGORY_ID, category.getId());
        startActivity(i);
    }
}
