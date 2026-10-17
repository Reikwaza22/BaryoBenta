package com.baryobenta.community;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;

import okhttp3.MultipartBody;

/** "Sell an Item": posts a real listing (photo + specs) into PostgreSQL. */
public class AddListingActivity extends AppCompatActivity {

    private LinearLayout specsContainer;
    private TextView categoryText;
    private ImageView preview;
    private AppCompatButton postButton;

    private List<Category> categories;
    private Category selectedCategory;
    private Uri photoUri;

    private final ActivityResultLauncher<String> pickPhoto =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    photoUri = uri;
                    preview.setImageURI(uri);
                    preview.setVisibility(View.VISIBLE);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_listing);

        findViewById(R.id.button_add_listing_back).setOnClickListener(v -> finish());

        specsContainer = findViewById(R.id.container_specs);
        categoryText = findViewById(R.id.text_selected_category);
        preview = findViewById(R.id.image_upload_preview);
        postButton = findViewById(R.id.button_post_listing);
        postButton.setEnabled(false);

        findViewById(R.id.button_upload_photo).setOnClickListener(v -> pickPhoto.launch("image/*"));

        CategoryRepo.load(this, false, new CategoryRepo.Listener() {
            @Override
            public void onLoaded(List<Category> list) {
                categories = list;
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AddListingActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });

        findViewById(R.id.field_category).setOnClickListener(v -> {
            if (categories == null || categories.isEmpty()) {
                Toast.makeText(this, R.string.error_network, Toast.LENGTH_SHORT).show();
                return;
            }
            String[] names = new String[categories.size()];
            for (int i = 0; i < names.length; i++) names[i] = categories.get(i).getName();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.choose_category)
                    .setItems(names, (d, which) -> {
                        selectedCategory = categories.get(which);
                        categoryText.setText(selectedCategory.getName());
                        categoryText.setTextColor(getResources().getColor(R.color.text_primary));
                    })
                    .show();
        });

        addSpecRow();
        findViewById(R.id.button_add_spec).setOnClickListener(v -> addSpecRow());
        postButton.setOnClickListener(v -> post());
        checkWalletBeforeSelling();
    }

    private void checkWalletBeforeSelling() {
        ApiHelper.call(this, RetrofitClient.getApi(this).wallet(), new ApiHelper.Result<WalletData>() {
            @Override
            public void onSuccess(WalletData data, String message) {
                if (data.getBalance() >= data.getPostingFee()) {
                    postButton.setEnabled(true);
                } else {
                    showWalletRequired(getString(R.string.listing_wallet_insufficient_message,
                            (int) Math.round(data.getPostingFee())));
                }
            }

            @Override
            public void onError(String message) {
                showWalletRequired(getString(R.string.listing_wallet_required_message));
            }
        });
    }

    private void showWalletRequired(String message) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(R.string.listing_wallet_required_title)
                .setMessage(message)
                .setPositiveButton(R.string.btn_open_wallet, (dialog, which) -> {
                    startActivity(new android.content.Intent(this, WalletActivity.class));
                    overridePendingTransition(R.anim.slide_up_in, R.anim.slide_up_out);
                    finish();
                })
                .setNegativeButton(R.string.btn_cancel, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    private void addSpecRow() {
        final View row = LayoutInflater.from(this).inflate(R.layout.item_spec_row, specsContainer, false);
        row.findViewById(R.id.button_remove_spec).setOnClickListener(v -> specsContainer.removeView(row));
        specsContainer.addView(row);
    }

    private void post() {
        EditText titleInput = findViewById(R.id.input_listing_title);
        EditText priceInput = findViewById(R.id.input_listing_price);
        EditText descInput = findViewById(R.id.input_listing_description);

        final String title = titleInput.getText().toString().trim();
        final String price = priceInput.getText().toString().trim();
        final String desc = descInput.getText().toString().trim();

        if (title.isEmpty()) { titleInput.setError(getString(R.string.hint_item_title)); return; }
        if (selectedCategory == null) { Toast.makeText(this, R.string.error_choose_category, Toast.LENGTH_SHORT).show(); return; }
        if (price.isEmpty() || !price.matches("\\d+(\\.\\d{1,2})?")) { priceInput.setError(getString(R.string.error_enter_price)); return; }

        JsonArray specs = new JsonArray();
        for (int i = 0; i < specsContainer.getChildCount(); i++) {
            View row = specsContainer.getChildAt(i);
            String n = ((EditText) row.findViewById(R.id.input_spec_name)).getText().toString().trim();
            String v = ((EditText) row.findViewById(R.id.input_spec_value)).getText().toString().trim();
            if (!n.isEmpty() && !v.isEmpty()) {
                JsonObject o = new JsonObject();
                o.addProperty("name", n);
                o.addProperty("value", v);
                specs.add(o);
            }
        }
        final String specsJson = specs.toString();

        postButton.setEnabled(false);
        postButton.setText(R.string.posting);

        final android.content.Context app = getApplicationContext();
        new Thread(() -> {
            MultipartBody.Part photoPart = null;
            if (photoUri != null) {
                File f = ImageUtil.prepareJpeg(app, photoUri, "listing_upload");
                if (f != null) photoPart = ImageUtil.photoPart("photo", f);
            }
            final MultipartBody.Part part = photoPart;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                ApiHelper.call(this, RetrofitClient.getApi(this).createListing(
                                ImageUtil.text(title),
                                ImageUtil.text(String.valueOf(selectedCategory.getId())),
                                ImageUtil.text(price),
                                ImageUtil.text(desc),
                                ImageUtil.text(specsJson),
                                part),
                        new ApiHelper.Result<Object>() {
                            @Override
                            public void onSuccess(Object data, String message) {
                                Toast.makeText(AddListingActivity.this, R.string.posted_message, Toast.LENGTH_SHORT).show();
                                finish();
                            }

                            @Override
                            public void onError(String message) {
                                postButton.setEnabled(true);
                                postButton.setText(R.string.btn_post_listing);
                                String lower = message == null ? "" : message.toLowerCase(java.util.Locale.ROOT);
                                if (lower.contains("wallet") || lower.contains("credit") || lower.contains("top up")) {
                                    showWalletRequired(getString(R.string.listing_wallet_insufficient_message, 10));
                                } else {
                                    Toast.makeText(AddListingActivity.this, message, Toast.LENGTH_LONG).show();
                                }
                            }
                        });
            });
        }).start();
    }
}
