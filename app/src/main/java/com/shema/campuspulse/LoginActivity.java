package com.shema.campuspulse;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.SportsTeam;
import com.shema.campuspulse.data.TokenManager;
import com.shema.campuspulse.data.UserPrefs;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout layoutUsername, layoutPassword;
    private TextInputEditText inputUsername, inputPassword;
    private MaterialButton btnLogin;
    private ProgressBar progressLogin;
    private TextView textToSignup, textForgotPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_login);

        final View content = findViewById(R.id.login_content);
        if (content != null) {
            final int baseTop = content.getPaddingTop();
            final int baseBottom = content.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
                Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), baseTop + bars.top,
                        v.getPaddingRight(), baseBottom + bars.bottom);
                return windowInsets;
            });
        }

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        layoutUsername = findViewById(R.id.layout_username);
        layoutPassword = findViewById(R.id.layout_password);
        inputUsername = findViewById(R.id.input_username);
        inputPassword = findViewById(R.id.input_password);
        btnLogin = findViewById(R.id.btn_login);
        progressLogin = findViewById(R.id.progress_login);
        textToSignup = findViewById(R.id.text_to_signup);
        if (textToSignup != null) {
            textToSignup.setText(android.text.Html.fromHtml("New to CampusPulse? <font color='#002B49'><u><b>Create an account</b></u></font>", android.text.Html.FROM_HTML_MODE_LEGACY));
        }
        textForgotPassword = findViewById(R.id.text_forgot_password);

        clearErrorOnType(inputUsername, layoutUsername);
        clearErrorOnType(inputPassword, layoutPassword);

        inputPassword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        btnLogin.setOnClickListener(v -> attemptLogin());
        textForgotPassword.setOnClickListener(v -> showForgotPasswordDialog());
        textToSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });
    }

    private void clearErrorOnType(TextInputEditText editText, TextInputLayout layout) {
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (layout.getError() != null) {
                    layout.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showForgotPasswordDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset CampusPulse Password")
                .setMessage("To recover access to your CampusPulse account, please contact the USIU-Africa Student Tech Desk or email support@campuspulse.ac.ke from your @usiu.ac.ke address with your Student ID.")
                .setPositiveButton("Got it", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void setLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnLogin.setText(loading ? "" : "Sign In");
        progressLogin.setVisibility(loading ? View.VISIBLE : View.GONE);
        inputUsername.setEnabled(!loading);
        inputPassword.setEnabled(!loading);
    }

    private void attemptLogin() {
        String username = inputUsername.getText() != null ? inputUsername.getText().toString().trim() : "";
        String password = inputPassword.getText() != null ? inputPassword.getText().toString().trim() : "";

        boolean valid = true;
        if (username.isEmpty()) {
            layoutUsername.setError("Enter your student ID, username, or email");
            valid = false;
        }
        if (password.isEmpty()) {
            layoutPassword.setError("Enter your password");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("password", password);

        ApiClient.get(this).login(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject json = response.body();
                    String access = json.has("access") ? json.get("access").getAsString() : "";
                    String refresh = json.has("refresh") ? json.get("refresh").getAsString() : "";
                    TokenManager.saveTokens(LoginActivity.this, access, refresh);
                    proceedAfterAuth();
                } else {
                    String err = parseErrorMessage(response, "Wrong username or password");
                    layoutPassword.setError(err);
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        "Network error. Please check your connection and try again.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private String parseErrorMessage(Response<?> response, String defaultMsg) {
        if (response.errorBody() == null) return defaultMsg;
        try {
            String jsonStr = response.errorBody().string();
            com.google.gson.JsonObject obj = new com.google.gson.JsonParser().parse(jsonStr).getAsJsonObject();
            if (obj.has("detail")) {
                return obj.get("detail").getAsString();
            }
            for (String key : obj.keySet()) {
                com.google.gson.JsonElement val = obj.get(key);
                if (val.isJsonArray() && val.getAsJsonArray().size() > 0) {
                    String err = val.getAsJsonArray().get(0).getAsString();
                    return key.equals("non_field_errors") ? err : key + ": " + err;
                } else if (val.isJsonPrimitive()) {
                    return val.getAsString();
                }
            }
        } catch (Exception ignored) {}
        if (response.code() == 401) return "Wrong username or password";
        if (response.code() == 429) return "Too many attempts, try again in a minute";
        return defaultMsg;
    }

    private void proceedAfterAuth() {
        setLoading(true);
        ApiClient.get(this).getProfile().enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject profile = response.body();
                    String school = profile.has("school_code") && !profile.get("school_code").isJsonNull()
                            ? profile.get("school_code").getAsString() : null;
                    String major = profile.has("major") && !profile.get("major").isJsonNull()
                            ? profile.get("major").getAsString() : null;
                    if (school != null && !school.isEmpty()) {
                        CampusRepository.get().setSelection(school, major);
                    }
                }

                ApiClient.get(LoginActivity.this).getFavoriteTeams().enqueue(new Callback<List<SportsTeam>>() {
                    @Override
                    public void onResponse(Call<List<SportsTeam>> call2, Response<List<SportsTeam>> response2) {
                        setLoading(false);
                        if (response2.isSuccessful() && response2.body() != null) {
                            for (SportsTeam st : response2.body()) {
                                if (st.teamId != null && !CampusRepository.get().isFollowing(st.teamId)) {
                                    CampusRepository.get().toggleFollow(st.teamId);
                                }
                            }
                        }
                        UserPrefs.save(LoginActivity.this);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }

                    @Override
                    public void onFailure(Call<List<SportsTeam>> call2, Throwable t2) {
                        setLoading(false);
                        UserPrefs.save(LoginActivity.this);
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
                        finish();
                    }
                });
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                UserPrefs.save(LoginActivity.this);
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
        });
    }
}