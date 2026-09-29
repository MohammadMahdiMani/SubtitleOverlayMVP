package com.example.subtitleoverlay;

import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
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
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState state) { push(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { push(); }
    };

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        manager = (MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);
        refresh();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) { refresh(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { refresh(); }

    private synchronized void refresh() {
        if (manager == null) return;
        clearCallbacks();
        try {
            List<MediaController> list = manager.getActiveSessions(getComponentName());
            if (list != null) controllers.addAll(list);
            for (MediaController c : controllers) c.registerCallback(callback);
            push();
        } catch (SecurityException ignored) {}
    }

    private synchronized void clearCallbacks() {
        for (MediaController c : controllers) { try { c.unregisterCallback(callback); } catch (Exception ignored) {} }
        controllers.clear();
    }

    private void push() {
        MediaController best = null;
        String preferred = SubtitleAccessibilityService.getActivePackage();
        long now = android.os.SystemClock.elapsedRealtime();
        for (MediaController c : controllers) {
            android.media.session.PlaybackState s = c.getPlaybackState();
            if (s == null) continue;
            boolean playing = s.getState() == android.media.session.PlaybackState.STATE_PLAYING;
            if (!playing && best == null) continue;
            if (preferred != null && preferred.equals(c.getPackageName())) { best = c; if (playing) break; }
            else if (best == null && playing) best = c;
        }
        if (best == null) return;
        android.media.session.PlaybackState s = best.getPlaybackState();
        if (s == null || s.getPosition() < 0) return;
        long position = s.getPosition();
        float speed = s.getPlaybackSpeed();
        if (s.getState() == android.media.session.PlaybackState.STATE_PLAYING && speed != 0f) {
            long delta = now - s.getLastPositionUpdateTime();
            if (delta > 0 && delta < 10000) position += (long)(delta * speed);
        }
        long duration = -1;
        MediaMetadata md = best.getMetadata();
        if (md != null && md.containsKey(MediaMetadata.METADATA_KEY_DURATION)) duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION);
        OverlayService.setMediaPosition(position, speed, duration, best.getPackageName(), s.getState() == android.media.session.PlaybackState.STATE_PLAYING);
    }

    @Override public void onListenerDisconnected() { clearCallbacks(); super.onListenerDisconnected(); }
}
