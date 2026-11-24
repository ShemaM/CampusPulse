package com.shema.campuspulse;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.School;
import com.shema.campuspulse.data.SportsTeam;
import com.shema.campuspulse.data.Team;
import com.shema.campuspulse.data.TokenManager;
import com.shema.campuspulse.data.UserPrefs;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SignupActivity extends AppCompatActivity {

    private final CampusRepository repo = CampusRepository.get();

    private TextInputLayout layoutFullname, layoutEmail, layoutUsername, layoutPassword, layoutMajor;
    private TextInputEditText inputFullname, inputUsername, inputEmail, inputPassword;
    private MaterialAutoCompleteTextView dropSchool, dropMajor;
    private ChipGroup chipTeams;
    private MaterialButton btnSignup;
    private ProgressBar progressSignup;
    private List<School> schools;
    private School selectedSchool;
    private String selectedMajor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this,
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT));
        setContentView(R.layout.activity_signup);

        final View content = findViewById(R.id.signup_content);
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

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        layoutFullname = findViewById(R.id.layout_fullname);
        layoutEmail = findViewById(R.id.layout_email);
        layoutUsername = findViewById(R.id.layout_username);
        layoutPassword = findViewById(R.id.layout_password);
        layoutMajor = findViewById(R.id.layout_major);

        inputFullname = findViewById(R.id.input_fullname);
        inputEmail = findViewById(R.id.input_email);
        inputUsername = findViewById(R.id.input_username);
        inputPassword = findViewById(R.id.input_password);
        dropSchool = findViewById(R.id.drop_school);
        dropMajor = findViewById(R.id.drop_major);
        chipTeams = findViewById(R.id.signup_chip_teams);
        btnSignup = findViewById(R.id.btn_signup);
        progressSignup = findViewById(R.id.progress_signup);
        TextView textToLogin = findViewById(R.id.text_to_login);

        clearErrorOnType(inputFullname, layoutFullname);
        clearErrorOnType(inputEmail, layoutEmail);
        clearErrorOnType(inputUsername, layoutUsername);
        clearErrorOnType(inputPassword, layoutPassword);

        buildSchoolDropdown();
        buildTeamChips();

        btnSignup.setOnClickListener(v -> attemptSignup());
        textToLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
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

    private void buildSchoolDropdown() {
        schools = repo.getSchools();
        List<String> names = new ArrayList<>();
        for (School s : schools) names.add(s.name);
        dropSchool.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names));

        dropSchool.setOnItemClickListener((parent, view, position, id) -> {
            selectedSchool = schools.get(position);
            selectedMajor = null;
            dropMajor.setText("", false);
            dropMajor.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, selectedSchool.majors));
            layoutMajor.setEnabled(true);
        });

        dropMajor.setOnItemClickListener((parent, view, position, id) -> {
            if (selectedSchool != null && position < selectedSchool.majors.size()) {
                selectedMajor = selectedSchool.majors.get(position);
            }
        });
    }

    private void buildTeamChips() {
        if (chipTeams == null) return;
        chipTeams.removeAllViews();
        for (Team t : repo.getTeams()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, chipTeams, false);
            chip.setId(View.generateViewId());
            chip.setText(t.sport + " · " + t.name);
            chip.setTag(t.id);
            chip.setChecked(false);
            chipTeams.addView(chip);
        }
    }

    private void setLoading(boolean loading) {
        btnSignup.setEnabled(!loading);
        btnSignup.setText(loading ? "" : "Create Account");
        progressSignup.setVisibility(loading ? View.VISIBLE : View.GONE);
        inputFullname.setEnabled(!loading);
        inputEmail.setEnabled(!loading);
        inputUsername.setEnabled(!loading);
        inputPassword.setEnabled(!loading);
    }

    private void attemptSignup() {
        String fullName = inputFullname.getText() != null ? inputFullname.getText().toString().trim() : "";
        String username = inputUsername.getText() != null ? inputUsername.getText().toString().trim() : "";
        String email = inputEmail.getText() != null ? inputEmail.getText().toString().trim() : "";
        String password = inputPassword.getText() != null ? inputPassword.getText().toString().trim() : "";

        boolean valid = true;
        if (username.isEmpty()) {
            layoutUsername.setError("Choose a username or enter your Student ID");
            valid = false;
        }
        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            layoutEmail.setError("Enter a valid email address");
            valid = false;
        }
        if (password.isEmpty()) {
            layoutPassword.setError("Create a password");
            valid = false;
        } else if (password.length() < 6) {
            layoutPassword.setError("Password must be at least 6 characters");
            valid = false;
        }
        if (!valid) return;

        setLoading(true);

        JsonObject body = new JsonObject();
        body.addProperty("username", username);
        body.addProperty("email", email);
        body.addProperty("password", password);
        if (!fullName.isEmpty()) {
            body.addProperty("full_name", fullName);
        }
        if (selectedSchool != null) {
            body.addProperty("school_code", selectedSchool.code);
        }
        if (selectedMajor != null) {
            body.addProperty("major", selectedMajor);
        }

        ApiClient.get(this).register(body).enqueue(new Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject json = response.body();
                    String access = json.has("access") ? json.get("access").getAsString() : "";
                    String refresh = json.has("refresh") ? json.get("refresh").getAsString() : "";
                    TokenManager.saveTokens(SignupActivity.this, access, refresh);

                    List<String> chosenTeams = new ArrayList<>();
                    if (chipTeams != null) {
                        for (int i = 0; i < chipTeams.getChildCount(); i++) {
                            Chip chip = (Chip) chipTeams.getChildAt(i);
                            if (chip.isChecked()) {
                                String teamId = (String) chip.getTag();
                                chosenTeams.add(teamId);
                                if (!repo.isFollowing(teamId)) {
                                    repo.toggleFollow(teamId);
                                }
                            }
                        }
                    }

                    if (selectedSchool != null || !chosenTeams.isEmpty()) {
                        repo.setSelection(selectedSchool != null ? selectedSchool.code : null, selectedMajor);
                    }
                    UserPrefs.save(SignupActivity.this);

                    for (String teamId : chosenTeams) {
                        JsonObject teamObj = new JsonObject();
                        teamObj.addProperty("team_id", teamId);
                        ApiClient.get(SignupActivity.this).followTeam(teamObj).enqueue(new Callback<SportsTeam>() {
                            @Override public void onResponse(Call<SportsTeam> c, Response<SportsTeam> r) {}
                            @Override public void onFailure(Call<SportsTeam> c, Throwable t) {}
                        });
                    }

                    Intent intent = new Intent(SignupActivity.this, OnboardingSuccessActivity.class);
                    intent.putExtra("username", !fullName.isEmpty() ? fullName : username);
                    startActivity(intent);
                    finish();
                } else {
                    String err = parseErrorMessage(response, "Registration failed.");
                    Toast.makeText(SignupActivity.this, err, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                setLoading(false);
                Toast.makeText(SignupActivity.this,
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
}