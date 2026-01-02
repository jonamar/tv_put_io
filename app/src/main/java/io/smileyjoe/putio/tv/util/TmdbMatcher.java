package io.smileyjoe.putio.tv.util;

import android.content.Context;
import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import io.smileyjoe.putio.tv.db.AppDatabase;
import io.smileyjoe.putio.tv.network.Response;
import io.smileyjoe.putio.tv.network.Tmdb;
import io.smileyjoe.putio.tv.object.TmdbCache;
import io.smileyjoe.putio.tv.util.Async;

/**
 * Lean hybrid TMDB matcher that generates title candidates, competes movie vs series searches,
 * and scores results using Levenshtein distance + popularity.
 */
public class TmdbMatcher {

    private static final String TAG = "TmdbMatcher";

    private static final Set<String> BRAND_PREFIXES = new HashSet<>(Arrays.asList(
            "amazon", "netflix", "hulu", "disney", "hbo", "apple"
    ));

    private static final double MIN_MATCH_SCORE = 0.6;

    /**
     * Result from TMDB matching
     */
    public static class MatchResult {
        public long tmdbId;
        public String contentType; // "movie" or "tv"
        public String matchedTitle;
        public double matchScore;
        public boolean fromCache;

        public MatchResult(long tmdbId, String contentType, String matchedTitle, double matchScore) {
            this.tmdbId = tmdbId;
            this.contentType = contentType;
            this.matchedTitle = matchedTitle;
            this.matchScore = matchScore;
            this.fromCache = false;
        }
    }

    /**
     * Generate 2-3 title candidates for searching
     */
    public static List<String> generateCandidates(String title) {
        List<String> candidates = new ArrayList<>();

        // Base cleaned title
        String cleaned = cleanTitle(title);
        candidates.add(cleaned);

        // Apostrophe variant for titles ending in 's' (e.g., "Bobs" -> "Bob's")
        String apostropheVariant = tryApostropheFix(cleaned);
        if (apostropheVariant != null && !apostropheVariant.equals(cleaned)) {
            candidates.add(apostropheVariant);
        }

        // Prefix-stripped variant (e.g., "Amazon Just Add Magic" -> "Just Add Magic")
        String prefixStripped = tryStripBrandPrefix(cleaned);
        if (prefixStripped != null && !prefixStripped.equals(cleaned)) {
            candidates.add(prefixStripped);
        }

        return candidates;
    }

