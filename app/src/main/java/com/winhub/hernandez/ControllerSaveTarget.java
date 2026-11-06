package com.winhub.hernandez;

import android.content.SharedPreferences;
import android.util.Log;

import com.winhub.hernandez.container.Container;
import com.winhub.hernandez.container.Shortcut;
import com.winhub.hernandez.core.DInputVisibility;
import com.winhub.hernandez.ui.XServerDialogState;

/**
 * Where the in-game controller and rumble changes (Controls › Pad and › Vibration) are saved.
 * A session started from a game saves them for that game, unless "Save for: All games in container"
 * is picked (remembered for the app). A desktop / container launch saves to the container.
 *
 * <p>"All games in container" also drops this game's own value of the setting that changed, so the
 * game follows the container like the others. The footer under those rows says where the last change
 * went. Main thread.
 */
final class ControllerSaveTarget {
    private static final String TAG = "ControllerSave";
    /** "game" (default) or "container", for every game session in the app. */
    static final String PREF_KEY = "ingame_controller_save_scope";
    private static final String SCOPE_GAME = "game";
    private static final String SCOPE_CONTAINER = "container";

    private final SharedPreferences preferences;
    private final Container container;
    private final Shortcut shortcut;
    private boolean toGame;
    private String lastSaved;

    ControllerSaveTarget(SharedPreferences preferences, Container container, Shortcut shortcut) {
        this.preferences = preferences;
        this.container = container;
        this.shortcut = shortcut;
        this.toGame = shortcut != null
                && !SCOPE_CONTAINER.equals(preferences.getString(PREF_KEY, SCOPE_GAME));
    }

    /** Wires the footer's "Save for" choice and shows where changes go. */
    void attach() {
        XServerDialogState.INSTANCE.onControllerSaveScope = this::setToGame;
        publish();
    }

    private void setToGame(boolean game) {
        if (shortcut == null || game == toGame) return;
        toGame = game;
        preferences.edit().putString(PREF_KEY, game ? SCOPE_GAME : SCOPE_CONTAINER).apply();
        lastSaved = null;
        Log.i(TAG, "in-game changes now save for " + (game ? "this game" : "all games in the container"));
        publish();
    }

    /**
     * A setting kept under the same extra key on the game and the container: the game's own value
     * ({@code gameValue}), or the container's through {@code writeContainer}.
     */
    void save(String key, String gameValue, Runnable writeContainer) {
        if (toGame) {
            shortcut.putExtra(key, gameValue);
            shortcut.saveData();
        } else {
            writeContainer.run();
            container.saveData();
            if (shortcut != null && shortcut.hasExtra(key)) {
                shortcut.removeExtra(key);
                shortcut.saveData();
            }
        }
        noteSaved(key);
    }

    /** DirectInput visibility: two extras (DInputVisibility), on the game or the container. */
    void saveDInput(boolean visible) {
        if (toGame) {
            DInputVisibility.writeTo(shortcut, visible);
            shortcut.saveData();
        } else {
            DInputVisibility.writeTo(container, visible);
            container.saveData();
            if (DInputVisibility.gameOwns(shortcut)) {
                DInputVisibility.writeTo(shortcut, (Boolean) null);
                shortcut.saveData();
            }
        }
        noteSaved("DirectInput visibility");
    }

    private void noteSaved(String what) {
        lastSaved = toGame ? "Saved for " + shortcut.name + " only."
                : "Saved for every game in " + container.getName() + ".";
        Log.i(TAG, what + ": " + lastSaved);
        publish();
    }

    private void publish() {
        XServerDialogState.INSTANCE.setControllerSaveScope(new XServerDialogState.ControllerSaveScope(
                shortcut != null ? shortcut.name : null,
                container != null ? container.getName() : "",
                toGame,
                lastSaved));
    }
}
