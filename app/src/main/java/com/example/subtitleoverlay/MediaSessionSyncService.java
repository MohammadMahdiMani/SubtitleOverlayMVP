package com.example.subtitleoverlay;

import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.media.session.MediaSessionManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads Android's exposed MediaSession position. It never reads the video stream.
 * When Brave or another browser exposes a MediaSession, this gives us real playback
 * position and playback speed, including pause/seek/resume.
 */
public class MediaSessionSyncService extends NotificationListenerService {
    private MediaSessionManager manager;
    private final List<MediaController> controllers = new ArrayList<>();
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private long lastControllerRefresh = 0;
    private static final long POLL_INTERVAL_MS = 500;
    private static final long REFRESH_INTERVAL_MS = 2500;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (manager != null) {
                long now = SystemClock.elapsedRealtime();
                if (now - lastControllerRefresh >= REFRESH_INTERVAL_MS) refresh();
                else push();
            }
            pollHandler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state) { push(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { push(); }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        manager = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        lastControllerRefresh = SystemClock.elapsedRealtime();
        refresh();
        pollHandler.removeCallbacks(poll);
        pollHandler.postDelayed(poll, POLL_INTERVAL_MS);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    private synchronized void refresh() {
        if (manager == null) return;
        lastControllerRefresh = SystemClock.elapsedRealtime();
        clearCallbacks();
        try {
            List<MediaController> list = manager.getActiveSessions(new ComponentName(this, MediaSessionSyncService.class));
            if (list != null) controllers.addAll(list);
            for (MediaController c : controllers) c.registerCallback(callback);
            push();
        } catch (SecurityException ignored) {}
    }

    private synchronized void clearCallbacks() {
        for (MediaController c : controllers) { try { c.unregisterCallback(callback); } catch (Exception ignored) {} }
        controllers.clear();
    }

    private synchronized void push() {
        MediaController bestPlayingPreferred = null;
        MediaController bestPlaying = null;
        MediaController bestPausedPreferred = null;
        MediaController bestPaused = null;
        String preferred = SubtitleAccessibilityService.getActivePackage();

        // Prefer the active browser's playing session, but never let a stale paused
        // session prevent us from using another currently playing media session.
        for (MediaController c : controllers) {
            android.media.session.PlaybackState state;
            try { state = c.getPlaybackState(); } catch (Exception ignored) { continue; }
            if (state == null || state.getPosition() < 0) continue;
            boolean playing = state.getState() == android.media.session.PlaybackState.STATE_PLAYING;
            boolean matches = preferred != null && preferred.equals(c.getPackageName());
            if (playing && matches && bestPlayingPreferred == null) bestPlayingPreferred = c;
            else if (playing && bestPlaying == null) bestPlaying = c;
            else if (!playing && matches && bestPausedPreferred == null) bestPausedPreferred = c;
            else if (!playing && bestPaused == null) bestPaused = c;
        }
        MediaController best = bestPlayingPreferred != null ? bestPlayingPreferred
                : bestPlaying != null ? bestPlaying
                : bestPausedPreferred != null ? bestPausedPreferred : bestPaused;
        if (best == null) return;

        android.media.session.PlaybackState state;
        try { state = best.getPlaybackState(); } catch (Exception ignored) { return; }
        if (state == null || state.getPosition() < 0) return;
        long now = SystemClock.elapsedRealtime();
        long position = state.getPosition();
        float speed = state.getPlaybackSpeed();
        boolean playing = state.getState() == android.media.session.PlaybackState.STATE_PLAYING;
        if (playing && speed > 0f && speed < 4f) {
            long lastUpdate = state.getLastPositionUpdateTime();
            long delta = now - lastUpdate;
            // PlaybackState timestamps use elapsed realtime. Avoid extrapolating
            // from a future or implausibly old anchor (six hours is the safety cap).
            if (lastUpdate > 0 && delta >= 0 && delta < 21600000) {
                position += (long) (delta * speed);
            }
        }
        long duration = -1;
        MediaMetadata md = best.getMetadata();
        if (md != null && md.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        }
        OverlayService.setMediaPosition(position, speed, duration, best.getPackageName(), playing);
    }

    @Override public void onListenerDisconnected() {
        pollHandler.removeCallbacksAndMessages(null);
        clearCallbacks();
        super.onListenerDisconnected();
    }

    @Override public void onDestroy() {
        pollHandler.removeCallbacksAndMessages(null);
        clearCallbacks();
        super.onDestroy();
    }
}