    /**
     * Clean title by removing quality tags, brackets, etc.
     */
    private static String cleanTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return "";
        }

        // Remove quality indicators, brackets, group names, etc.
        String cleaned = title
                .replaceAll("(?i)\\b(1080p|720p|480p|2160p|4k|x264|x265|hevc|aac|mp3|flac|bluray|brrip|webrip|web-dl|hdtv|dvdrip|complete|season\\s*\\d+)\\b", "")
                .replaceAll("\\[.*?\\]", "") // Remove [brackets]
                .replaceAll("\\(.*?\\)", "") // Remove (parentheses) - careful with years
                .replaceAll("\\s+", " ")     // Collapse whitespace
                .trim();

        return cleaned;
    }

    /**
     * Try to add apostrophe for titles like "Bobs Burgers" -> "Bob's Burgers"
     */
    private static String tryApostropheFix(String title) {
        // Simple pattern: if a short word ends in 's' followed by a capital letter, add apostrophe
        // Example: "Bobs Burgers" -> "Bob's Burgers"
        String[] words = title.split("\\s+");
        if (words.length >= 2) {
            for (int i = 0; i < words.length - 1; i++) {
                String word = words[i];
                if (word.length() >= 3 && word.endsWith("s") && !word.endsWith("'s")) {
                    // Check if next word is capitalized
                    if (i + 1 < words.length && Character.isUpperCase(words[i + 1].charAt(0))) {
                        words[i] = word.substring(0, word.length() - 1) + "'s";
                        return String.join(" ", words);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Try to strip brand prefix if detected
     */
    private static String tryStripBrandPrefix(String title) {
        String[] words = title.split("\\s+");
        if (words.length >= 2) {
            String firstWord = words[0].toLowerCase(Locale.ROOT);
            if (BRAND_PREFIXES.contains(firstWord)) {
                // Remove first word
                return String.join(" ", Arrays.copyOfRange(words, 1, words.length));
            }
        }
        return null;
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private static int levenshteinDistance(String s1, String s2) {
        String a = s1.toLowerCase(Locale.ROOT);
        String b = s2.toLowerCase(Locale.ROOT);

        int[] costs = new int[b.length() + 1];
        for (int j = 0; j < costs.length; j++) {
            costs[j] = j;
        }

        for (int i = 1; i <= a.length(); i++) {
            costs[0] = i;
            int nw = i - 1;
            for (int j = 1; j <= b.length(); j++) {
                int cj = Math.min(Math.min(costs[j] + 1, costs[j - 1] + 1),
                                  a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
                nw = costs[j];
                costs[j] = cj;
            }
        }

        return costs[b.length()];
    }

    /**
     * Calculate match score (0.0 to 1.0) based on Levenshtein distance
     */
    private static double calculateMatchScore(String queryTitle, String resultTitle) {
        if (TextUtils.isEmpty(queryTitle) || TextUtils.isEmpty(resultTitle)) {
            return 0.0;
        }

        int distance = levenshteinDistance(queryTitle, resultTitle);
        int maxLen = Math.max(queryTitle.length(), resultTitle.length());

        if (maxLen == 0) {
            return 1.0;
        }

        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Score a single search result
     */
    private static class ScoredResult {
        JsonObject result;
        String contentType;
        double matchScore;
        double popularity;

        ScoredResult(JsonObject result, String contentType, String queryTitle) {
            this.result = result;
            this.contentType = contentType;

            JsonUtil json = new JsonUtil(result);
            String resultTitle = contentType.equals("movie")
                    ? json.getString("title")
                    : json.getString("name");

            this.matchScore = calculateMatchScore(queryTitle, resultTitle);
            // Get popularity as double (JsonUtil doesn't have getDouble, so use direct access)
            try {
                this.popularity = result.has("popularity") ? result.get("popularity").getAsDouble() : 0.0;
            } catch (Exception e) {
                this.popularity = 0.0;
            }
        }

        boolean isGoodMatch() {
            return matchScore >= MIN_MATCH_SCORE;
        }
    }

    /**
     * Find best match from combined movie + series results
     */
    private static ScoredResult findBestMatch(JsonArray movieResults, JsonArray seriesResults, String queryTitle) {
        List<ScoredResult> allResults = new ArrayList<>();

        // Score movie results
        if (movieResults != null) {
            for (JsonElement element : movieResults) {
                allResults.add(new ScoredResult(element.getAsJsonObject(), "movie", queryTitle));
            }
        }

        // Score series results
        if (seriesResults != null) {
            for (JsonElement element : seriesResults) {
                allResults.add(new ScoredResult(element.getAsJsonObject(), "tv", queryTitle));
            }
        }

        // Find best match
        ScoredResult best = null;
        for (ScoredResult result : allResults) {
            if (!result.isGoodMatch()) {
                continue;
            }

            if (best == null || result.matchScore > best.matchScore) {
                best = result;
            } else if (result.matchScore == best.matchScore && result.popularity > best.popularity) {
                // Tiebreaker: use popularity
                best = result;
            }
        }

        return best;
    }

    /**
     * Generate cache key from filename
     */
    private static String getCacheKey(String filename) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(filename.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(filename.hashCode());
        }
    }

    /**
     * Check cache for previous match
     */
    private static MatchResult checkCache(Context context, String filename) {
        String cacheKey = getCacheKey(filename);
        TmdbCache cached = AppDatabase.getInstance(context).tmdbCacheDao().getByFilenameHash(cacheKey);

        if (cached != null) {
            MatchResult result = new MatchResult(
                    cached.getTmdbId(),
                    cached.getContentType(),
                    cached.getMatchedTitle(),
                    cached.getMatchScore()
            );
            result.fromCache = true;
            return result;
        }

        return null;
    }

    /**
     * Save match to cache
     */
    private static void saveToCache(Context context, String filename, MatchResult match) {
        TmdbCache cache = new TmdbCache();
        cache.setFilenameHash(getCacheKey(filename));
        cache.setTmdbId(match.tmdbId);
        cache.setContentType(match.contentType);
        cache.setMatchedTitle(match.matchedTitle);
        cache.setMatchScore(match.matchScore);
        cache.setTimestamp(System.currentTimeMillis());

        AppDatabase.getInstance(context).tmdbCacheDao().insert(cache);
    }

    /**
     * Search callback interface
     */
    public interface OnMatchListener {
        void onMatch(MatchResult result);
        void onNoMatch();
    }

    /**
     * Main entry point: Search for best TMDB match using lean hybrid approach
     * This will:
     * 1. Check cache first
     * 2. Generate 2-3 title candidates
     * 3. Search both movie AND series for each candidate (4-6 API calls max)
     * 4. Score results using Levenshtein + popularity
     * 5. Cache the result
     */
    public static void findBestMatch(Context context, String filename, String title, int year, OnMatchListener listener) {
        // Check cache first
        MatchResult cached = checkCache(context, filename);
        if (cached != null) {
            android.util.Log.d(TAG, "Cache hit for: " + filename);
            listener.onMatch(cached);
            return;
        }

        // Generate candidates
        List<String> candidates = generateCandidates(title);
        android.util.Log.d(TAG, "Generated " + candidates.size() + " candidates for: " + title);
        for (String candidate : candidates) {
            android.util.Log.d(TAG, "  - " + candidate);
        }

        // Search using first candidate only (lean approach - keep API calls minimal)
        // Full candidate search can be added later if needed
        String searchTitle = candidates.get(0);

        // Execute hybrid search: both movie AND series in parallel
        HybridSearchHandler handler = new HybridSearchHandler(context, filename, searchTitle, year, listener);
        handler.execute();
    }

    /**
     * Handles parallel movie + series search and scoring
     */
    private static class HybridSearchHandler {
        private Context context;
        private String filename;
        private String searchTitle;
        private int year;
        private OnMatchListener listener;

        private JsonArray movieResults;
        private JsonArray seriesResults;
        private boolean movieSearchComplete = false;
        private boolean seriesSearchComplete = false;

        HybridSearchHandler(Context context, String filename, String searchTitle, int year, OnMatchListener listener) {
            this.context = context;
            this.filename = filename;
            this.searchTitle = searchTitle;
            this.year = year;
            this.listener = listener;
        }

        void execute() {
            // Search both movie and series in parallel
            Tmdb.Movie.search(context, searchTitle, year, new Response() {
                @Override
                public void onSuccess(JsonObject result) {
                    synchronized (HybridSearchHandler.this) {
                        movieResults = result.has("results") ? result.getAsJsonArray("results") : null;
                        movieSearchComplete = true;
                        checkComplete();
                    }
                }

                @Override
                public void onFail(Exception e) {
                    synchronized (HybridSearchHandler.this) {
                        movieResults = null;
                        movieSearchComplete = true;
                        checkComplete();
                    }
                }
            });

            Tmdb.Series.search(context, searchTitle, year, new Response() {
                @Override
                public void onSuccess(JsonObject result) {
                    synchronized (HybridSearchHandler.this) {
                        seriesResults = result.has("results") ? result.getAsJsonArray("results") : null;
                        seriesSearchComplete = true;
                        checkComplete();
                    }
                }

                @Override
                public void onFail(Exception e) {
                    synchronized (HybridSearchHandler.this) {
                        seriesResults = null;
                        seriesSearchComplete = true;
                        checkComplete();
                    }
                }
            });
        }

        private void checkComplete() {
            if (movieSearchComplete && seriesSearchComplete) {
                processResults();
            }
        }

        private void processResults() {
            android.util.Log.d(TAG, "Hybrid search complete for: " + searchTitle);
            android.util.Log.d(TAG, "  Movie results: " + (movieResults != null ? movieResults.size() : 0));
            android.util.Log.d(TAG, "  Series results: " + (seriesResults != null ? seriesResults.size() : 0));

            ScoredResult best = findBestMatch(movieResults, seriesResults, searchTitle);

            if (best != null && best.isGoodMatch()) {
                JsonUtil json = new JsonUtil(best.result);
                long tmdbId = json.getLong("id");
                String matchedTitle = best.contentType.equals("movie")
                        ? json.getString("title")
                        : json.getString("name");

                MatchResult match = new MatchResult(tmdbId, best.contentType, matchedTitle, best.matchScore);

                android.util.Log.d(TAG, "Best match: " + matchedTitle + " (" + best.contentType + ") score=" + best.matchScore);

                // Save to cache on background thread
                Async.run(() -> {
                    saveToCache(context, filename, match);
                });

                listener.onMatch(match);
            } else {
                android.util.Log.d(TAG, "No good match found (threshold: " + MIN_MATCH_SCORE + ")");
                listener.onNoMatch();
            }
        }
    }
}
