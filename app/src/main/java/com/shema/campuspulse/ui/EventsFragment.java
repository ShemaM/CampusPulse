package com.shema.campuspulse.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.shema.campuspulse.EventDetailActivity;
import com.shema.campuspulse.R;
import com.shema.campuspulse.data.CampusRepository;
import com.shema.campuspulse.data.Event;
import com.shema.campuspulse.data.School;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class EventsFragment extends Fragment {

    private static final String[] TYPES = {"All", "Academic", "Career", "Social", "Sports", "Wellness"};

    private final CampusRepository repo = CampusRepository.get();

    private EventAdapter adapter;
    private RecyclerView recycler;
    private EditText search;
    private View btnSearchClear, layoutEmpty, btnEmptyClear;
    private ChipGroup chipTypes, chipFilters;
    private Chip chipSchool, chipMajor;
    private TextView textCount, textEmpty, btnClear;
    private int allChipId;

    private String query = "";
    private String typeFilter = null;
    private String schoolFilter = null;
    private boolean forMyMajor = false;

    public EventsFragment() {
        super(R.layout.fragment_events);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        search = view.findViewById(R.id.search);
        btnSearchClear = view.findViewById(R.id.btn_search_clear);
        layoutEmpty = view.findViewById(R.id.layout_empty);
        btnEmptyClear = view.findViewById(R.id.btn_empty_clear);
        chipTypes = view.findViewById(R.id.chip_types);
        chipFilters = view.findViewById(R.id.chip_filters);
        textCount = view.findViewById(R.id.text_count);
        textEmpty = view.findViewById(R.id.text_empty);
        btnClear = view.findViewById(R.id.btn_clear);
        recycler = view.findViewById(R.id.recycler);

        if (btnSearchClear != null) {
            btnSearchClear.setOnClickListener(v -> {
                search.setText("");
                hideKeyboard();
            });
        }

        if (btnEmptyClear != null) {
            btnEmptyClear.setOnClickListener(v -> clearFilters());
        }

        View notif = view.findViewById(R.id.btn_events_notifications);
        if (notif != null) {
            notif.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), com.shema.campuspulse.NotificationsActivity.class)));
        }

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new EventAdapter(e -> {
            Intent i = new Intent(requireContext(), EventDetailActivity.class);
            i.putExtra(EventDetailActivity.EXTRA_EVENT_ID, e.id);
            startActivity(i);
        });
        recycler.setAdapter(adapter);

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        // Type chips (single choice)
        int targetCheckId = -1;
        Bundle args = getArguments();
        String initialType = args != null ? args.getString("filter_type") : null;

        for (String label : TYPES) {
            Chip chip = (Chip) inflater.inflate(R.layout.item_chip_hero, chipTypes, false);
            chip.setId(View.generateViewId());
            chip.setText(label);
            chip.setTag(label);
            chipTypes.addView(chip);
            if (label.equals("All")) allChipId = chip.getId();
            if (initialType != null && initialType.equalsIgnoreCase(label)) {
                targetCheckId = chip.getId();
                typeFilter = label;
            }
        }
        chipTypes.check(targetCheckId != -1 ? targetCheckId : allChipId);
        chipTypes.setOnCheckedStateChangeListener((group, ids) -> {
            if (ids.isEmpty()) {
                typeFilter = null;
            } else {
                Chip c = group.findViewById(ids.get(0));
                String tag = (String) c.getTag();
                typeFilter = "All".equals(tag) ? null : tag;
            }
            refresh();
        });

        // School dropdown chip
        chipSchool = (Chip) inflater.inflate(R.layout.item_chip_filter, chipFilters, false);
        chipSchool.setCheckable(false);
        chipSchool.setOnClickListener(v -> showSchoolMenu());
        chipFilters.addView(chipSchool);
        updateSchoolChip();

        // For my major
        chipMajor = (Chip) inflater.inflate(R.layout.item_chip_filter, chipFilters, false);
        chipMajor.setText("For my major");
        chipMajor.setOnCheckedChangeListener((b, checked) -> {
            forMyMajor = checked;
            refresh();
        });
        chipFilters.addView(chipMajor);

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString().trim().toLowerCase(Locale.getDefault());
                if (btnSearchClear != null) {
                    btnSearchClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                }
                refresh();
            }
        });
        search.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });

        btnClear.setOnClickListener(v -> clearFilters());
        recycler.scheduleLayoutAnimation();
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(); // seat badges change after an RSVP
    }

    private void showSchoolMenu() {
        final List<School> schools = repo.getSchools();
        PopupMenu menu = new PopupMenu(requireContext(), chipSchool);
        menu.getMenu().add(0, 0, 0, "All schools");
        for (int i = 0; i < schools.size(); i++) {
            menu.getMenu().add(0, i + 1, i + 1, schools.get(i).code + " \u2013 " + schools.get(i).name);
        }
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            schoolFilter = (id == 0) ? null : schools.get(id - 1).code;
            updateSchoolChip();
            refresh();
            return true;
        });
        menu.show();
    }

    private void updateSchoolChip() {
        if (schoolFilter == null) {
            chipSchool.setText("All schools \u25BE");
            chipSchool.setChipBackgroundColorResource(R.color.white);
            chipSchool.setTextColor(ContextCompat.getColor(requireContext(), R.color.usiu_navy));
        } else {
            chipSchool.setText(schoolFilter + " \u25BE");
            chipSchool.setChipBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.usiu_gold)));
            chipSchool.setTextColor(ContextCompat.getColor(requireContext(), R.color.usiu_navy));
        }
    }

    private boolean filtersActive() {
        return !query.isEmpty() || typeFilter != null || schoolFilter != null
                || forMyMajor;
    }

    private void clearFilters() {
        search.setText("");
        if (btnSearchClear != null) btnSearchClear.setVisibility(View.GONE);
        chipTypes.check(allChipId);
        chipMajor.setChecked(false);
        schoolFilter = null;
        updateSchoolChip();
        hideKeyboard();
        refresh();
    }

    private void refresh() {
        long now = System.currentTimeMillis();

        Set<String> mine = null;
        if (forMyMajor) {
            mine = new HashSet<>();
            for (Event e : repo.getForYou()) mine.add(e.id);
        }

        List<Event> result = new ArrayList<>();
        for (Event e : repo.getEvents()) {
            if (e.endMillis < now) continue;                               // already finished
            if (typeFilter != null && !typeFilter.equals(e.type)) continue;
            if (schoolFilter != null && !schoolFilter.equals(e.schoolCode)) continue;
            if (mine != null && !mine.contains(e.id)) continue;
            if (!query.isEmpty()) {
                String haystack = (e.title + " " + e.venue + " " + e.organizer + " "
                        + e.type + " " + e.description).toLowerCase(Locale.getDefault());
                if (!haystack.contains(query)) continue;
            }
            result.add(e);
        }

        adapter.submitGrouped(result);

        int n = result.size();
        textCount.setText(n + (n == 1 ? " event" : " events"));
        if (layoutEmpty != null) {
            layoutEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        } else if (textEmpty != null) {
            textEmpty.setVisibility(n == 0 ? View.VISIBLE : View.GONE);
        }
        recycler.setVisibility(n == 0 ? View.GONE : View.VISIBLE);
        btnClear.setVisibility(filtersActive() ? View.VISIBLE : View.GONE);
    }

    private void hideKeyboard() {
        View v = getView();
        if (v == null) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        search.clearFocus();
    }
}