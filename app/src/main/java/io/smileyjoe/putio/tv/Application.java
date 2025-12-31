package io.smileyjoe.putio.tv;

import android.content.Context;

import java.util.Arrays;

import io.smileyjoe.putio.tv.channel.ChannelType;
import io.smileyjoe.putio.tv.channel.Channels;
import io.smileyjoe.putio.tv.util.SharedPrefs;

public class Application extends android.app.Application {

    private static String sPutToken;
    private static Context sApplicationContext;

    public void onCreate() {
        super.onCreate();

        // Setup crash handler for better debugging
        setupCrashHandler();

        sApplicationContext = getApplicationContext();
        setPutToken(SharedPrefs.getInstance(getApplicationContext()).getPutToken());

        // DISABLED: Channel creation causes crashes on Android 7.1
        // Preview channels are non-critical feature - can be re-enabled after debugging
        // Arrays.stream(ChannelType.values()).forEach(type -> Channels.create(getBaseContext(), type));

        // Alternative: Create channels in background with error handling
        createChannelsSafely();
    }

    private void setupCrashHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            // Log the crash
            android.util.Log.e("PUT.IO_CRASH", "App crashed!", throwable);

            // Show error in system UI (will appear in logcat and crash dialog)
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        });
    }

    private void createChannelsSafely() {
        // Run in background thread to avoid blocking app startup
        new Thread(() -> {
            try {
                // Small delay to ensure app is fully initialized
                Thread.sleep(2000);
                Arrays.stream(ChannelType.values()).forEach(type -> {
                    try {
                        Channels.create(getBaseContext(), type);
                    } catch (Exception e) {
                        // Log but don't crash - channels are optional
                        android.util.Log.w("PUT.IO_CHANNELS", "Failed to create channel: " + type, e);
                    }
                });
            } catch (Exception e) {
                android.util.Log.w("PUT.IO_CHANNELS", "Channel creation failed", e);
            }
        }).start();
    }

    public static Context getStaticContext() {
        return sApplicationContext;
    }

    public static String getPutToken() {
        return sPutToken;
    }

    public static void setPutToken(String putToken) {
        sPutToken = putToken;
    }
}
