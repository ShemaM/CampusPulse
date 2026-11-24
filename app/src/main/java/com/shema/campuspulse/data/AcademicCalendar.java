package com.shema.campuspulse.data;

import java.util.Calendar;
import java.util.Locale;

/** Academic calendar helper for 2026-2027 semester week tracking. */
public class AcademicCalendar {

    /** Total academic weeks in a standard USIU-Africa semester. */
    public static final int TOTAL_WEEKS = 14;

    // Reference: Week 10 starts Monday, November 9th, 2026
    private static final long WEEK_10_START_MILLIS;

    static {
        Calendar c = Calendar.getInstance();
        c.set(2026, Calendar.NOVEMBER, 9, 0, 0, 0);
        c.set(Calendar.MILLISECOND, 0);
        WEEK_10_START_MILLIS = c.getTimeInMillis();
    }

    public static int getCurrentWeekNumber() {
        return getWeekNumber(System.currentTimeMillis());
    }

    public static int getWeekNumber(long millis) {
        Calendar ref = Calendar.getInstance();
        ref.setTimeInMillis(WEEK_10_START_MILLIS);

        Calendar target = Calendar.getInstance();
        target.setTimeInMillis(millis);
        target.set(Calendar.HOUR_OF_DAY, 0);
        target.set(Calendar.MINUTE, 0);
        target.set(Calendar.SECOND, 0);
        target.set(Calendar.MILLISECOND, 0);

        long diffDays = (target.getTimeInMillis() - ref.getTimeInMillis()) / (24L * 60 * 60 * 1000);
        long weekDiff = diffDays / 7;
        if (diffDays < 0 && (diffDays % 7 != 0)) {
            weekDiff -= 1;
        }
        int week = 10 + (int) weekDiff;
        return Math.max(1, Math.min(TOTAL_WEEKS, week));
    }

    public static String getWeekProgressText(int weekNumber) {
        return "Week " + weekNumber + " of " + TOTAL_WEEKS;
    }

    public static String getWeekRangeDescription(int weekNumber) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(WEEK_10_START_MILLIS);
        c.add(Calendar.DAY_OF_YEAR, (weekNumber - 10) * 7);

        Calendar end = (Calendar) c.clone();
        end.add(Calendar.DAY_OF_YEAR, 6);

        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("MMM d", Locale.getDefault());
        return fmt.format(c.getTime()) + " \u2013 " + fmt.format(end.getTime());
    }

    public static String getSemesterTitle() {
        int week = getCurrentWeekNumber();
        return "FALL 2026 \u00B7 WEEK " + week;
    }

    public static String getEncouragement(int weekNumber) {
        switch (weekNumber) {
            case 1:
                return "Welcome to the new academic term. Set clear priorities and establish a consistent study routine.";
            case 2:
            case 3:
                return "Build focused academic habits early. Consistent engagement sets the foundation for term success.";
            case 4:
            case 5:
                return "Midterm assessments are approaching. Review your coursework and balance your study schedule.";
            case 6:
            case 7:
                return "Midterm examination period. Maintain steady focus and utilize campus academic support resources.";
            case 8:
            case 9:
                return "Entering the second half of the term. Maintain momentum across coursework and project milestones.";
            case 10:
            case 11:
                return "Week " + weekNumber + " of " + TOTAL_WEEKS + ". Stay disciplined with current deliverables and study schedules.";
            case 12:
            case 13:
                return "Final project deliverables and presentations are underway. Manage your deadlines systematically.";
            case 14:
                return "Final examination period. Trust your preparation and conclude the term with excellence.";
            default:
                return "Make each week purposeful. Stay engaged, focused, and connected with campus resources.";
        }
    }
}
