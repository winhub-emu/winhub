package com.winhub.hernandez;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.graphics.Rect;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.runtime.Composer;
import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.winhub.hernandez.R;
import com.winhub.hernandez.inputcontrols.Binding;
import com.winhub.hernandez.inputcontrols.ControlElement;
import com.winhub.hernandez.inputcontrols.ControlsIconStyle;
import androidx.preference.PreferenceManager;
import com.winhub.hernandez.inputcontrols.ControlsProfile;
import com.winhub.hernandez.inputcontrols.InputControlsManager;
import com.winhub.hernandez.inputcontrols.CustomIconManager;
import com.winhub.hernandez.core.AppUtils;
import com.winhub.hernandez.widget.InputControlsView;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;

public class ControlsEditorActivity extends AppCompatActivity {
    private InputControlsView inputControlsView;
    private ControlsProfile profile;
    private CustomIconManager customIconManager;
    private EditorReferenceState editorReferenceState;
    private ActivityResultLauncher<String> iconPickerLauncher;
    private ActivityResultLauncher<Intent> iconPickerInAppLauncher;

    // Background image picker
    private ActivityResultLauncher<String> bgImagePickerLauncher;
    private ActivityResultLauncher<Intent> bgImagePickerInAppLauncher;

    private View sidebarOverlay;
    private View sidebarScrollView;
    private LinearLayout sidebarContent;
    private ComposeView sidebarComposeView;
    private ComposeView dialogComposeView;
    private String activeDialogMode;
    private boolean sidebarOpen = false;
    private boolean sidebarOnRight = false;
    private int sidebarSettingsReloadKey = 0;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        AppUtils.hideSystemUI(this);
        setContentView(R.layout.controls_editor_activity);

