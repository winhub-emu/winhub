package com.winhub.hernandez;

import android.graphics.Rect;
import android.net.Uri;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.compose.runtime.Composer;
import androidx.compose.ui.platform.ComposeView;

import com.winhub.hernandez.core.AppUtils;
import com.winhub.hernandez.inputcontrols.Binding;
import com.winhub.hernandez.inputcontrols.ControlElement;
import com.winhub.hernandez.inputcontrols.ControlsIconStyle;
import androidx.preference.PreferenceManager;
import com.winhub.hernandez.ui.XServerDrawerState;
import com.winhub.hernandez.inputcontrols.ControlsProfile;
import com.winhub.hernandez.inputcontrols.CustomIconManager;
import com.winhub.hernandez.widget.InputControlsView;

import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

final class InGameControlsEditor {
    private final XServerDisplayActivity activity;
    private final InputControlsView inputControlsView;
    private final ControlsProfile profile;
    private final CustomIconManager customIconManager;
    private final Runnable onDone;
    private final Runnable onPickCustomIcon;
    private final FrameLayout root;
    private final View sidebarOverlay;
    private final View sidebar;
    private final LinearLayout sidebarContent;
    private final ComposeView dialogHost;
    private ComposeView sidebarComposeView;
    private String activeDialogMode;
    private int sidebarReloadKey;

    InGameControlsEditor(
            XServerDisplayActivity activity,
            FrameLayout container,
            InputControlsView inputControlsView,
            ControlsProfile profile,
            Runnable onDone,
            Runnable onPickCustomIcon) {
        this.activity = activity;
        this.inputControlsView = inputControlsView;
        this.profile = profile;
        this.onDone = onDone;
        this.onPickCustomIcon = onPickCustomIcon;
        customIconManager = new CustomIconManager(activity);

        root = (FrameLayout)LayoutInflater.from(activity).inflate(
                R.layout.in_game_controls_editor_overlay, container, false);
        container.addView(root);
        root.bringToFront();

        sidebarOverlay = root.findViewById(R.id.InGameSidebarOverlay);
        sidebar = root.findViewById(R.id.InGameSidebar);
        sidebarContent = root.findViewById(R.id.InGameSidebarContent);
        dialogHost = root.findViewById(R.id.InGameComposeDialogHost);
        updateSidebarWidth(activity.getResources().getDisplayMetrics().widthPixels);
        root.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateSidebarWidth(right - left));

        sidebarOverlay.setOnTouchListener((view, event) -> {
            if (event.getAction() != MotionEvent.ACTION_DOWN) return true;
            ControlElement element = findElementAtScreen(event.getRawX(), event.getRawY());
            if (element != null) showSettings(element);
            else closeSidebar();
            return true;
        });

