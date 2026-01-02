package io.smileyjoe.putio.tv.object;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "tmdb_cache",
        indices = {@Index("filename_hash")})
public class TmdbCache {

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private int mId;

    @ColumnInfo(name = "filename_hash")
    private String mFilenameHash;

    @ColumnInfo(name = "tmdb_id")
    private long mTmdbId;

    @ColumnInfo(name = "content_type")
    private String mContentType;

    @ColumnInfo(name = "matched_title")
    private String mMatchedTitle;

    @ColumnInfo(name = "match_score")
    private double mMatchScore;

    @ColumnInfo(name = "timestamp")
    private long mTimestamp;

    public int getId() {
        return mId;
    }

    public void setId(int id) {
        mId = id;
    }

    public String getFilenameHash() {
        return mFilenameHash;
    }

    public void setFilenameHash(String filenameHash) {
        mFilenameHash = filenameHash;
    }

    public long getTmdbId() {
        return mTmdbId;
    }

    public void setTmdbId(long tmdbId) {
        mTmdbId = tmdbId;
    }

    public String getContentType() {
        return mContentType;
    }

    public void setContentType(String contentType) {
        mContentType = contentType;
    }

    public String getMatchedTitle() {
        return mMatchedTitle;
    }

    public void setMatchedTitle(String matchedTitle) {
        mMatchedTitle = matchedTitle;
    }

    public double getMatchScore() {
        return mMatchScore;
    }

    public void setMatchScore(double matchScore) {
        mMatchScore = matchScore;
    }

    public long getTimestamp() {
        return mTimestamp;
    }

    public void setTimestamp(long timestamp) {
        mTimestamp = timestamp;
    }
}
