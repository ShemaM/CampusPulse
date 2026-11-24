package com.shema.campuspulse.data;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Saves the student's setup choices on the device. */
public class UserPrefs {
    private static final String FILE = "campuspulse_prefs";
    private static final String K_DONE = "setup_done";
    private static final String K_WELCOME = "welcome_shown";
    private static final String K_SCHOOL = "school";
    private static final String K_MAJOR = "major";
    private static final String K_TEAMS = "teams";

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean isSetupDone(Context c) {
        return sp(c).getBoolean(K_DONE, false);
    }

    public static boolean isWelcomeShown(Context c) {
        return sp(c).getBoolean(K_WELCOME, false);
    }

    public static void markWelcomeShown(Context c) {
        sp(c).edit().putBoolean(K_WELCOME, true).apply();
    }

    public static void clearSelection(Context c) {
        sp(c).edit()
                .remove(K_SCHOOL)
                .remove(K_MAJOR)
                .remove(K_DONE)
                .apply();
        CampusRepository.get().setSelection(null, null);
    }

    /** Push saved choices into the repository (call once at app start). */
    public static void restore(Context c) {
        SharedPreferences p = sp(c);
        CampusRepository repo = CampusRepository.get();
        repo.setSelection(p.getString(K_SCHOOL, null), p.getString(K_MAJOR, null));
        Set<String> teams = p.getStringSet(K_TEAMS, new HashSet<String>());
        for (String id : teams) {
            if (!repo.isFollowing(id)) repo.toggleFollow(id);
        }
    }

    /** Save whatever the repository currently holds and mark setup as done. */
    public static void save(Context c) {
        CampusRepository repo = CampusRepository.get();
        sp(c).edit()
                .putBoolean(K_DONE, true)
                .putString(K_SCHOOL, repo.getSelectedSchoolCode())
                .putString(K_MAJOR, repo.getSelectedMajor())
                .putStringSet(K_TEAMS, repo.getFollowedTeamIds())
                .apply();
    }
}
