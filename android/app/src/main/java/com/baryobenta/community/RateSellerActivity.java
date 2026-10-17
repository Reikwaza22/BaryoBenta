package com.baryobenta.community;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Saves a 1-5 star review (plus comment) for a seller into the reviews table. */
public class RateSellerActivity extends AppCompatActivity {

    public static final String EXTRA_SELLER_NAME = "extra_seller_name";
    public static final String EXTRA_LISTING_ID = "extra_listing_id";

    private final ImageView[] stars = new ImageView[5];
    private int selectedRating = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate_seller);

        findViewById(R.id.button_rate_back).setOnClickListener(v -> finish());

        final int listingId = getIntent().getIntExtra(EXTRA_LISTING_ID, 0);
        String sellerName = getIntent().getStringExtra(EXTRA_SELLER_NAME);
        ((TextView) findViewById(R.id.text_rate_seller_name)).setText(
                sellerName != null ? sellerName : getString(R.string.title_rate_seller));

        stars[0] = findViewById(R.id.star_1);
        stars[1] = findViewById(R.id.star_2);
        stars[2] = findViewById(R.id.star_3);
        stars[3] = findViewById(R.id.star_4);
        stars[4] = findViewById(R.id.star_5);
        for (int i = 0; i < stars.length; i++) {
            final int starIndex = i + 1;
            stars[i].setOnClickListener(v -> setSelectedRating(starIndex));
        }

        final EditText comment = findViewById(R.id.input_review_comment);
        final android.view.View submit = findViewById(R.id.button_submit_review);
        submit.setOnClickListener(v -> {
            if (selectedRating == 0) {
                Toast.makeText(this, R.string.error_select_rating, Toast.LENGTH_SHORT).show();
                return;
            }
            submit.setEnabled(false);
            ApiHelper.call(this, RetrofitClient.getApi(this).addReview(listingId, selectedRating, comment.getText().toString().trim()),
                    new ApiHelper.Result<Object>() {
                        @Override
                        public void onSuccess(Object data, String message) {
                            Toast.makeText(RateSellerActivity.this, message, Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            submit.setEnabled(true);
                            Toast.makeText(RateSellerActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }

    private void setSelectedRating(int rating) {
        selectedRating = rating;
        for (int i = 0; i < stars.length; i++) {
            stars[i].setImageResource(i < rating ? R.drawable.ic_star_filled : R.drawable.ic_star_border);
        }
    }
}
