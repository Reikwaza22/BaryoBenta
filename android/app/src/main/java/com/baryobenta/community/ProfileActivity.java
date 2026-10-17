package com.baryobenta.community;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Account hub: live stats, edit profile, listings, favorites, history, verification, log out. */
public class ProfileActivity extends AppCompatActivity {

    private ProfileData profile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        findViewById(R.id.menu_my_listings).setOnClickListener(v ->
                startActivity(new Intent(this, MyListingsActivity.class)));
        findViewById(R.id.menu_wallet).setOnClickListener(v ->
                startActivity(new Intent(this, WalletActivity.class)));
        findViewById(R.id.menu_verify_account).setOnClickListener(v ->
                startActivity(new Intent(this, KycActivity.class)));
        findViewById(R.id.menu_favorites).setOnClickListener(v ->
                startActivity(new Intent(this, FavoritesActivity.class)));
        findViewById(R.id.menu_transactions).setOnClickListener(v ->
                startActivity(new Intent(this, TransactionsActivity.class)));
        findViewById(R.id.menu_messages).setOnClickListener(v ->
                BottomNavHelper.navigate(this, BottomNavHelper.Tab.PROFILE,
                        BottomNavHelper.Tab.MESSAGES, MessagesActivity.class));
        findViewById(R.id.menu_categories).setOnClickListener(v ->
                startActivity(new Intent(this, CategoriesActivity.class)));
        findViewById(R.id.menu_settings).setOnClickListener(v -> showEditDialog());

        findViewById(R.id.menu_logout).setOnClickListener(v -> logout());

        BottomNavHelper.setup(this, BottomNavHelper.Tab.PROFILE);

        ((TextView) findViewById(R.id.text_profile_name)).setText(SessionManager.getName(this));
        ((TextView) findViewById(R.id.text_profile_location)).setText(SessionManager.getLocation(this));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ApiHelper.call(this, RetrofitClient.getApi(this).profile(null), new ApiHelper.Result<ProfileData>() {
            @Override
            public void onSuccess(ProfileData data, String message) {
                profile = data;
                SessionManager.updateProfile(ProfileActivity.this, data.getFullName(), data.getLocation(), data.isVerified());
                bind();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bind() {
        ((TextView) findViewById(R.id.text_profile_name)).setText(profile.getFullName());
        ((TextView) findViewById(R.id.text_profile_location)).setText(profile.getLocation());
        ((TextView) findViewById(R.id.text_profile_stats)).setText(profile.getReviewCount() > 0
                ? getString(R.string.stats_rating_format, profile.getListingCount(), profile.getSoldCount(), profile.getRating(), profile.getReviewCount())
                : getString(R.string.stats_format, profile.getListingCount(), profile.getSoldCount()));
        ImageView badge = findViewById(R.id.icon_profile_verified);
        badge.setVisibility(profile.isVerified() ? View.VISIBLE : View.GONE);

        TextView verify = findViewById(R.id.text_menu_verify);
        if (profile.isVerified()) verify.setText(R.string.menu_verify_done);
        else if ("pending".equals(profile.getKycStatus())) verify.setText(R.string.menu_verify_pending);
        else verify.setText(R.string.menu_verify_account);
    }

    private void showEditDialog() {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);

        final EditText name = new EditText(this);
        name.setHint(R.string.hint_full_name);
        name.setText(SessionManager.getName(this));
        final EditText location = new EditText(this);
        location.setHint(R.string.hint_location);
        location.setText(SessionManager.getLocation(this));
        box.addView(name);
        box.addView(location);

        new AlertDialog.Builder(this)
                .setTitle(R.string.edit_profile_title)
                .setView(box)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String n = name.getText().toString().trim();
                    String l = location.getText().toString().trim();
                    if (n.isEmpty()) {
                        Toast.makeText(this, R.string.error_required_field, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ApiHelper.call(this, RetrofitClient.getApi(this).updateProfile(n, l),
                            new ApiHelper.Result<ProfileData>() {
                                @Override
                                public void onSuccess(ProfileData data, String message) {
                                    profile = data;
                                    SessionManager.updateProfile(ProfileActivity.this, data.getFullName(), data.getLocation(), data.isVerified());
                                    bind();
                                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(String message) {
                                    Toast.makeText(ProfileActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            });
                })
                .show();
    }

    private void logout() {
        final Runnable goToLogin = () -> {
            SessionManager.logout(this);
            CategoryRepo.clear();
            Intent intent = new Intent(this, AuthActivity.class);
            intent.putExtra(AuthActivity.EXTRA_MODE, AuthActivity.MODE_LOGIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        };
        // Tell the server to drop the token; leave regardless of the result.
        ApiHelper.call(this, RetrofitClient.getApi(this).logout(), new ApiHelper.Result<Object>() {
            @Override
            public void onSuccess(Object data, String message) {
                goToLogin.run();
            }

            @Override
            public void onError(String message) {
                goToLogin.run();
            }
        });
    }
}