        ComposeView toolbar = root.findViewById(R.id.InGameComposeToolbar);
        toolbar.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                ControlsEditorToolbarKt.InGameControlsEditorToolbar(
                        profile.getName(),
                        action(() -> showDialog(ControlsEditorDialogsKt.DIALOG_ADD_ELEMENT)),
                        action(InGameControlsEditor.this::removeSelectedElement),
                        action(InGameControlsEditor.this::showSelectedSettings),
                        action(InGameControlsEditor.this::toggleGroupsDialog),
                        action(() -> showDialog(ControlsEditorDialogsKt.DIALOG_ICON_STYLE)),
                        action(onDone),
                        composer,
                        0);
                return Unit.INSTANCE;
            }
        });

        inputControlsView.setOnEditorElementSettingsRequested(this::showSettings);
        renderDialogHost();
    }

    private void updateSidebarWidth(int availableWidth) {
        if (availableWidth <= 0) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)sidebar.getLayoutParams();
        int width = ControlsEditorActivity.calculateSidebarWidth(
                availableWidth, activity.getResources().getDisplayMetrics().density);
        if (params.width != width) {
            params.width = width;
            sidebar.setLayoutParams(params);
        }
    }

    private static Function0<Unit> action(Runnable runnable) {
        return () -> {
            runnable.run();
            return Unit.INSTANCE;
        };
    }

    void dispose() {
        activeDialogMode = null;
        inputControlsView.setOnEditorElementSettingsRequested(null);
        save();
        if (root.getParent() instanceof FrameLayout) {
            ((FrameLayout)root.getParent()).removeView(root);
        }
    }

    void save() {
        if (profile != null) profile.save();
    }

    boolean isOpen() {
        return root.getParent() != null;
    }

    boolean handleBack() {
        if (activeDialogMode != null) {
            closeDialog();
            return true;
        }
        if (sidebar.getVisibility() == View.VISIBLE) {
            closeSidebar();
            return true;
        }
        return false;
    }

    void addCustomIcon(Uri uri) {
        ControlElement element = inputControlsView.getSelectedElement();
        if (element == null || uri == null) return;
        short iconId = customIconManager.addCustomIcon(uri);
        if (iconId < 0) {
            AppUtils.showToast(activity, R.string.unable_to_load_image);
            return;
        }
        element.setIconId(iconId);
        profile.save();
        inputControlsView.invalidate();
        refreshSidebar();
    }

    private void showSelectedSettings() {
        ControlElement element = inputControlsView.getSelectedElement();
        if (element != null) showSettings(element);
        else AppUtils.showToast(activity, R.string.no_control_element_selected);
    }

    private void removeSelectedElement() {
        if (inputControlsView.removeElement()) closeSidebar();
        else AppUtils.showToast(activity, R.string.no_control_element_selected);
    }

    private void toggleGroupsDialog() {
        if (ControlsEditorDialogsKt.DIALOG_GROUP_VISIBILITY.equals(activeDialogMode)) closeDialog();
        else showDialog(ControlsEditorDialogsKt.DIALOG_GROUP_VISIBILITY);
    }

    private void showSettings(ControlElement element) {
        if (element == null) return;
        inputControlsView.selectElementAt(element);
        ensureSidebarComposeView();
        bindSidebar(element);

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams)sidebar.getLayoutParams();
        Rect bounds = element.getBoundingBox();
        float centerX = bounds != null && !bounds.isEmpty() ? bounds.centerX() : element.getX();
        params.gravity = centerX <= inputControlsView.getWidth() / 2f
                ? Gravity.END | Gravity.TOP
                : Gravity.START | Gravity.TOP;
        sidebar.setLayoutParams(params);
        sidebarOverlay.setVisibility(View.VISIBLE);
        sidebar.setVisibility(View.VISIBLE);
        sidebar.bringToFront();
    }

    private void closeSidebar() {
        if (profile != null) profile.save();
        sidebar.setVisibility(View.GONE);
        sidebarOverlay.setVisibility(View.GONE);
    }

    private void ensureSidebarComposeView() {
        if (sidebarComposeView == null) sidebarComposeView = new ComposeView(activity);
        if (sidebarComposeView.getParent() == null) sidebarContent.addView(sidebarComposeView);
    }

    private void bindSidebar(ControlElement element) {
        final int reloadKey = sidebarReloadKey;
        sidebarComposeView.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                ControlsEditorSettingsPaneKt.ControlsEditorSettingsPane(
                        element,
                        profile,
                        action(inputControlsView::invalidate),
                        customIconManager,
                        reloadKey,
                        activity,
                        action(InGameControlsEditor.this::closeSidebar),
                        action(() -> {
                            closeDialog();
                            onPickCustomIcon.run();
                        }),
                        new Function1<Integer, Boolean>() {
                            @Override
                            public Boolean invoke(Integer iconId) {
                                if (iconId == null || !customIconManager.deleteIconIfUnused(iconId)) return false;
                                inputControlsView.evictCustomIcon(iconId);
                                refreshSidebar();
                                return true;
                            }
                        },
                        composer,
                        0);
                return Unit.INSTANCE;
            }
        });
    }

    private void refreshSidebar() {
        if (sidebar.getVisibility() != View.VISIBLE) return;
        ControlElement element = inputControlsView.getSelectedElement();
        if (element == null) return;
        sidebarReloadKey++;
        ensureSidebarComposeView();
        bindSidebar(element);
    }

    private ControlElement findElementAtScreen(float rawX, float rawY) {
        if (!profile.isElementsLoaded()) return null;
        int[] location = new int[2];
        inputControlsView.getLocationOnScreen(location);
        float x = rawX - location[0];
        float y = rawY - location[1];
        List<ControlElement> elements = profile.getElements();
        for (int index = elements.size() - 1; index >= 0; index--) {
            ControlElement element = elements.get(index);
            if (element.isInGroup() && !profile.isGroupVisible(element.getGroupId())) continue;
            if (element.containsPoint(x, y)) return element;
        }
        return null;
    }

    private void showDialog(String mode) {
        activeDialogMode = mode;
        renderDialogHost();
    }

    private void closeDialog() {
        activeDialogMode = null;
        renderDialogHost();
    }

    private void renderDialogHost() {
        dialogHost.setVisibility(activeDialogMode == null ? View.GONE : View.VISIBLE);
        final String mode = activeDialogMode;
        dialogHost.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                ControlsEditorDialogsKt.ControlsEditorDialogHost(
                        mode,
                        profile,
                        0f,
                        inputControlsView.getIconStyle().key(),
                        new ControlsEditorDialogActions() {
                            @Override
                            public void onDismiss() {
                                closeDialog();
                            }

                            @Override
                            public void onAddElement(ControlElement.Type type) {
                                if (!inputControlsView.addElement(type)) {
                                    AppUtils.showToast(activity, R.string.no_profile_selected);
                                    return;
                                }
                                closeDialog();
                                showSelectedSettings();
                            }

                            @Override
                            public void onAddAndroidKeyboardButton() {
                                if (!inputControlsView.addElement(ControlElement.Type.BUTTON)) {
                                    AppUtils.showToast(activity, R.string.no_profile_selected);
                                    return;
                                }
                                ControlElement element = inputControlsView.getSelectedElement();
                                element.setBindingAt(0, Binding.SHOW_ANDROID_KEYBOARD);
                                element.setText(activity.getString(R.string.keyboard));
                                profile.save();
                                inputControlsView.invalidate();
                                closeDialog();
                                showSettings(element);
                            }

                            @Override
                            public void onPickBackgroundFile() {}

                            @Override
                            public void onPickBackgroundSystem() {}

                            @Override
                            public void onClearBackground() {}

                            @Override
                            public void onBackgroundOpacityChange(float opacity) {}

                            @Override
                            public void onGroupVisibilityChange(String groupName, boolean visible) {
                                if (groupName == null) return;
                                profile.setGroupVisible(groupName, visible);
                                profile.save();
                                inputControlsView.invalidate();
                                refreshSidebar();
                                renderDialogHost();
                            }

                            @Override
                            public void onPickIconFile() {
                                closeDialog();
                                onPickCustomIcon.run();
                            }

                            @Override
                            public void onPickIconSystem() {
                                closeDialog();
                                onPickCustomIcon.run();
                            }

                            @Override
                            public void onIconStyleChange(String key) {
                                ControlsIconStyle style = ControlsIconStyle.fromKey(key);
                                inputControlsView.setIconStyle(style);
                                PreferenceManager.getDefaultSharedPreferences(activity)
                                        .edit().putString(ControlsIconStyle.PREF_KEY, style.key()).apply();
                                XServerDrawerState.INSTANCE.setControlsIconStyle(style.key()); // keep the drawer chips in sync
                                renderDialogHost();
                            }
                        },
                        composer,
                        0);
                return Unit.INSTANCE;
            }
        });
    }
}
