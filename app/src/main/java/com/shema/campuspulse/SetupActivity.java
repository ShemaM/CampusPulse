package com.shema.campuspulse;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

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
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;
import com.shema.campuspulse.data.ApiClient;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.School;
import com.shema.campuspulse.data.Team;
import com.shema.campuspulse.data.TokenManager;
import com.shema.campuspulse.data.UserPrefs;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SetupActivity extends AppCompatActivity {

    public static final String EXTRA_EDIT = "edit";

    private final CampusRepository repo = CampusRepository.get();

    private MaterialAutoCompleteTextView dropSchool;
    private MaterialAutoCompleteTextView dropMajor;
    private TextInputLayout layoutMajor;
    private TextView textPreview;
    private ChipGroup chipTeams;
    private MaterialButton btnContinue;

    private boolean editMode;
    private List<School> schools;
    private School selectedSchool;
    private String selectedMajor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        setContentView(R.layout.activity_setup);
        SystemBars.addStatusScrim(this);

        editMode = getIntent().getBooleanExtra(EXTRA_EDIT, false);

        View headerContent = findViewById(R.id.header_content);
        View bottom = findViewById(R.id.setup_bottom);
        ImageButton btnBack = findViewById(R.id.btn_back);
        View btnSkip = findViewById(R.id.btn_skip);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.setup_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extra = editMode ? dp(40) : 0;
            headerContent.setPadding(dp(20), dp(12) + bars.top + extra, dp(20), dp(20));
            bottom.setPadding(dp(16), dp(12), dp(16), dp(12) + bars.bottom);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) btnBack.getLayoutParams();
            lp.topMargin = bars.top + dp(8);
            btnBack.setLayoutParams(lp);
            return insets;
        });

        dropSchool = findViewById(R.id.drop_school);
        dropMajor = findViewById(R.id.drop_major);
        layoutMajor = findViewById(R.id.layout_major);
        textPreview = findViewById(R.id.text_preview);
        chipTeams = findViewById(R.id.chip_teams);
        btnContinue = findViewById(R.id.btn_continue);

        if (editMode) {
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> finish());
            btnContinue.setText("Save changes");
            btnSkip.setVisibility(View.GONE);
        }

        buildSchoolDropdown();
        buildTeamChips();
        prefillFromRepo();

        btnContinue.setOnClickListener(v -> {
            repo.setSelection(selectedSchool.code, selectedMajor);
            for (int i = 0; i < chipTeams.getChildCount(); i++) {
                Chip chip = (Chip) chipTeams.getChildAt(i);
                String teamId = (String) chip.getTag();
                if (repo.isFollowing(teamId) != chip.isChecked()) {
                    repo.toggleFollow(teamId);
                }
            }
            finishSetup();
        });

        btnSkip.setOnClickListener(v -> {
            repo.setSelection(null, null);
            finishSetup();
        });
    }

    private void buildSchoolDropdown() {
        schools = repo.getSchools();
        List<String> names = new ArrayList<>();
        for (School s : schools) names.add(s.name);
        dropSchool.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, names));

        dropSchool.setOnItemClickListener((parent, view, position, id) ->
                applySchool(schools.get(position)));

        dropMajor.setOnItemClickListener((parent, view, position, id) ->
                applyMajor(selectedSchool.majors.get(position)));
    }

    private void applySchool(School s) {
        selectedSchool = s;
        selectedMajor = null;
        textPreview.setVisibility(View.GONE);
        dropMajor.setText("", false);
        dropMajor.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, s.majors));
        layoutMajor.setEnabled(true);
        updateButton();
    }

    private void applyMajor(String major) {
        selectedMajor = major;

        // Count matching events without changing the saved selection
        String oldSchool = repo.getSelectedSchoolCode();
        String oldMajor = repo.getSelectedMajor();
        repo.setSelection(selectedSchool.code, major);
        int count = repo.getForYou().size();
        repo.setSelection(oldSchool, oldMajor);

        textPreview.setText(count + (count == 1 ? " event is" : " events are")
                + " lined up for you this week.");
        textPreview.setVisibility(View.VISIBLE);
        updateButton();
    }

    private void prefillFromRepo() {
        String code = repo.getSelectedSchoolCode();
        if (code == null) return;
        for (School s : schools) {
            if (s.code.equals(code)) {
                applySchool(s);
                dropSchool.setText(s.name, false);
                break;
            }
        }
        String major = repo.getSelectedMajor();
        if (selectedSchool != null && major != null && selectedSchool.majors.contains(major)) {
            applyMajor(major);
            dropMajor.setText(major, false);
        }
    }

    private void buildTeamChips() {
        for (Team t : repo.getTeams()) {
            Chip chip = (Chip) getLayoutInflater().inflate(R.layout.item_chip_filter, chipTeams, false);
            chip.setId(View.generateViewId());
            chip.setText(t.sport + " - " + t.name);
            chip.setTag(t.id);
            chip.setChecked(repo.isFollowing(t.id));
            chipTeams.addView(chip);
        }
    }

    private void updateButton() {
        btnContinue.setEnabled(selectedSchool != null && selectedMajor != null);
    }

    private void finishSetup() {
        UserPrefs.save(this);

        if (TokenManager.getAccessToken(this) != null) {
            JsonObject profile = new JsonObject();
            if (repo.getSelectedSchoolCode() != null) profile.addProperty("school_code", repo.getSelectedSchoolCode());
            if (repo.getSelectedMajor() != null) profile.addProperty("major", repo.getSelectedMajor());
            ApiClient.get(this).updateProfile(profile).enqueue(new Callback<JsonObject>() {
                @Override
                public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {}
                @Override
                public void onFailure(Call<JsonObject> call, Throwable t) {}
            });
        }

        if (editMode) {
            setResult(RESULT_OK);
            finish();
        } else {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
