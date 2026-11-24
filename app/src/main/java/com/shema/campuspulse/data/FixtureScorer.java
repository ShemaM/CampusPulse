package com.shema.campuspulse.data;

import com.google.gson.annotations.SerializedName;

public class FixtureScorer {
    @SerializedName("player_name")
    public String playerName;

    @SerializedName("minute")
    public int minute;

    @SerializedName("added_minute")
    public Integer addedMinute;

    @SerializedName("display_minute")
    public String displayMinute;
}
