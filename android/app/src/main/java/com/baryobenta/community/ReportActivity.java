package com.baryobenta.community;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Sends a report to the reports table; admins review it in admin.php. */
public class ReportActivity extends AppCompatActivity {

    public static final String EXTRA_ITEM_NAME = "extra_item_name";
    public static final String EXTRA_LISTING_ID = "extra_listing_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        findViewById(R.id.button_report_back).setOnClickListener(v -> finish());

        final int listingId = getIntent().getIntExtra(EXTRA_LISTING_ID, 0);
        String itemName = getIntent().getStringExtra(EXTRA_ITEM_NAME);
        TextView itemNameText = findViewById(R.id.text_report_item_name);
        itemNameText.setText(itemName != null ? getString(R.string.title_report) + ": " + itemName
                : getString(R.string.title_report));

        final RadioGroup reasonGroup = findViewById(R.id.group_report_reason);
        final EditText details = findViewById(R.id.input_report_details);
        final android.view.View submit = findViewById(R.id.button_submit_report);

        submit.setOnClickListener(v -> {
            int id = reasonGroup.getCheckedRadioButtonId();
            String reason;
            if (id == R.id.reason_prohibited) reason = "prohibited";
            else if (id == R.id.reason_scam) reason = "scam";
            else if (id == R.id.reason_inappropriate) reason = "inappropriate";
            else if (id == R.id.reason_other) reason = "other";
            else {
                Toast.makeText(this, R.string.reason_required, Toast.LENGTH_SHORT).show();
                return;
            }
            submit.setEnabled(false);
            ApiHelper.call(this, RetrofitClient.getApi(this).addReport(listingId, reason, details.getText().toString().trim()),
                    new ApiHelper.Result<Object>() {
                        @Override
                        public void onSuccess(Object data, String message) {
                            Toast.makeText(ReportActivity.this, message, Toast.LENGTH_LONG).show();
                            finish();
                        }

                        @Override
                        public void onError(String message) {
                            submit.setEnabled(true);
                            Toast.makeText(ReportActivity.this, message, Toast.LENGTH_LONG).show();
                        }
                    });
        });
    }
}
