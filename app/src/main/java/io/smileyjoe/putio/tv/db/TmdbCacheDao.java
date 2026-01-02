package io.smileyjoe.putio.tv.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import io.smileyjoe.putio.tv.object.TmdbCache;

@Dao
public interface TmdbCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(TmdbCache tmdbCache);

    @Query("SELECT * FROM tmdb_cache WHERE filename_hash = :filenameHash LIMIT 1")
    TmdbCache getByFilenameHash(String filenameHash);

    @Query("DELETE FROM tmdb_cache WHERE timestamp < :timestamp")
    void deleteOlderThan(long timestamp);
}
