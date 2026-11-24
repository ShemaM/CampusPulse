package com.shema.campuspulse.data;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class Fixture {
    @SerializedName(value = "id", alternate = {"fixture_id"})
    public String id;

    @SerializedName(value = "teamId", alternate = {"team_id"})
    public String teamId;

    @SerializedName(value = "teamName", alternate = {"team_name"})
    public String teamName;

    @SerializedName("sport")
    public String sport;

    @SerializedName("opponent")
    public String opponent;

    @SerializedName(value = "startMillis", alternate = {"start_millis", "time_millis"})
    public long startMillis;

    @SerializedName(value = "endMillis", alternate = {"end_millis"})
    public long endMillis;

    @SerializedName("venue")
    public String venue;

    @SerializedName(value = "homeGame", alternate = {"is_home"})
    public boolean homeGame;

    @SerializedName("league")
    public String league;

    @SerializedName(value = "fanCount", alternate = {"fan_count"})
    public int fanCount;

    @SerializedName("score_usiu")
    public Integer scoreUsiu;

    @SerializedName("score_opponent")
    public Integer scoreOpponent;

    @SerializedName(value = "startTime", alternate = {"start_time"})
    public String startTime;

    @SerializedName(value = "endTime", alternate = {"end_time"})
    public String endTime;

    @SerializedName("status")
    public String status;

    @SerializedName("is_demo")
    public boolean isDemo;

    @SerializedName("scorers")
    public List<FixtureScorer> scorers = new ArrayList<>();

    public Fixture() {}

    public Fixture(String id, String teamId, String teamName, String sport,
                   String opponent, long startMillis, long endMillis, String venue,
                   boolean homeGame, String league, int fanCount) {
        this.id = id;
        this.teamId = teamId;
        this.teamName = teamName;
        this.sport = sport;
        this.opponent = opponent;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.venue = venue;
        this.homeGame = homeGame;
        this.league = league;
        this.fanCount = fanCount;
    }
}

