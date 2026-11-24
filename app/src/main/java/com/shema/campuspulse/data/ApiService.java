package com.shema.campuspulse.data;

import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    @POST("auth/login/")
    Call<JsonObject> login(@Body JsonObject credentials);

    @POST("auth/register/")
    Call<JsonObject> register(@Body JsonObject userData);

    @GET("auth/profile/")
    Call<JsonObject> getProfile();

    @PUT("auth/profile/")
    Call<JsonObject> updateProfile(@Body JsonObject profileData);

    @GET("schools/")
    Call<List<Map<String, Object>>> getSchools();

    @GET("events/")
    Call<List<Event>> getEvents();

    @POST("events/{id}/rsvp/")
    Call<JsonObject> rsvpEvent(@Path("id") String eventId, @Body JsonObject rsvpData);

    @DELETE("events/{id}/rsvp/")
    Call<JsonObject> cancelRsvp(@Path("id") String eventId);

    @POST("events/{id}/bookmark/")
    Call<JsonObject> bookmarkEvent(@Path("id") String eventId);

    @DELETE("events/{id}/bookmark/")
    Call<JsonObject> unbookmarkEvent(@Path("id") String eventId);

    @GET("fixtures/")
    Call<List<Fixture>> getFixtures();

    @GET("fixtures/{id}/")
    Call<Fixture> getFixture(@Path("id") String fixtureId);

    @GET("teams/")
    Call<List<SportsTeam>> getSportsTeams();

    @GET("teams/{teamId}/")
    Call<SportsTeam> getSportsTeam(@Path(value = "teamId", encoded = true) String teamId);

    @GET("teams/{teamId}/fixtures/")
    Call<List<Fixture>> getTeamFixtures(
            @Path(value = "teamId", encoded = true) String teamId,
            @Query("status") String status);

    @GET("teams/{teamId}/standings/")
    Call<JsonObject> getTeamStandings(@Path(value = "teamId", encoded = true) String teamId);

    @GET("favorites/teams/")
    Call<List<SportsTeam>> getFavoriteTeams();

    @POST("favorites/teams/")
    Call<SportsTeam> followTeam(@Body JsonObject teamId);

    @DELETE("favorites/teams/{teamId}/")
    Call<Void> unfollowTeam(@Path(value = "teamId", encoded = true) String teamId);

    @GET("schedules/")
    Call<List<Map<String, Object>>> getSchedules();

    @POST("schedules/")
    Call<JsonObject> addSchedule(@Body JsonObject scheduleData);

    @DELETE("schedules/{id}/")
    Call<JsonObject> deleteSchedule(@Path("id") long scheduleId);

    @GET("notifications/")
    Call<List<NotificationItem>> getNotifications();

    @POST("notifications/{id}/read/")
    Call<JsonObject> markNotificationRead(@Path("id") int id);

    @POST("notifications/{id}/dismiss/")
    Call<JsonObject> dismissNotification(@Path("id") int id);

    @POST("notifications/mark-all-read/")
    Call<JsonObject> markAllNotificationsRead();
}
