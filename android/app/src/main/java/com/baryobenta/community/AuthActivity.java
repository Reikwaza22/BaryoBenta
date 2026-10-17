package com.baryobenta.community;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;

/**
 * Login + Sign Up. Both talk to PostgreSQL through the PHP API
 * (register.php / login.php). On success the server returns a token that
 * SessionManager keeps so the user stays signed in.
 */
public class AuthActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "extra_mode";
    public static final String MODE_LOGIN = "login";
    public static final String MODE_SIGNUP = "signup";

    private boolean isSignUpMode = false;
    private boolean isPasswordVisible = false;

    private TextView title;
    private TextView subtitle;
    private TextView statusText;
    private View fullNameLayout;
    private View usernameLayout;
    private View termsLayout;
    private CheckBox termsCheck;
    private EditText fullNameInput;
    private EditText usernameInput;
    private EditText emailInput;
    private EditText passwordInput;
    private AppCompatButton submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);

        title = findViewById(R.id.text_auth_title);
        subtitle = findViewById(R.id.text_auth_subtitle);
        statusText = findViewById(R.id.text_auth_status);
        fullNameLayout = findViewById(R.id.layout_full_name);
        usernameLayout = findViewById(R.id.layout_username);
        termsLayout = findViewById(R.id.layout_terms);
        termsCheck = findViewById(R.id.check_terms);
        fullNameInput = findViewById(R.id.input_full_name);
        usernameInput = findViewById(R.id.input_username);
        emailInput = findViewById(R.id.input_email);
        passwordInput = findViewById(R.id.input_password);
        submitButton = findViewById(R.id.button_auth_submit);
        ImageView togglePasswordIcon = findViewById(R.id.button_toggle_password);
        TextView toggle = findViewById(R.id.text_auth_toggle);

        findViewById(R.id.button_auth_back).setOnClickListener(v -> finish());
        findViewById(R.id.text_view_terms).setOnClickListener(v -> showTermsDialog());

        isSignUpMode = MODE_SIGNUP.equals(getIntent().getStringExtra(EXTRA_MODE));
        applyMode();

        toggle.setOnClickListener(v -> {
            isSignUpMode = !isSignUpMode;
            applyMode();
        });

        submitButton.setOnClickListener(v -> {
            if (isSignUpMode) handleSignUp();
            else handleLogin();
        });

        togglePasswordIcon.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            passwordInput.setInputType(isPasswordVisible
                    ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            passwordInput.setSelection(passwordInput.getText().length());
        });
    }

    private void handleSignUp() {
        hideStatus();
        String name = fullNameInput.getText().toString().trim();
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(name)) { fullNameInput.setError(getString(R.string.error_required_field)); return; }
        if (TextUtils.isEmpty(username)) { usernameInput.setError(getString(R.string.error_required_field)); return; }
        if (TextUtils.isEmpty(email)) { emailInput.setError(getString(R.string.error_required_field)); return; }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) { emailInput.setError(getString(R.string.error_invalid_email)); return; }
        if (password.length() < 6) { passwordInput.setError(getString(R.string.error_password_too_short)); return; }
        if (!termsCheck.isChecked()) { showStatus(getString(R.string.error_terms_agree), false); return; }

        setLoading(true, R.string.btn_signup_loading);
        ApiHelper.call(this, RetrofitClient.getApi(this).register(name, username, email, password, "1"),
                new ApiHelper.Result<AuthData>() {
                    @Override
                    public void onSuccess(AuthData data, String message) {
                        finishAuth(data, message);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false, R.string.btn_signup);
                        showStatus(message, false);
                    }
                });
    }

    private void handleLogin() {
        hideStatus();
        String login = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(login)) { emailInput.setError(getString(R.string.error_required_field)); return; }
        if (TextUtils.isEmpty(password)) { passwordInput.setError(getString(R.string.error_required_field)); return; }

        setLoading(true, R.string.login_loading);
        ApiHelper.call(this, RetrofitClient.getApi(this).login(login, password),
                new ApiHelper.Result<AuthData>() {
                    @Override
                    public void onSuccess(AuthData data, String message) {
                        finishAuth(data, null);
                    }

                    @Override
                    public void onError(String message) {
                        setLoading(false, R.string.btn_login);
                        showStatus(message, false);
                    }
                });
    }

    private void finishAuth(AuthData data, String message) {
        if (data == null || data.getUser() == null) {
            setLoading(false, isSignUpMode ? R.string.btn_signup : R.string.btn_login);
            showStatus(getString(R.string.error_network), false);
            return;
        }
        SessionManager.saveSession(this, data.getToken(), data.getUser());
        CategoryRepo.clear();
        if (message != null) showStatus(message, true);
        Intent intent = new Intent(this, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean loading, int labelRes) {
        submitButton.setEnabled(!loading);
        submitButton.setText(labelRes);
    }

    private void showStatus(String message, boolean isSuccess) {
        statusText.setText(message);
        statusText.setTextColor(getResources().getColor(isSuccess ? R.color.status_available : R.color.status_sold));
        statusText.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }

    private void showTermsDialog() {
        ScrollView scroll = new ScrollView(this);
        TextView body = new TextView(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        body.setPadding(pad, pad / 2, pad, 0);
        body.setText(R.string.terms_body);
        body.setTextSize(13f);
        body.setTextColor(getResources().getColor(R.color.text_primary));
        body.setLineSpacing(0, 1.2f);
        scroll.addView(body);

        new AlertDialog.Builder(this)
                .setTitle(R.string.terms_title)
                .setView(scroll)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.terms_btn_agree, (d, w) -> termsCheck.setChecked(true))
                .show();
    }

    private void applyMode() {
        hideStatus();
        TextView toggle = findViewById(R.id.text_auth_toggle);
        if (isSignUpMode) {
            title.setText(R.string.auth_signup_title);
            subtitle.setText(R.string.auth_signup_subtitle);
            fullNameLayout.setVisibility(View.VISIBLE);
            usernameLayout.setVisibility(View.VISIBLE);
            termsLayout.setVisibility(View.VISIBLE);
            emailInput.setHint(R.string.hint_email_signup);
            submitButton.setText(R.string.btn_signup);
            toggle.setText(R.string.auth_toggle_to_login);
        } else {
            title.setText(R.string.auth_login_title);
            subtitle.setText(R.string.auth_login_subtitle);
            fullNameLayout.setVisibility(View.GONE);
            usernameLayout.setVisibility(View.GONE);
            termsLayout.setVisibility(View.GONE);
            emailInput.setHint(R.string.hint_email);
            submitButton.setText(R.string.btn_login);
            toggle.setText(R.string.auth_toggle_to_signup);
        }
    }
}
