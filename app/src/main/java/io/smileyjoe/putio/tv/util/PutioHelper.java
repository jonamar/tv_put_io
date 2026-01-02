package io.smileyjoe.putio.tv.util;

import android.content.Context;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;

import io.smileyjoe.putio.tv.comparator.FolderComparator;
import io.smileyjoe.putio.tv.db.AppDatabase;
import io.smileyjoe.putio.tv.interfaces.Folder;
import io.smileyjoe.putio.tv.network.Tmdb;
import io.smileyjoe.putio.tv.object.Directory;
import io.smileyjoe.putio.tv.object.FileType;
import io.smileyjoe.putio.tv.object.Video;
import io.smileyjoe.putio.tv.object.VirtualDirectory;

public class PutioHelper {

    private ArrayList<Folder> mFolders;
    private ArrayList<Video> mVideos;
    private Video mCurrent;
    private Context mContext;

    public PutioHelper(Context context) {
        mContext = context;
        mFolders = new ArrayList<>();
        mVideos = new ArrayList<>();
    }

    public ArrayList<Video> getVideos() {
        return mVideos;
    }

    public ArrayList<Folder> getFolders() {
        return mFolders;
    }

    public Video getCurrent() {
        return mCurrent;
    }

    public void parse(long putId, JsonObject jsonObject) {
        parse(putId, 0, jsonObject);
    }

    public void parse(long putId, long parentTmdbId, JsonObject jsonObject) {
        JsonArray filesJson = jsonObject.getAsJsonArray("files");

        try {
            JsonObject parentObject = jsonObject.getAsJsonObject("parent");
            mCurrent = VideoUtil.parseFromPut(mContext, parentObject);
        } catch (ClassCastException e) {
            mCurrent = VirtualDirectory.getFromPutId(mContext, putId).asVideo();
        }

        if (mCurrent.getFileType() == FileType.FOLDER) {

            ArrayList<Video> videos = VideoUtil.filter(VideoUtil.parseFromPut(mContext, filesJson));

            if (videos != null && videos.size() == 1) {
                Video currentDbVideo = VideoUtil.getFromDbByPutId(mContext, mCurrent.getPutId());

                if (currentDbVideo != null && currentDbVideo.isTmdbFound()) {
                    Video updated = VideoUtil.updateFromDb(videos.get(0), currentDbVideo);
                    AppDatabase.getInstance(mContext).videoDao().insert(updated);
                }
            }

            for (Video video : videos) {
                updateTmdb(mCurrent.getTmdbId(), video);
            }
        } else {
            Video currentDbVideo = VideoUtil.getFromDbByPutId(mContext, mCurrent.getPutId());

            if (currentDbVideo != null) {
                if (currentDbVideo.isTmdbFound()) {
                    Video updated = VideoUtil.updateFromDb(mCurrent, currentDbVideo);
                    AppDatabase.getInstance(mContext).videoDao().insert(updated);
                    mVideos.add(updated);
                } else {
                    AppDatabase.getInstance(mContext).videoDao().insert(mCurrent);
                    updateTmdb(parentTmdbId, currentDbVideo);
                }
            } else {
                AppDatabase.getInstance(mContext).videoDao().insert(mCurrent);
                updateTmdb(parentTmdbId, mCurrent);
            }
        }

        VideoUtil.sort(mVideos);
        Collections.sort(mFolders, new FolderComparator());
    }

    private void updateTmdb(long parentTmdbId, Video video) {
        switch (video.getVideoType()) {
            case MOVIE:
                if (!video.isTmdbChecked()) {
                    // Use hybrid matcher for better accuracy
                    TmdbMatcher.findBestMatch(mContext, video.getPutTitle(), video.getTitle(), video.getYear(),
                            new TmdbMatcher.OnMatchListener() {
                                @Override
                                public void onMatch(TmdbMatcher.MatchResult result) {
                                    // Get full details from TMDB
                                    TmdbUtil.OnTmdbResponse response = new TmdbUtil.OnTmdbResponse(mContext, video);
                                    if (result.contentType.equals("movie")) {
                                        Tmdb.Movie.get(mContext, result.tmdbId, response);
                                    } else {
                                        Tmdb.Series.get(mContext, result.tmdbId, response);
                                    }
                                }

                                @Override
                                public void onNoMatch() {
                                    video.isTmdbChecked(true);
                                    video.isTmdbFound(false);
                                    Async.run(() -> {
                                        AppDatabase.getInstance(mContext).videoDao().insert(video);
                                    });
                                }
                            });
                }
                mVideos.add(video);
                break;
            case EPISODE:
                if (!video.isTmdbChecked() && parentTmdbId > 0) {
                    video.setParentTmdbId(parentTmdbId);
                    TmdbUtil.OnTmdbResponse response = new TmdbUtil.OnTmdbResponse(mContext, video);
                    Tmdb.Series.getEpisode(mContext, parentTmdbId, video.getSeason(), video.getEpisode(), response);
                }
                mVideos.add(video);
                break;
            case SEASON:
                if (!video.isTmdbChecked()) {
                    // Use hybrid matcher for series as well
                    TmdbMatcher.findBestMatch(mContext, video.getPutTitle(), video.getTitle(), video.getYear(),
                            new TmdbMatcher.OnMatchListener() {
                                @Override
                                public void onMatch(TmdbMatcher.MatchResult result) {
                                    // For seasons, we need series data
                                    TmdbUtil.OnTmdbResponse response = new TmdbUtil.OnTmdbResponse(mContext, video);
                                    if (result.contentType.equals("tv")) {
                                        Tmdb.Series.get(mContext, result.tmdbId, response);
                                    } else {
                                        // Matched as movie but it's actually a season - treat as no match
                                        onNoMatch();
                                    }
                                }

                                @Override
                                public void onNoMatch() {
                                    video.isTmdbChecked(true);
                                    video.isTmdbFound(false);
                                    Async.run(() -> {
                                        AppDatabase.getInstance(mContext).videoDao().insert(video);
                                    });
                                }
                            });
                }
                mVideos.add(video);
                break;
            case UNKNOWN:
                switch (video.getFileType()) {
                    case VIDEO:
                        // Use hybrid matcher for unknown videos (could be movie or series)
                        if (!video.isTmdbChecked()) {
                            TmdbMatcher.findBestMatch(mContext, video.getPutTitle(), video.getTitle(), video.getYear(),
                                    new TmdbMatcher.OnMatchListener() {
                                        @Override
                                        public void onMatch(TmdbMatcher.MatchResult result) {
                                            TmdbUtil.OnTmdbResponse response = new TmdbUtil.OnTmdbResponse(mContext, video);
                                            if (result.contentType.equals("movie")) {
                                                Tmdb.Movie.get(mContext, result.tmdbId, response);
                                            } else {
                                                Tmdb.Series.get(mContext, result.tmdbId, response);
                                            }
                                        }

                                        @Override
                                        public void onNoMatch() {
                                            video.isTmdbChecked(true);
                                            video.isTmdbFound(false);
                                            Async.run(() -> {
                                                AppDatabase.getInstance(mContext).videoDao().insert(video);
                                            });
                                        }
                                    });
                        }
                        mVideos.add(video);
                        break;
                    case FOLDER:
                    case UNKNOWN:
                    default:
                        mFolders.add(new Directory(video));
                        break;
                }
                break;
        }
    }

}
