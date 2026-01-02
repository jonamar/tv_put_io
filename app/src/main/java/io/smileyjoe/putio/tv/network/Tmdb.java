package io.smileyjoe.putio.tv.network;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.koushikdutta.ion.Ion;

import java.net.URLEncoder;
import java.util.ArrayList;

import io.smileyjoe.putio.tv.BuildConfig;
import io.smileyjoe.putio.tv.db.AppDatabase;
import io.smileyjoe.putio.tv.util.Async;
import io.smileyjoe.putio.tv.util.TmdbUtil;

public class Tmdb {

    private static String BASE = "https://api.themoviedb.org/3";
    private static String BASE_IMAGE_POSTER = "https://image.tmdb.org/t/p/w342";
    private static String BASE_IMAGE_BACKDROP = "https://image.tmdb.org/t/p/w780";
    private static String BASE_IMAGE_PROFILE = "https://image.tmdb.org/t/p/w185";
    private static String SEARCH = "/search";
    private static String MOVIE = "/movie";
    private static String TV = "/tv";
    private static String EPISODE = TV + "/{id}/season/{season}/episode/{episode}";
    private static String MOVIE_CREDITS = MOVIE + "/{id}/credits";
    private static String LIST = "/list";
    private static String GENRE = "/genre";
    private static String PARAM_API_KEY = "api_key";
    private static String PARAM_SEARCH = "query";
    private static String PARAM_YEAR = "primary_release_year";

    private static class Base {
        protected static String getUrl(String... paths) {
            String url = BASE;

            for (String path : paths) {
                url += path;
            }

            url += "?" + PARAM_API_KEY + "=" + BuildConfig.TMDB_AUTH_TOKEN;

            return url;
        }

        protected static String addParam(String url, String key, String value) {
            return url + "&" + key + "=" + URLEncoder.encode(value);
        }
    }

    public static class Image extends Base {
        public enum Type {
            POSTER, BACKDROP, PROFILE
        }

        public static String getUrl(String url) {
            return getUrl(url, Type.POSTER);
        }

        public static String getUrl(String url, Type type) {
            if (!TextUtils.isEmpty(url)) {
                String baseUrl;
                switch (type) {
                    case BACKDROP:
                        baseUrl = BASE_IMAGE_BACKDROP;
                        break;
                    case PROFILE:
                        baseUrl = BASE_IMAGE_PROFILE;
                        break;
                    case POSTER:
                    default:
                        baseUrl = BASE_IMAGE_POSTER;
                        break;
                }
                return baseUrl + url;
            } else {
                return null;
            }
        }
    }

    public static class Genre extends Base {
        public static void update(Context context) {
            Ion.with(context)
                    .load(getUrl(GENRE, MOVIE, LIST))
                    .asJsonObject()
                    .withResponse()
                    .setCallback(new OnResponse(context));
        }

        private static class OnResponse extends Response {
            private Context mContext;

            public OnResponse(Context context) {
                mContext = context;
            }

            @Override
            public void onSuccess(JsonObject result) {
                Async.run(() -> {
                    JsonElement genresElement = result.get("genres");
                    if (genresElement != null && genresElement.isJsonArray()) {
                        ArrayList<io.smileyjoe.putio.tv.object.Genre> genres = io.smileyjoe.putio.tv.object.Genre.fromApi(genresElement.getAsJsonArray());
                        AppDatabase.getInstance(mContext).genreDao().insert(genres);
                    }
                });
            }
        }
    }

    public static class Series extends Base {
        public static void search(Context context, String title, TmdbUtil.OnTmdbSeriesSearchResponse response) {

            String url = getUrl(SEARCH, TV);
            url = addParam(url, PARAM_SEARCH, title);

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }

        public static void search(Context context, String title, int year, Response response) {

            String url = getUrl(SEARCH, TV);
            url = addParam(url, PARAM_SEARCH, title);
            if (year > 0) {
                url = addParam(url, "first_air_date_year", Integer.toString(year));
            }

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }

        public static void get(Context context, long id, Response response) {
            String url = getUrl(TV, "/" + id);

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }

        public static void getEpisode(Context context, long id, int season, int episode, Response response) {
            String url = getUrl(EPISODE)
                    .replace("{id}", Long.toString(id))
                    .replace("{season}", Integer.toString(season))
                    .replace("{episode}", Integer.toString(episode));

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }
    }

    public static class Movie extends Base {
        public static void search(Context context, String title, int year, Response response) {

            String url = getUrl(SEARCH, MOVIE);
            url = addParam(url, PARAM_SEARCH, title);
            url = addParam(url, PARAM_YEAR, Integer.toString(year));

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }

        public static void get(Context context, long id, Response response) {
            String url = getUrl(MOVIE, "/" + id) + "&append_to_response=credits,videos";

            Ion.with(context)
                    .load(url)
                    .asJsonObject()
                    .withResponse()
                    .setCallback(response);
        }
    }

}
