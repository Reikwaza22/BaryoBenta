package com.baryobenta.community;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

/**
 * Listing details: everything comes from PostgreSQL. Buyers can message,
 * favorite, comment, report and rate. The seller sees "Manage Listing"
 * (mark available / pending / sold, delete) instead of contact/report/rate.
 */
public class ListingDetailsActivity extends AppCompatActivity {

    public static final String EXTRA_LISTING_ID = "extra_listing_id";

    private int listingId;
    private Listing listing;

    private LinearLayout specsContainer;
    private LinearLayout commentsContainer;
    private TextView noComments;
    private EditText commentInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_listing_details);

        listingId = getIntent().getIntExtra(EXTRA_LISTING_ID, 0);
        specsContainer = findViewById(R.id.container_detail_specs);
        commentsContainer = findViewById(R.id.container_comments);
        noComments = findViewById(R.id.text_no_comments);
        commentInput = findViewById(R.id.input_comment);

        findViewById(R.id.button_details_back).setOnClickListener(v -> finish());
        findViewById(R.id.button_back).setOnClickListener(v -> finish());

        findViewById(R.id.button_send_comment).setOnClickListener(v -> sendComment());
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadListing();
        loadComments();
    }

    private void loadListing() {
        ApiHelper.call(this, RetrofitClient.getApi(this).listing(listingId), new ApiHelper.Result<Listing>() {
            @Override
            public void onSuccess(Listing data, String message) {
                listing = data;
                bind();
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                if (listing == null) finish();
            }
        });
    }

    private void bind() {
        ((TextView) findViewById(R.id.text_detail_title)).setText(listing.getTitle());
        ((TextView) findViewById(R.id.text_detail_price)).setText(listing.getPriceLabel());
        ((TextView) findViewById(R.id.text_detail_category)).setText(listing.getCategory());
        ((TextView) findViewById(R.id.text_detail_description)).setText(
                listing.getDescription().isEmpty() ? "—" : listing.getDescription());
        Fmt.styleStatus((TextView) findViewById(R.id.text_detail_status), listing.getStatus());

        TextView seller = findViewById(R.id.text_detail_seller);
        seller.setText(listing.getSeller());
        seller.setTextColor(getResources().getColor(R.color.primary_dark));
        seller.setOnClickListener(v -> {
            Intent i = new Intent(this, SellerProfileActivity.class);
            i.putExtra(SellerProfileActivity.EXTRA_USER_ID, listing.getSellerId());
            startActivity(i);
        });

        View ratingRow = findViewById(R.id.row_detail_rating);
        if (listing.getReviewCount() > 0) {
            ratingRow.setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.text_detail_rating)).setText(String.valueOf(listing.getRating()));
            ((TextView) findViewById(R.id.text_detail_review_count)).setText(
                    getString(R.string.review_count_format, listing.getReviewCount()));
        } else {
            ratingRow.setVisibility(View.GONE);
        }
        findViewById(R.id.icon_detail_verified).setVisibility(listing.isSellerVerified() ? View.VISIBLE : View.GONE);

        ImageView photo = findViewById(R.id.image_detail_photo);
        View placeholder = findViewById(R.id.image_detail);
        if (listing.getImageUrl() != null) {
            placeholder.setVisibility(View.GONE);
            Glide.with(this).load(listing.getImageUrl()).centerCrop().into(photo);
        } else {
            photo.setImageDrawable(null);
            placeholder.setVisibility(View.VISIBLE);
        }

        // Specifications
        specsContainer.removeAllViews();
        List<Spec> specs = listing.getSpecs() == null ? new ArrayList<Spec>() : listing.getSpecs();
        findViewById(R.id.label_detail_specs).setVisibility(specs.isEmpty() ? View.GONE : View.VISIBLE);
        for (Spec s : specs) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_spec_display, specsContainer, false);
            ((TextView) row.findViewById(R.id.text_spec_display_name)).setText(s.getName());
            ((TextView) row.findViewById(R.id.text_spec_display_value)).setText(s.getValue());
            specsContainer.addView(row);
        }

        boolean owner = listing.isOwner();
        View contact = findViewById(R.id.button_contact_seller);
        View manage = findViewById(R.id.button_manage_listing);
        View rate = findViewById(R.id.button_rate_seller);
        View reportBtn = findViewById(R.id.button_report_listing);
        View reportIcon = findViewById(R.id.button_details_report);
        View favBtn = findViewById(R.id.button_add_favorite);
        View favIcon = findViewById(R.id.button_details_favorite_icon);

        contact.setVisibility(owner ? View.GONE : View.VISIBLE);
        manage.setVisibility(owner ? View.VISIBLE : View.GONE);
        rate.setVisibility(owner ? View.GONE : View.VISIBLE);
        reportBtn.setVisibility(owner ? View.GONE : View.VISIBLE);
        reportIcon.setVisibility(owner ? View.GONE : View.VISIBLE);
        favBtn.setVisibility(owner ? View.GONE : View.VISIBLE);
        favIcon.setVisibility(owner ? View.GONE : View.VISIBLE);
        updateFavoriteUi();

        contact.setOnClickListener(v -> {
            Intent i = new Intent(this, ChatActivity.class);
            i.putExtra(ChatActivity.EXTRA_LISTING_ID, listing.getId());
            i.putExtra(ChatActivity.EXTRA_OTHER_ID, listing.getSellerId());
            i.putExtra(ChatActivity.EXTRA_OTHER_NAME, listing.getSeller());
            i.putExtra(ChatActivity.EXTRA_LISTING_TITLE, listing.getTitle());
            startActivity(i);
        });

        View.OnClickListener favToggle = v ->
                ApiHelper.call(this, RetrofitClient.getApi(this).toggleFavorite(listing.getId()),
                        new ApiHelper.Result<FavoriteState>() {
                            @Override
                            public void onSuccess(FavoriteState data, String message) {
                                listing.setFavorite(data != null && data.isFavorite());
                                updateFavoriteUi();
                                Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void onError(String message) {
                                Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
                            }
                        });
        favBtn.setOnClickListener(favToggle);
        favIcon.setOnClickListener(favToggle);

        findViewById(R.id.button_details_share).setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_TEXT, listing.getTitle() + " — " + listing.getPriceLabel()
                    + " on BaryoBenta (" + listing.getCategory() + ")");
            startActivity(Intent.createChooser(send, getString(R.string.cd_share)));
        });

        View.OnClickListener openReport = v -> {
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra(ReportActivity.EXTRA_LISTING_ID, listing.getId());
            i.putExtra(ReportActivity.EXTRA_ITEM_NAME, listing.getTitle());
            startActivity(i);
        };
        reportBtn.setOnClickListener(openReport);
        reportIcon.setOnClickListener(openReport);

        rate.setOnClickListener(v -> {
            Intent i = new Intent(this, RateSellerActivity.class);
            i.putExtra(RateSellerActivity.EXTRA_LISTING_ID, listing.getId());
            i.putExtra(RateSellerActivity.EXTRA_SELLER_NAME, listing.getSeller());
            startActivity(i);
        });

        manage.setOnClickListener(v -> showManageDialog());
    }

    private void updateFavoriteUi() {
        ((ImageView) findViewById(R.id.button_details_favorite_icon)).setImageResource(
                listing.isFavorite() ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        ((androidx.appcompat.widget.AppCompatButton) findViewById(R.id.button_add_favorite)).setText(
                listing.isFavorite() ? R.string.btn_remove_favorite : R.string.btn_add_favorite);
    }

    // ---------------- seller tools ----------------
    private void showManageDialog() {
        final boolean sold = "sold".equals(listing.getStatus());
        List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>(); // 0 available, 1 pending, 2 sold, 3 delete
        if (!sold) {
            if (!"available".equals(listing.getStatus())) { labels.add(getString(R.string.manage_available)); actions.add(0); }
            if (!"pending".equals(listing.getStatus())) { labels.add(getString(R.string.manage_pending)); actions.add(1); }
            labels.add(getString(R.string.manage_sold)); actions.add(2);
        }
        labels.add(getString(R.string.manage_delete)); actions.add(3);

        new AlertDialog.Builder(this)
                .setTitle(R.string.manage_title)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    int action = actions.get(which);
                    if (action == 0) setStatus("available");
                    else if (action == 1) setStatus("pending");
                    else if (action == 2) chooseBuyer();
                    else confirmDelete();
                })
                .show();
    }

    private void setStatus(String status) {
        ApiHelper.call(this, RetrofitClient.getApi(this).listingAction(listing.getId(), "set_status", status),
                new ApiHelper.Result<Object>() {
                    @Override
                    public void onSuccess(Object data, String message) {
                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
                        loadListing();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void chooseBuyer() {
        ApiHelper.call(this, RetrofitClient.getApi(this).buyers(listing.getId()),
                new ApiHelper.Result<List<BuyerItem>>() {
                    @Override
                    public void onSuccess(final List<BuyerItem> buyers, String message) {
                        if (buyers == null || buyers.isEmpty()) {
                            new AlertDialog.Builder(ListingDetailsActivity.this)
                                    .setMessage(R.string.manage_no_buyers)
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                            return;
                        }
                        String[] names = new String[buyers.size()];
                        for (int i = 0; i < names.length; i++) names[i] = buyers.get(i).getName();
                        new AlertDialog.Builder(ListingDetailsActivity.this)
                                .setTitle(R.string.manage_choose_buyer)
                                .setItems(names, (d, which) -> markSold(buyers.get(which).getUserId()))
                                .show();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void markSold(int buyerId) {
        ApiHelper.call(this, RetrofitClient.getApi(this).markSold(listing.getId(), buyerId),
                new ApiHelper.Result<Object>() {
                    @Override
                    public void onSuccess(Object data, String message) {
                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
                        loadListing();
                    }

                    @Override
                    public void onError(String message) {
                        if (message != null && message.contains("credits")) {
                            // Not enough credits for the commission -> offer the wallet directly.
                            new AlertDialog.Builder(ListingDetailsActivity.this)
                                    .setTitle(R.string.title_wallet)
                                    .setMessage(message)
                                    .setPositiveButton(R.string.btn_topup, (d, w) ->
                                            startActivity(new Intent(ListingDetailsActivity.this, WalletActivity.class)))
                                    .setNegativeButton(R.string.btn_cancel, null)
                                    .show();
                        } else {
                            Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.manage_delete_confirm)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, w) ->
                        ApiHelper.call(this, RetrofitClient.getApi(this).listingAction(listing.getId(), "delete", null),
                                new ApiHelper.Result<Object>() {
                                    @Override
                                    public void onSuccess(Object data, String message) {
                                        Toast.makeText(ListingDetailsActivity.this, R.string.listing_deleted, Toast.LENGTH_SHORT).show();
                                        finish();
                                    }

                                    @Override
                                    public void onError(String message) {
                                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_LONG).show();
                                    }
                                }))
                .show();
    }

    // ---------------- comments ----------------
    private void loadComments() {
        ApiHelper.call(this, RetrofitClient.getApi(this).comments(listingId),
                new ApiHelper.Result<List<CommentItem>>() {
                    @Override
                    public void onSuccess(List<CommentItem> data, String message) {
                        showComments(data);
                    }

                    @Override
                    public void onError(String message) {
                        // listing itself reports the error
                    }
                });
    }

    private void sendComment() {
        String body = commentInput.getText().toString().trim();
        if (TextUtils.isEmpty(body)) return;
        final View send = findViewById(R.id.button_send_comment);
        send.setEnabled(false);
        ApiHelper.call(this, RetrofitClient.getApi(this).addComment(listingId, body),
                new ApiHelper.Result<List<CommentItem>>() {
                    @Override
                    public void onSuccess(List<CommentItem> data, String message) {
                        send.setEnabled(true);
                        commentInput.setText("");
                        showComments(data);
                    }

                    @Override
                    public void onError(String message) {
                        send.setEnabled(true);
                        Toast.makeText(ListingDetailsActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showComments(List<CommentItem> items) {
        commentsContainer.removeAllViews();
        boolean empty = items == null || items.isEmpty();
        noComments.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (empty) return;
        for (CommentItem c : items) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_comment, commentsContainer, false);
            ((TextView) row.findViewById(R.id.text_comment_name)).setText(c.getName());
            ((TextView) row.findViewById(R.id.text_comment_time)).setText(Fmt.timeAgo(c.getCreatedAt()));
            ((TextView) row.findViewById(R.id.text_comment_body)).setText(c.getBody());
            commentsContainer.addView(row);
        }
    }
}
