package com.shema.campuspulse.data;

import java.util.List;

public class Team {
    public final String id;
    public final String name;
    public final String sport;
    public final String league;
    public final String coach;
    public final String trainingSchedule;
    public final String description;
    public final String rosterSummary;
    public final List<String> pastScores;

    public Team(String id, String name, String sport, String league, String coach,
                String trainingSchedule, String description, String rosterSummary,
                List<String> pastScores) {
        this.id = id;
        this.name = name;
        this.sport = sport;
        this.league = league;
        this.coach = coach;
        this.trainingSchedule = trainingSchedule;
        this.description = description;
        this.rosterSummary = rosterSummary;
        this.pastScores = pastScores;
    }
}