        customIconManager = new CustomIconManager(this);
        editorReferenceState = new ViewModelProvider(this).get(EditorReferenceState.class);
        inputControlsView = new InputControlsView(this);
        inputControlsView.setEditMode(true);
        inputControlsView.setOverlayOpacity(0.6f);
        inputControlsView.setBackgroundOpacity(editorReferenceState.getOpacity());
        Bitmap restoredBackground = editorReferenceState.createBitmapCopy();
        if (restoredBackground != null) inputControlsView.setBackgroundImage(restoredBackground);

        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, getIntent().getIntExtra("profile_id", 0)));
        inputControlsView.setProfile(profile);

        ComposeView composeToolbar = findViewById(R.id.ComposeToolbar);
        if (composeToolbar != null) {
            composeToolbar.setContent(new Function2<Composer, Integer, Unit>() {
                @Override
                public Unit invoke(Composer composer, Integer changed) {
                    ControlsEditorToolbarKt.ControlsEditorToolbar(
                        profile.getName(),
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                showAddElementTypeDialog();
                                return Unit.INSTANCE;
                            }
                        },
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                removeElement();
                                return Unit.INSTANCE;
                            }
                        },
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                ControlElement selectedElement = inputControlsView.getSelectedElement();
                                if (selectedElement != null) showControlElementSettingsFor(selectedElement);
                                else AppUtils.showToast(ControlsEditorActivity.this, R.string.no_control_element_selected);
                                return Unit.INSTANCE;
                            }
                        },
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                toggleGroupListDialog();
                                return Unit.INSTANCE;
                            }
                        },
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                showComposeDialog(ControlsEditorDialogsKt.DIALOG_ICON_STYLE);
                                return Unit.INSTANCE;
                            }
                        },
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                showBackgroundImageDialog();
                                return Unit.INSTANCE;
                            }
                        },
                        composer,
                        0
                    );
                    return Unit.INSTANCE;
                }
            });
        }
        dialogComposeView = findViewById(R.id.ComposeDialogHost);
        renderDialogHost();

        FrameLayout container = findViewById(R.id.FLContainer);
        container.addView(inputControlsView, 0);

        sidebarOverlay = findViewById(R.id.VSidebarOverlay);
        sidebarScrollView = findViewById(R.id.SVSidebar);
        sidebarContent = findViewById(R.id.LLSidebarContent);
        updateSidebarWidth(getResources().getDisplayMetrics().widthPixels);
        container.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> updateSidebarWidth(right - left));
        if (sidebarOverlay != null) {
            sidebarOverlay.setClickable(false);
            sidebarOverlay.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    ControlElement hitElement = findElementAtScreen(event.getRawX(), event.getRawY());
                    if (hitElement != null) {
                        showControlElementSettingsFor(hitElement);
                        return true;
                    }
                    closeSidebar();
                    return true;
                }
                return false;
            });
        }

        // Custom-icon pickers: the built-in file picker (primary) and the system SAF picker (secondary).
        iconPickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) addCustomIconFromUri(uri);
        });
        iconPickerInAppLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String path = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_FILE);
                if (path != null) addCustomIconFromUri(Uri.fromFile(new java.io.File(path)));
            }
        });

        // Background image pickers
        bgImagePickerLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) setBackgroundImageFromUri(uri);
        });
        bgImagePickerInAppLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String path = result.getData().getStringExtra(FilePickerActivity.EXTRA_SELECTED_FILE);
                if (path != null) setBackgroundImageFromUri(Uri.fromFile(new java.io.File(path)));
            }
        });
    }

    static int calculateSidebarWidth(int availableWidth, float density) {
        return Math.min(Math.round(300 * density), Math.round(availableWidth * 0.85f));
    }

    private void updateSidebarWidth(int availableWidth) {
        if (sidebarScrollView == null || availableWidth <= 0) return;
        ViewGroup.LayoutParams params = sidebarScrollView.getLayoutParams();
        int width = calculateSidebarWidth(availableWidth, getResources().getDisplayMetrics().density);
        if (params.width != width) {
            params.width = width;
            sidebarScrollView.setLayoutParams(params);
        }
    }

    private void setBackgroundImageFromUri(Uri uri) {
        try {
            Bitmap bitmap = decodeSampledBitmap(uri);
            if (bitmap != null) {
                if (editorReferenceState.replaceBitmapWithCopy(bitmap)) {
                    inputControlsView.setBackgroundImage(bitmap);
                    AppUtils.showToast(this, R.string.background_image_set);
                } else {
                    bitmap.recycle();
                    AppUtils.showToast(this, R.string.unable_to_load_image);
                }
            } else {
                AppUtils.showToast(this, R.string.unable_to_load_image);
            }
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            AppUtils.showToast(this, R.string.unable_to_load_image);
        }
    }

    private Bitmap decodeSampledBitmap(Uri uri) throws IOException {
        int targetWidth = inputControlsView.getWidth();
        int targetHeight = inputControlsView.getHeight();
        if (targetWidth <= 0 || targetHeight <= 0) {
            targetWidth = getResources().getDisplayMetrics().widthPixels;
            targetHeight = getResources().getDisplayMetrics().heightPixels;
        }

        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            if (stream == null) return null;
            BitmapFactory.decodeStream(stream, null, bounds);
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= targetWidth
                || bounds.outHeight / (sampleSize * 2) >= targetHeight) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sampleSize;
        try (InputStream stream = getContentResolver().openInputStream(uri)) {
            return stream != null ? BitmapFactory.decodeStream(stream, null, options) : null;
        }
    }

    private void showAddElementTypeDialog() {
        showComposeDialog(ControlsEditorDialogsKt.DIALOG_ADD_ELEMENT);
    }

    private void showBackgroundImageDialog() {
        showComposeDialog(ControlsEditorDialogsKt.DIALOG_BACKGROUND_IMAGE);
    }

    private void toggleGroupListDialog() {
        if (ControlsEditorDialogsKt.DIALOG_GROUP_VISIBILITY.equals(activeDialogMode)) closeComposeDialog();
        else showComposeDialog(ControlsEditorDialogsKt.DIALOG_GROUP_VISIBILITY);
    }

    private void addElement(ControlElement.Type type) {
        if (inputControlsView.addElement(type)) {
            ControlElement selectedElement = inputControlsView.getSelectedElement();
            closeComposeDialog();
            if (selectedElement != null) showControlElementSettingsFor(selectedElement);
        } else {
            AppUtils.showToast(this, R.string.no_profile_selected);
        }
    }

    private void addAndroidKeyboardButton() {
        if (!inputControlsView.addElement(ControlElement.Type.BUTTON)) {
            AppUtils.showToast(this, R.string.no_profile_selected);
            return;
        }
        ControlElement element = inputControlsView.getSelectedElement();
        element.setBindingAt(0, Binding.SHOW_ANDROID_KEYBOARD);
        element.setText(getString(R.string.keyboard));
        profile.save();
        inputControlsView.invalidate();
        closeComposeDialog();
        showControlElementSettingsFor(element);
    }

    // Shared: add a custom icon from any Uri (file:// from the in-app picker, content:// from SAF).
    private void addCustomIconFromUri(Uri uri) {
        ControlElement selectedElement = inputControlsView.getSelectedElement();
        if (selectedElement == null) return;

        short iconId = customIconManager.addCustomIcon(uri);
        if (iconId >= 0) {
            selectedElement.setIconId(iconId);
            profile.save();
            inputControlsView.invalidate();
        } else {
            AppUtils.showToast(this, R.string.unable_to_load_image);
        }
        refreshSidebarSettings();
    }

    // Two-option chooser: built-in picker first, then system SAF.
    public void promptPickCustomIcon() {
        showComposeDialog(ControlsEditorDialogsKt.DIALOG_CUSTOM_ICON_SOURCE);
    }

    private void showComposeDialog(String dialogMode) {
        activeDialogMode = dialogMode;
        renderDialogHost();
    }

    private void closeComposeDialog() {
        activeDialogMode = null;
        renderDialogHost();
    }

    private void renderDialogHost() {
        if (dialogComposeView == null) return;
        dialogComposeView.setVisibility(activeDialogMode == null ? View.GONE : View.VISIBLE);
        final String dialogMode = activeDialogMode;
        final float backgroundOpacity = inputControlsView != null ? inputControlsView.getBackgroundOpacity() : 0f;
        final String iconStyle = (inputControlsView != null ? inputControlsView.getIconStyle() : ControlsIconStyle.FUSION).key();

        dialogComposeView.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                ControlsEditorDialogsKt.ControlsEditorDialogHost(
                    dialogMode,
                    profile,
                    backgroundOpacity,
                    iconStyle,
                    new ControlsEditorDialogActions() {
                        @Override
                        public void onDismiss() {
                            closeComposeDialog();
                        }
                        @Override
                        public void onAddElement(ControlElement.Type type) {
                            addElement(type);
                        }
                        @Override
                        public void onAddAndroidKeyboardButton() {
                            addAndroidKeyboardButton();
                        }
                        @Override
                        public void onPickBackgroundFile() {
                            pickBackgroundFromFiles();
                        }
                        @Override
                        public void onPickBackgroundSystem() {
                            pickBackgroundViaSystem();
                        }
                        @Override
                        public void onClearBackground() {
                            clearBackgroundImage();
                        }
                        @Override
                        public void onBackgroundOpacityChange(float opacity) {
                            editorReferenceState.setOpacity(opacity);
                            if (inputControlsView != null) inputControlsView.setBackgroundOpacity(opacity);
                        }
                        @Override
                        public void onGroupVisibilityChange(String groupName, boolean visible) {
                            setGroupVisibility(groupName, visible);
                        }
                        @Override
                        public void onPickIconFile() {
                            pickCustomIconFromFiles();
                        }
                        @Override
                        public void onPickIconSystem() {
                            pickCustomIconViaSystem();
                        }
                        @Override
                        public void onIconStyleChange(String key) {
                            ControlsIconStyle style = ControlsIconStyle.fromKey(key);
                            inputControlsView.setIconStyle(style);
                            PreferenceManager.getDefaultSharedPreferences(ControlsEditorActivity.this)
                                    .edit().putString(ControlsIconStyle.PREF_KEY, style.key()).apply();
                            renderDialogHost();
                        }
                    },
                    composer,
                    0
                );
                return Unit.INSTANCE;
            }
        });
    }

    private void pickBackgroundFromFiles() {
        closeComposeDialog();
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_EXTENSIONS, new String[]{"png", "jpg", "jpeg", "webp", "bmp"});
        intent.putExtra(FilePickerActivity.EXTRA_PICKER_TITLE, getString(R.string.select_background_image));
        bgImagePickerInAppLauncher.launch(intent);
    }

    private void pickBackgroundViaSystem() {
        closeComposeDialog();
        bgImagePickerLauncher.launch("image/*");
    }

    private void clearBackgroundImage() {
        closeComposeDialog();
        editorReferenceState.clearBitmap();
        inputControlsView.setBackgroundImage(null);
        AppUtils.showToast(this, R.string.background_cleared);
    }

    private void pickCustomIconFromFiles() {
        closeComposeDialog();
        Intent intent = new Intent(this, FilePickerActivity.class);
        intent.putExtra(FilePickerActivity.EXTRA_EXTENSIONS, new String[]{"png", "jpg", "jpeg", "webp", "bmp", "gif", "svg"});
        intent.putExtra(FilePickerActivity.EXTRA_PICKER_TITLE, getString(R.string.select_icon_image));
        iconPickerInAppLauncher.launch(intent);
    }

    private void pickCustomIconViaSystem() {
        closeComposeDialog();
        iconPickerLauncher.launch("image/*");
    }

    private void setGroupVisibility(String groupName, boolean visible) {
        if (profile == null || groupName == null) return;
        profile.setGroupVisible(groupName, visible);
        profile.save();
        inputControlsView.invalidate();
        refreshSidebarSettings();
        renderDialogHost();
    }

    private void removeElement() {
        if (inputControlsView.removeElement()) {
            closeSidebar();
        } else {
            AppUtils.showToast(this, R.string.no_control_element_selected);
        }
    }

    public void showControlElementSettingsFor(ControlElement element) {
        if (element == null || sidebarContent == null || sidebarScrollView == null || sidebarOverlay == null || inputControlsView == null) return;
        inputControlsView.selectElementAt(element);
        if (sidebarOpen) saveSidebarState();

        sidebarComposeView = ensureSidebarComposeView();
        bindSidebarSettings(element);

        final float sidebarWidthPx = sidebarScrollView.getLayoutParams().width;
        final float screenWidth = inputControlsView.getWidth() > 0
                ? inputControlsView.getWidth()
                : getResources().getDisplayMetrics().widthPixels;
        Rect elementBounds = element.getBoundingBox();
        final float centerX = elementBounds != null && !elementBounds.isEmpty() ? elementBounds.centerX() : element.getX();
        sidebarOnRight = centerX <= screenWidth / 2f;

        boolean animateIn = !sidebarOpen || sidebarScrollView == null || sidebarScrollView.getVisibility() != View.VISIBLE;

        if (sidebarScrollView != null) {
            if (sidebarScrollView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sidebarScrollView.getLayoutParams();
                lp.gravity = sidebarOnRight ? (Gravity.END | Gravity.TOP) : (Gravity.START | Gravity.TOP);
                sidebarScrollView.setLayoutParams(lp);
            }
            sidebarScrollView.animate().cancel();
            sidebarScrollView.setVisibility(View.VISIBLE);
            sidebarScrollView.setAlpha(1f);
            if (animateIn) {
                sidebarScrollView.setTranslationX(sidebarOnRight ? sidebarWidthPx : -sidebarWidthPx);
                sidebarScrollView.animate()
                    .translationX(0f)
                    .setDuration(250)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            } else {
                sidebarScrollView.setTranslationX(0f);
            }
        }

        if (sidebarOverlay != null) {
            sidebarOverlay.animate().cancel();
            sidebarOverlay.setVisibility(View.VISIBLE);
            if (animateIn) {
                sidebarOverlay.setAlpha(0f);
                sidebarOverlay.animate().alpha(1f).setDuration(200).start();
            } else {
                sidebarOverlay.setAlpha(1f);
            }
        }

        sidebarOpen = true;
    }

    private ComposeView ensureSidebarComposeView() {
        if (sidebarComposeView == null) {
            sidebarComposeView = new ComposeView(this);
        }
        if (sidebarComposeView.getParent() == null && sidebarContent != null) {
            sidebarContent.addView(sidebarComposeView);
        }
        return sidebarComposeView;
    }

    private void bindSidebarSettings(final ControlElement element) {
        if (sidebarComposeView == null) return;
        final int reloadKey = sidebarSettingsReloadKey;
        sidebarComposeView.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                ControlsEditorSettingsPaneKt.ControlsEditorSettingsPane(
                    element,
                    profile,
                    new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            inputControlsView.invalidate();
                            return Unit.INSTANCE;
                        }
                    },
                    customIconManager,
                    reloadKey,
                    ControlsEditorActivity.this,
                    new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            closeSidebar();
                            return Unit.INSTANCE;
                        }
                    },
                    new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            promptPickCustomIcon();
                            return Unit.INSTANCE;
                        }
                    },
                    new Function1<Integer, Boolean>() {
                        @Override
                        public Boolean invoke(Integer iconId) {
                            if (iconId == null || !customIconManager.deleteIconIfUnused(iconId)) return false;
                            inputControlsView.evictCustomIcon(iconId);
                            refreshSidebarSettings();
                            return true;
                        }
                    },
                    composer,
                    0
                );
                return Unit.INSTANCE;
            }
        });
    }

    private void refreshSidebarSettings() {
        if (sidebarScrollView == null || sidebarScrollView.getVisibility() != View.VISIBLE) return;
        ControlElement selectedElement = inputControlsView.getSelectedElement();
        if (selectedElement == null) return;
        sidebarSettingsReloadKey++;
        ensureSidebarComposeView();
        bindSidebarSettings(selectedElement);
    }
    private void saveSidebarState() {
        if (profile != null) profile.save();
    }

    /** Find a control element at the given raw screen coordinates (for overlay passthrough). */
    private ControlElement findElementAtScreen(float rawX, float rawY) {
        if (inputControlsView == null || profile == null || !profile.isElementsLoaded()) return null;
        // Convert raw screen coordinates to local coordinates relative to the InputControlsView
        int[] loc = new int[2];
        inputControlsView.getLocationOnScreen(loc);
        float localX = rawX - loc[0];
        float localY = rawY - loc[1];
        List<ControlElement> elements = profile.getElements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            ControlElement element = elements.get(i);
            if (!isElementVisible(element)) continue;
            if (element.containsPoint(localX, localY)) return element;
        }
        return null;
    }

    private boolean isElementVisible(ControlElement element) {
        return element != null && (!element.isInGroup() || profile == null || profile.isGroupVisible(element.getGroupId()));
    }

    public void closeSidebar() {
        if (sidebarScrollView == null || sidebarOverlay == null) return;
        if (sidebarScrollView.getVisibility() != View.VISIBLE) return;

        saveSidebarState();
        sidebarOpen = false;

        final float sidebarWidthPx = sidebarScrollView.getLayoutParams().width;
        sidebarOverlay.animate().cancel();
        sidebarScrollView.animate().cancel();

        sidebarOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .start();

        sidebarScrollView.animate()
            .translationX(sidebarOnRight ? sidebarWidthPx : -sidebarWidthPx)
            .setDuration(250)
            .setInterpolator(new DecelerateInterpolator())
            .setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    sidebarScrollView.setVisibility(View.GONE);
                    sidebarOverlay.setVisibility(View.GONE);
                    sidebarScrollView.setAlpha(1f);
                    sidebarOverlay.setAlpha(1f);
                    sidebarScrollView.animate().setListener(null);
                }
            })
            .start();
    }

    @Override
    public void onBackPressed() {
        if (activeDialogMode != null) {
            closeComposeDialog();
            return;
        }
        if (sidebarScrollView != null && sidebarScrollView.getVisibility() == View.VISIBLE) {
            closeSidebar();
            return;
        }
        super.onBackPressed();
        overridePendingTransition(R.anim.slide_in_down, R.anim.slide_out_up);
    }

    @Override
    protected void onStop() {
        if (profile != null) profile.save();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        activeDialogMode = null;
        if (inputControlsView != null) inputControlsView.setBackgroundImage(null);
        super.onDestroy();
    }

    public static final class EditorReferenceState extends ViewModel {
        private Bitmap bitmap;
        private float opacity = 0.65f;

        public float getOpacity() {
            return opacity;
        }

        public void setOpacity(float opacity) {
            this.opacity = Math.max(0f, Math.min(1f, opacity));
        }

        public boolean replaceBitmapWithCopy(Bitmap source) {
            Bitmap copy = copyBitmap(source);
            if (copy == null) return false;
            clearBitmap();
            bitmap = copy;
            return true;
        }

        public Bitmap createBitmapCopy() {
            return copyBitmap(bitmap);
        }

        public void clearBitmap() {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            bitmap = null;
        }

        private static Bitmap copyBitmap(Bitmap source) {
            if (source == null || source.isRecycled()) return null;
            Bitmap.Config config = source.getConfig() != null ? source.getConfig() : Bitmap.Config.ARGB_8888;
            try {
                return source.copy(config, false);
            }
            catch (OutOfMemoryError e) {
                return null;
            }
        }

        @Override
        protected void onCleared() {
            clearBitmap();
        }
    }
}
