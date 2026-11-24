package com.shema.campuspulse.data;

import com.google.gson.annotations.SerializedName;

public class SportsTeam {
    public int id;

    @SerializedName("team_id")
    public String teamId;

    public String name;
    public String sport;

    @SerializedName("competition")
    public String competition;
}
