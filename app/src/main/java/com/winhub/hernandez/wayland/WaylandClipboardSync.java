package com.winhub.hernandez.wayland;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Keeps Android's clipboard and the Wayland guest's selection in sync (text only).
 *
 * Guest → Android: the compositor reads what a program copied and calls
 * {@link WaylandCompositor.ClipboardListener}; the text goes to {@link ClipboardManager} on the
 * main thread. Android → guest: {@link ClipboardManager.OnPrimaryClipChangedListener} and
 * {@link #refresh()} (resume / focus gain: Android hides clipboard changes from background apps)
 * only look at the clip's description and tell the compositor that text is there. The text itself
 * is read when a program pastes ({@link #onClipboardRequest()}), because reading it makes Android
 * show "pasted from your clipboard"; without this every game start showed that notice. Text that
 * came from the guest is not offered back.
 */
public final class WaylandClipboardSync implements WaylandCompositor.ClipboardListener,
        WaylandCompositor.ClipboardRequestListener, ClipboardManager.OnPrimaryClipChangedListener {
    private static final String TAG = "WaylandClipboard";
    private static final String GUEST_LABEL = "Wayland guest";

    private final Context context;
    private final ClipboardManager clipboard;
    private final Handler main = new Handler(Looper.getMainLooper());
    private long lastOffered = -1;  // timestamp of the clip last offered to the guest
    private boolean started;

    public WaylandClipboardSync(Activity activity) {
        context = activity.getApplicationContext();
        clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
    }

    /** Register both directions. Call once the compositor is up (main thread). */
    public void start() {
        if (started || clipboard == null) return;
        started = true;
        WaylandCompositor.setClipboardListener(this);
        WaylandCompositor.setClipboardRequestListener(this);
        clipboard.addPrimaryClipChangedListener(this);
        refresh();
    }

    public void stop() {
        if (!started) return;
        started = false;
        WaylandCompositor.setClipboardListener(null);
        WaylandCompositor.setClipboardRequestListener(null);
        if (clipboard != null) clipboard.removePrimaryClipChangedListener(this);
    }

    /** Offer new Android clipboard text to the guest, unread (resume / window focus / change). */
    public void refresh() {
        if (!started || clipboard == null) return;
        ClipDescription desc;
        try {
            desc = clipboard.getPrimaryClipDescription();   // no "pasted" notice, unlike getPrimaryClip
        } catch (Exception e) {
            Log.w(TAG, "clipboard description failed", e);
            return;
        }
        if (desc == null) return;
        if (GUEST_LABEL.contentEquals(desc.getLabel() == null ? "" : desc.getLabel())) return;
        if (!desc.hasMimeType("text/*")) return;
        long stamp = desc.getTimestamp();
        if (stamp == lastOffered) return;
        lastOffered = stamp;
        WaylandCompositor.setClipboardPending();
    }

    @Override
    public void onClipboardRequest() {
        main.post(() -> {
            String text = started ? currentText() : null;
            WaylandCompositor.setClipboardText(text == null ? "" : text);
        });
    }

    private String currentText() {
        try {
            if (!clipboard.hasPrimaryClip()) return null;
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence cs = clip.getItemAt(0).coerceToText(context);
            return cs == null ? null : cs.toString();
        } catch (Exception e) {
            Log.w(TAG, "clipboard read failed", e);
            return null;
        }
    }

    @Override
    public void onPrimaryClipChanged() {
        refresh();
    }

    @Override
    public void onGuestClipboardText(String text) {
        if (text == null || clipboard == null) return;
        main.post(() -> {
            try {
                clipboard.setPrimaryClip(ClipData.newPlainText(GUEST_LABEL, text));
            } catch (Exception e) {
                Log.w(TAG, "clipboard write failed", e);
            }
        });
    }
}
