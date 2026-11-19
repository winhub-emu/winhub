package com.winhub.hernandez.inputcontrols;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.winhub.hernandez.core.CubicBezierInterpolator;
import com.winhub.hernandez.math.Mathf;
import com.winhub.hernandez.widget.InputControlsView;
import com.winhub.hernandez.widget.TouchpadView;
import com.winhub.hernandez.winhandler.MouseEventFlags;
import com.winhub.hernandez.xserver.XServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;

public class ControlElement {
    public static final float STICK_DEAD_ZONE = 0.15f;
    public static final float STICK_SENSITIVITY = 2.0f;
    public static final float TRACKPAD_MIN_SPEED = 0.8f;
    public static final float TRACKPAD_MAX_SPEED = 20.0f;
    public static final byte TRACKPAD_ACCELERATION_THRESHOLD = 4;
    public static final short BUTTON_MIN_TIME_TO_KEEP_PRESSED = 300;
    private static final int DEFAULT_GRID_ROWS = 2;
    private static final int DEFAULT_GRID_COLS = 8;
    private static final int MIN_AREA_SIZE = 200;
    private static final int MAX_AREA_SIZE = 2000;
    private static final int MIN_STICK_RADIUS = 60;
    private static final int MAX_STICK_RADIUS = 400;
    private static final int MAX_GRID_ROWS = 8;
    private static final int MAX_GRID_COLS = 16;
    private static final int MAX_BINDING_COUNT = MAX_GRID_ROWS * MAX_GRID_COLS;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 3.0f;
    private static final long GRID_FLASH_DURATION_MS = 150;
    public static final int MAX_EXPANDABLE_CHILDREN = 10;
    public static final int MAX_COMBO_BINDINGS = 10;
    public enum Type {
        BUTTON, D_PAD, RANGE_BUTTON, STICK, TRACKPAD, DYNAMIC_STICK, MOUSE_AREA, BUTTON_GRID, EXPANDABLE_BUTTON;

        public static String[] names() {
            Type[] types = values();
            String[] names = new String[types.length];
            for (int i = 0; i < types.length; i++) names[i] = types[i].name().replace("_", "-");
            return names;
        }
    }
    public enum Shape {
        CIRCLE, RECT, ROUND_RECT, SQUARE;

        public static String[] names() {
            Shape[] shapes = values();
            String[] names = new String[shapes.length];
            for (int i = 0; i < shapes.length; i++) names[i] = shapes[i].name().replace("_", " ");
            return names;
        }
    }
    public enum Range {
        FROM_A_TO_Z(26), FROM_0_TO_9(10), FROM_F1_TO_F12(12), FROM_NP0_TO_NP9(10);
        public final byte max;

        Range(int max) {
            this.max = (byte)max;
        }

        public static String[] names() {
            Range[] ranges = values();
            String[] names = new String[ranges.length];
            for (int i = 0; i < ranges.length; i++) names[i] = ranges[i].name().replace("_", " ");
            return names;
        }
    }
    public enum ExpandableLayout { RADIAL, LIST }
    public enum ExpandableDirection { UP, RIGHT, DOWN, LEFT }

    private final InputControlsView inputControlsView;
    private Type type = Type.BUTTON;
    private Shape shape = Shape.CIRCLE;
    private Binding[] bindings = {Binding.NONE, Binding.NONE, Binding.NONE, Binding.NONE};
    private float scale = 1.0f;
    private short x;
    private short y;
    private boolean selected = false;
    private boolean toggleSwitch = false;
    private boolean swipeable = true;
    private int currentPointerId = -1;
    private final Rect boundingBox = new Rect();
    private boolean[] states = new boolean[4];
    private boolean[] activeBindingSlots = new boolean[4];
    private boolean[] blockTouchscreenMouseButtons = {true, true, true, true};
    private boolean holdKeyActive;
    private final Path path = new Path();
    private final Rect iconSourceRect = new Rect();
    private final Rect iconDestinationRect = new Rect();
    private final RectF iconAspectFitDestinationRect = new RectF();
    private PorterDuffColorFilter iconColorFilter;
    private int iconColorFilterColor;
    private boolean boundingBoxNeedsUpdate = true;
    private String text = "";
    private short iconId;
    private boolean customIconTintEnabled = true;
    private boolean customIconAsButton;
    private Range range;
    private byte orientation;
    private PointF currentPosition;
    private RangeScroller scroller;
    private CubicBezierInterpolator interpolator;
    private Object touchTime;

    // --- New fields for DYNAMIC_STICK, MOUSE_AREA, BUTTON_GRID ---
    private int areaWidth;            // detection area width in current-view pixels
    private int areaHeight;           // detection area height in current-view pixels
    private int stickRadius;          // visual stick radius (for DYNAMIC_STICK)
    private boolean stickVisible;     // current visibility (for DYNAMIC_STICK)
    private float visualStickX;       // smoothed visual stick center X (for animation)
    private float visualStickY;       // smoothed visual stick center Y (for animation)
    private float lastFingerX;        // latest finger X (for thumb position in draw)
    private float lastFingerY;        // latest finger Y (for thumb position in draw)
    private float mouseSensitivity;   // cursor speed multiplier (for TRACKPAD/MOUSE_AREA), default 1.0
    private int gridRows;             // rows in button grid (for BUTTON_GRID)
    private int gridCols;             // columns in button grid (for BUTTON_GRID)
    private Shape gridCellShape;      // shape for each grid cell (default ROUND_RECT)
    private float gridSpacing;        // spacing between grid cells in snapping-size units
    private boolean gridMultitouchEnabled;
    private final ButtonGridTouchState buttonGridTouchState = new ButtonGridTouchState(MAX_BINDING_COUNT);
    private PointF mouseAreaLastPos;  // last touch position in MOUSE_AREA
    private Binding[][] comboBindings; // multi-key combos per binding slot (null = single key)
    private String[][] rawComboBindingNames;
    private long[] cellPressTimes;     // per-cell press timestamps for flash animation
    private Binding holdKey;           // key held while touch is active (TRACKPAD/MOUSE_AREA/STICK/DYNAMIC_STICK), default NONE
    private float deadZone = 0.15f;
    private boolean customAreaAppearanceEnabled;
    private int customAreaColor = 0xFF0055FF;
    private float customAreaOpacity = 0.25f;
    private String groupId = null;
    private JSONObject sourceJSONObject;
    private boolean holdKeyEdited;
    private ExpandableLayout expandableLayout = ExpandableLayout.RADIAL;
    private ExpandableDirection expandableDirection = ExpandableDirection.UP;
    private boolean expanded;
    private int activeExpandableChild = -1;
    private float expandedOffsetX;
    private float expandedOffsetY;
    private int expandedItemsPerLane;
    private float expandedChildScale = 1.0f;
    private final Rect expandedBoundingBox = new Rect();

    public ControlElement(InputControlsView inputControlsView) {
        this.inputControlsView = inputControlsView;
    }

    public void setSourceJSONObject(JSONObject sourceJSONObject) {
        holdKeyEdited = false;
        if (sourceJSONObject == null) {
            this.sourceJSONObject = null;
            return;
        }
        try {
            this.sourceJSONObject = new JSONObject(sourceJSONObject.toString());
        }
        catch (JSONException e) {
            this.sourceJSONObject = null;
        }
    }

    private void reset() {
        int bindingCount = type == Type.BUTTON_GRID ? DEFAULT_GRID_ROWS * DEFAULT_GRID_COLS : 4;
        resetBindingArrays(bindingCount);
        scroller = null;
        areaWidth = 0;
        areaHeight = 0;
        stickRadius = 0;
        stickVisible = false;
        visualStickX = 0;
        visualStickY = 0;
        lastFingerX = 0;
        lastFingerY = 0;
        mouseSensitivity = 1.0f;
        gridRows = 0;
        gridCols = 0;
        gridCellShape = Shape.ROUND_RECT;
        gridSpacing = 0;
        gridMultitouchEnabled = false;
        buttonGridTouchState.clear();
        mouseAreaLastPos = null;
        holdKey = Binding.NONE;
        holdKeyActive = false;
        toggleSwitch = false;
        deadZone = 0.15f;
        customAreaAppearanceEnabled = false;
        customAreaColor = 0xFF0055FF;
        customAreaOpacity = 0.25f;
        orientation = 0;
        currentPointerId = -1;
        currentPosition = null;
        touchTime = null;
        expandableLayout = ExpandableLayout.RADIAL;
        expandableDirection = ExpandableDirection.UP;
        expanded = false;
        activeExpandableChild = -1;

        if (type == Type.STICK || type == Type.DYNAMIC_STICK) {
            bindings[0] = Binding.KEY_W;
            bindings[1] = Binding.KEY_D;
            bindings[2] = Binding.KEY_S;
            bindings[3] = Binding.KEY_A;
        }
        else if(type == Type.D_PAD){
            bindings[0] = Binding.GAMEPAD_DPAD_UP;
            bindings[1] = Binding.GAMEPAD_DPAD_RIGHT;
            bindings[2] = Binding.GAMEPAD_DPAD_DOWN;
            bindings[3] = Binding.GAMEPAD_DPAD_LEFT;
        }
        else if (type == Type.TRACKPAD) {
            bindings[0] = Binding.GAMEPAD_RIGHT_THUMB_UP;
            bindings[1] = Binding.GAMEPAD_RIGHT_THUMB_RIGHT;
            bindings[2] = Binding.GAMEPAD_RIGHT_THUMB_DOWN;
            bindings[3] = Binding.GAMEPAD_RIGHT_THUMB_LEFT;
        }
        else if (type == Type.RANGE_BUTTON) {
            scroller = new RangeScroller(inputControlsView, this);
        }
        if (type == Type.DYNAMIC_STICK) {
            areaWidth = 600;
            areaHeight = 600;
            stickRadius = 120;
            stickVisible = false;
        }
        else if (type == Type.MOUSE_AREA) {
            areaWidth = 800;
            areaHeight = 400;
            mouseSensitivity = 1.0f;
        }
        else if (type == Type.BUTTON_GRID) {
            gridRows = DEFAULT_GRID_ROWS;
            gridCols = DEFAULT_GRID_COLS;
            // Default: map to keyboard keys A-P for 8x2
            Binding[] allBindings = Binding.values();
            int startIdx = Binding.KEY_A.ordinal();
            for (int i = 0; i < bindings.length && (startIdx + i) < allBindings.length; i++) {
                bindings[i] = allBindings[startIdx + i];
            }
        }

        text = "";
        iconId = 0;
        customIconTintEnabled = true;
        customIconAsButton = false;
        range = null;
        boundingBoxNeedsUpdate = true;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
        sourceJSONObject = null;
        reset();
        notifyBindingsChanged();
    }

    public int getBindingCount() {
        return bindings.length;
    }

    public void setBindingCount(int bindingCount) {
        resizeBindingArrays(bindingCount, true);
        notifyBindingsChanged();
    }

    public int getExpandableChildCount() {
        return type == Type.EXPANDABLE_BUTTON ? bindings.length : 0;
    }

    public void setExpandableChildCount(int childCount) {
        if (type == Type.EXPANDABLE_BUTTON) {
            resizeBindingArrays(clampExpandableChildCount(childCount), true);
            notifyBindingsChanged();
        }
    }

    static int clampExpandableChildCount(int childCount) {
        return Math.max(1, Math.min(MAX_EXPANDABLE_CHILDREN, childCount));
    }

    static int calculateExpandableItemsPerLane(float available, float itemSize, float gap, int itemCount) {
        if (itemCount <= 0) return 1;
        int fit = (int)Math.floor((Math.max(0, available) + gap) / Math.max(1, itemSize + gap));
        return Math.max(1, Math.min(itemCount, fit));
    }

    public ExpandableLayout getExpandableLayout() { return expandableLayout; }
    public void setExpandableLayout(ExpandableLayout layout) {
        expandableLayout = layout != null ? layout : ExpandableLayout.RADIAL;
    }
    public ExpandableDirection getExpandableDirection() { return expandableDirection; }
    public void setExpandableDirection(ExpandableDirection direction) {
        expandableDirection = direction != null ? direction : ExpandableDirection.UP;
    }
    public boolean isExpanded() { return expanded; }
    public int getActiveExpandablePointerId() { return activeExpandableChild >= 0 ? currentPointerId : -1; }

    private void resetBindingArrays(int bindingCount) {
        resizeBindingArrays(bindingCount, false);
    }

    private void resizeBindingArrays(int bindingCount, boolean preserveExisting) {
        int safeBindingCount = Math.min(MAX_BINDING_COUNT, Math.max(1, bindingCount));
        if (preserveExisting && bindings != null) {
            Binding[] oldBindings = bindings;
            boolean[] oldStates = states;
            boolean[] oldActiveBindingSlots = activeBindingSlots;
            boolean[] oldBlockTouchscreenMouseButtons = blockTouchscreenMouseButtons;
            Binding[][] oldComboBindings = comboBindings;
            String[][] oldRawComboBindingNames = rawComboBindingNames;
            long[] oldCellPressTimes = cellPressTimes;

            bindings = Arrays.copyOf(oldBindings, safeBindingCount);
            if (oldBindings.length < safeBindingCount) {
                Arrays.fill(bindings, oldBindings.length, safeBindingCount, Binding.NONE);
            }
            states = oldStates != null ? Arrays.copyOf(oldStates, safeBindingCount) : new boolean[safeBindingCount];
            activeBindingSlots = oldActiveBindingSlots != null
                    ? Arrays.copyOf(oldActiveBindingSlots, safeBindingCount)
                    : new boolean[safeBindingCount];
            blockTouchscreenMouseButtons = oldBlockTouchscreenMouseButtons != null
                    ? Arrays.copyOf(oldBlockTouchscreenMouseButtons, safeBindingCount)
                    : new boolean[safeBindingCount];
            if (oldBlockTouchscreenMouseButtons == null) {
                Arrays.fill(blockTouchscreenMouseButtons, true);
            }
            else if (oldBlockTouchscreenMouseButtons.length < safeBindingCount) {
                Arrays.fill(blockTouchscreenMouseButtons, oldBlockTouchscreenMouseButtons.length,
                        safeBindingCount, true);
            }
            comboBindings = oldComboBindings != null ? Arrays.copyOf(oldComboBindings, safeBindingCount) : null;
            rawComboBindingNames = oldRawComboBindingNames != null
                    ? Arrays.copyOf(oldRawComboBindingNames, safeBindingCount)
                    : null;
            cellPressTimes = oldCellPressTimes != null ? Arrays.copyOf(oldCellPressTimes, safeBindingCount) : null;
        } else {
            bindings = new Binding[safeBindingCount];
            Arrays.fill(bindings, Binding.NONE);
            states = new boolean[safeBindingCount];
            activeBindingSlots = new boolean[safeBindingCount];
            blockTouchscreenMouseButtons = new boolean[safeBindingCount];
            Arrays.fill(blockTouchscreenMouseButtons, true);
            comboBindings = null;
            rawComboBindingNames = null;
            cellPressTimes = null;
        }
        boundingBoxNeedsUpdate = true;
    }

    private void ensureBindingCapacity(int bindingCount) {
        int safeBindingCount = Math.min(MAX_BINDING_COUNT, Math.max(1, bindingCount));
        if (safeBindingCount <= bindings.length) return;
        int oldLength = bindings.length;
        bindings = Arrays.copyOf(bindings, safeBindingCount);
        Arrays.fill(bindings, oldLength, bindings.length, Binding.NONE);
        states = Arrays.copyOf(states, safeBindingCount);
        activeBindingSlots = Arrays.copyOf(activeBindingSlots, safeBindingCount);
        int oldPriorityLength = blockTouchscreenMouseButtons.length;
        blockTouchscreenMouseButtons = Arrays.copyOf(blockTouchscreenMouseButtons, safeBindingCount);
        Arrays.fill(blockTouchscreenMouseButtons, oldPriorityLength, safeBindingCount, true);
        if (comboBindings != null) comboBindings = Arrays.copyOf(comboBindings, safeBindingCount);
        if (rawComboBindingNames != null) rawComboBindingNames = Arrays.copyOf(rawComboBindingNames, safeBindingCount);
        if (cellPressTimes != null) cellPressTimes = Arrays.copyOf(cellPressTimes, safeBindingCount);
        boundingBoxNeedsUpdate = true;
    }

    private boolean isValidBindingIndex(int index) {
        return index >= 0 && index < bindings.length;
    }

    private int getEffectiveGridRows() {
        return gridRows > 0 ? gridRows : DEFAULT_GRID_ROWS;
    }

    private int getEffectiveGridCols() {
        return gridCols > 0 ? gridCols : DEFAULT_GRID_COLS;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
        boundingBoxNeedsUpdate = true;
    }

    public Range getRange() {
        return range != null ? range : Range.FROM_A_TO_Z;
    }

    public void setRange(Range range) {
        this.range = range;
    }

    public byte getOrientation() {
        return orientation;
    }

    public void setOrientation(byte orientation) {
        this.orientation = orientation;
        boundingBoxNeedsUpdate = true;
    }

    public int getAreaWidth() { return areaWidth; }
    public void setAreaWidth(int areaWidth) { this.areaWidth = clamp(areaWidth, MIN_AREA_SIZE, MAX_AREA_SIZE); boundingBoxNeedsUpdate = true; }
    public int getAreaHeight() { return areaHeight; }
    public void setAreaHeight(int areaHeight) { this.areaHeight = clamp(areaHeight, MIN_AREA_SIZE, MAX_AREA_SIZE); boundingBoxNeedsUpdate = true; }
    public int getStickRadius() { return stickRadius; }
    public void setStickRadius(int stickRadius) { this.stickRadius = clamp(stickRadius, MIN_STICK_RADIUS, MAX_STICK_RADIUS); }
    public float getMouseSensitivity() { return mouseSensitivity; }
    public void setMouseSensitivity(float s) { this.mouseSensitivity = clampFinite(s, 0.1f, 5.0f, 1.0f); }
    public int getGridRows() { return gridRows; }
    public void setGridRows(int gridRows) { this.gridRows = clamp(gridRows, 1, MAX_GRID_ROWS); boundingBoxNeedsUpdate = true; }
    public int getGridCols() { return gridCols; }
    public void setGridCols(int gridCols) { this.gridCols = clamp(gridCols, 1, MAX_GRID_COLS); boundingBoxNeedsUpdate = true; }
    public Shape getGridCellShape() { return gridCellShape != null ? gridCellShape : Shape.ROUND_RECT; }
    public void setGridCellShape(Shape s) { this.gridCellShape = s != null ? s : Shape.ROUND_RECT; boundingBoxNeedsUpdate = true; }
    public float getGridSpacing() { return gridSpacing; }
    public void setGridSpacing(float spacing) { gridSpacing = clampFinite(spacing, 0f, 1f, 0f); boundingBoxNeedsUpdate = true; }
    public boolean isGridMultitouchEnabled() { return gridMultitouchEnabled; }
    public void setGridMultitouchEnabled(boolean enabled) { gridMultitouchEnabled = enabled; }
    public Binding[] getCombo(int index) { return (comboBindings != null && index >= 0 && index < comboBindings.length) ? comboBindings[index] : null; }
    public boolean blocksTouchscreenMouseButtonsAt(int index) {
        return !isValidBindingIndex(index) || blockTouchscreenMouseButtons[index];
    }
    public void setBlocksTouchscreenMouseButtonsAt(int index, boolean blocked) {
        if (isValidBindingIndex(index)) blockTouchscreenMouseButtons[index] = blocked;
    }
    public void setCombo(int index, Binding[] combo) {
        if (!isValidBindingIndex(index)) return;
        if (rawComboBindingNames != null && index < rawComboBindingNames.length) rawComboBindingNames[index] = null;
        if (combo == null || combo.length == 0) {
            if (comboBindings != null && index < comboBindings.length) comboBindings[index] = null;
            notifyBindingsChanged();
            return;
        }
        if (comboBindings == null) comboBindings = new Binding[bindings.length][];
        else if (comboBindings.length != bindings.length) comboBindings = Arrays.copyOf(comboBindings, bindings.length);
        comboBindings[index] = sanitizeCombo(combo);
        notifyBindingsChanged();
    }

    void setLoadedCombo(int index, Binding[] combo, String[] rawNames) {
        if (!isValidBindingIndex(index)) return;
        if (comboBindings == null) comboBindings = new Binding[bindings.length][];
        String[] sanitizedRawNames = sanitizeRawComboNames(rawNames);
        if (sanitizedRawNames != null) {
            Binding[] loadedCombo = new Binding[sanitizedRawNames.length];
            for (int i = 0; i < sanitizedRawNames.length; i++) {
                loadedCombo[i] = Binding.fromString(sanitizedRawNames[i]);
            }
            comboBindings[index] = sanitizeCombo(loadedCombo);
        }
        else {
            comboBindings[index] = sanitizeCombo(combo);
        }
        if (rawComboBindingNames == null) rawComboBindingNames = new String[bindings.length][];
        rawComboBindingNames[index] = sanitizedRawNames;
    }
    public boolean hasCombo(int index) { return getCombo(index) != null && getCombo(index).length > 0; }
    public void setCellPressTime(int index, long time) {
        if (!isValidBindingIndex(index)) return;
        if (cellPressTimes == null) cellPressTimes = new long[bindings.length];
        else if (cellPressTimes.length != bindings.length) cellPressTimes = Arrays.copyOf(cellPressTimes, bindings.length);
        cellPressTimes[index] = time;
    }

    public Binding getHoldKey() { return holdKey != null ? holdKey : Binding.NONE; }
    public void setHoldKey(Binding key) {
        this.holdKey = key != null ? key : Binding.NONE;
        if (sourceJSONObject != null) holdKeyEdited = true;
    }
    public float getDeadZone() { return deadZone; }
    public void setDeadZone(float dz) { this.deadZone = clampFinite(dz, 0f, 0.5f, STICK_DEAD_ZONE); }
    public boolean isCustomAreaAppearanceEnabled() { return customAreaAppearanceEnabled; }
    public void setCustomAreaAppearanceEnabled(boolean enabled) { customAreaAppearanceEnabled = enabled; }
    public int getCustomAreaColor() { return customAreaColor; }
    public void setCustomAreaColor(int color) { customAreaColor = 0xFF000000 | (color & 0x00FFFFFF); }
    public float getCustomAreaOpacity() { return customAreaOpacity; }
    public void setCustomAreaOpacity(float opacity) { customAreaOpacity = clampFinite(opacity, 0f, 1f, 0.25f); }
    public String getGroupId() { return groupId; }
    public void setGroupId(String id) {
        String trimmedId = id != null ? id.trim() : null;
        this.groupId = (trimmedId == null || trimmedId.isEmpty()) ? null : trimmedId;
    }
    public boolean isInGroup() { return groupId != null && inputControlsView.getProfile() != null && inputControlsView.getProfile().getGroup(groupId) != null; }

    private static Binding[] sanitizeCombo(Binding[] combo) {
        if (combo == null || combo.length == 0) return new Binding[0];
        Binding[] sanitized = new Binding[Math.min(combo.length, MAX_COMBO_BINDINGS)];
        int count = 0;
        for (Binding binding : combo) {
            if (binding != null && binding != Binding.NONE) {
                sanitized[count++] = binding;
                if (count == MAX_COMBO_BINDINGS) break;
            }
        }
        return count == sanitized.length ? sanitized : Arrays.copyOf(sanitized, count);
    }

    private static String[] sanitizeRawComboNames(String[] rawNames) {
        if (rawNames == null) return null;
        String[] sanitized = new String[Math.min(rawNames.length, MAX_COMBO_BINDINGS)];
        int count = 0;
        for (String rawName : rawNames) {
            if (rawName != null) {
                sanitized[count++] = rawName;
                if (count == MAX_COMBO_BINDINGS) break;
            }
        }
        return count == sanitized.length ? sanitized : Arrays.copyOf(sanitized, count);
    }

    Binding[] getEffectiveBindingsForSlot(int index) {
        if (!isValidBindingIndex(index)) return new Binding[0];
        Binding mainBinding = getBindingAt(index);
        Binding[] combo = getCombo(index);
        if (combo == null || combo.length == 0) {
            return mainBinding == Binding.NONE ? new Binding[0] : new Binding[]{mainBinding};
        }
        if (mainBinding == Binding.NONE) return combo;
        for (Binding binding : combo) if (binding == mainBinding) return combo;
        Binding[] effective = new Binding[combo.length + 1];
        effective[0] = mainBinding;
        System.arraycopy(combo, 0, effective, 1, combo.length);
        return effective;
    }

    boolean usesGamepadBinding() {
        for (Binding binding : bindings) if (binding.isGamepad()) return true;
        if (comboBindings != null) {
            for (Binding[] combo : comboBindings) {
                if (combo == null) continue;
                for (Binding binding : combo) if (binding.isGamepad()) return true;
            }
        }
        return false;
    }

    private void notifyBindingsChanged() {
        ControlsProfile profile = inputControlsView != null ? inputControlsView.getProfile() : null;
        if (profile != null) profile.updateVirtualGamepad();
    }

    public boolean isToggleSwitch() {
        return toggleSwitch;
    }

    public void setToggleSwitch(boolean toggleSwitch) {
        this.toggleSwitch = toggleSwitch;
    }

    public boolean isSwipeable() {
        return swipeable;
    }

    public void setSwipeable(boolean swipeable) {
        this.swipeable = swipeable;
    }

    /** True when a moving finger may press this element by sliding onto it (and it releases on slide-off). */
    public boolean isSwipeTarget() {
        return (type == Type.BUTTON || type == Type.D_PAD) && swipeable;
    }

    public boolean isCapturing(int pointerId) {
        return currentPointerId == pointerId;
    }

    public Binding getBindingAt(int index) {
        return isValidBindingIndex(index) ? bindings[index] : Binding.NONE;
    }

    public void setBindingAt(int index, Binding binding) {
        if (index < 0 || index >= MAX_BINDING_COUNT) return;
        ensureBindingCapacity(index + 1);
        bindings[index] = binding != null ? binding : Binding.NONE;
        if (sourceJSONObject != null) {
            JSONArray sourceBindings = sourceJSONObject.optJSONArray("bindings");
            if (sourceBindings != null) {
                try {
                    while (sourceBindings.length() <= index) sourceBindings.put(Binding.NONE.name());
                    sourceBindings.put(index, bindings[index].name());
                }
                catch (JSONException ignored) {}
            }
        }
        notifyBindingsChanged();
    }

    public void setBinding(Binding binding) {
        Arrays.fill(bindings, binding != null ? binding : Binding.NONE);
        notifyBindingsChanged();
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = clampFinite(scale, MIN_SCALE, MAX_SCALE, 1.0f);
        boundingBoxNeedsUpdate = true;
    }

    public short getX() {
        return x;
    }

    public void setX(int x) {
        this.x = (short)x;
        boundingBoxNeedsUpdate = true;
    }

    public short getY() {
        return y;
    }

    public void setY(int y) {
        this.y = (short)y;
        boundingBoxNeedsUpdate = true;
    }

    public void setPosition(int x, int y) {
        setX(x);
        setY(y);
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text != null ? text : "";
    }

    public short getIconId() {
        return iconId;
    }

    public void setIconId(int iconId) {
        int normalizedId = iconId >= Byte.MIN_VALUE && iconId < 0 ? iconId & 0xFF : iconId;
        this.iconId = (short)Math.max(0, Math.min(255, normalizedId));
    }

    public boolean isCustomIconTintEnabled() {
        return customIconTintEnabled;
    }

    public void setCustomIconTintEnabled(boolean enabled) {
        customIconTintEnabled = enabled;
    }

    public boolean isCustomIconAsButton() {
        return customIconAsButton;
    }

    public void setCustomIconAsButton(boolean enabled) {
        customIconAsButton = enabled;
    }

    void loadCustomIconOptions(JSONObject elementJSONObject) throws JSONException {
        customIconTintEnabled = !elementJSONObject.has("customIconTintEnabled")
                || elementJSONObject.getBoolean("customIconTintEnabled");
        customIconAsButton = elementJSONObject.has("customIconAsButton")
                && elementJSONObject.getBoolean("customIconAsButton");
    }

    void writeCustomIconOptions(JSONObject elementJSONObject) throws JSONException {
        elementJSONObject.put("customIconTintEnabled", customIconTintEnabled);
        elementJSONObject.put("customIconAsButton", customIconAsButton);
    }

    private static float clampFinite(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    public Rect getBoundingBox() {
        if (boundingBoxNeedsUpdate) computeBoundingBox();
        if (type == Type.EXPANDABLE_BUTTON && expanded) {
            expandedBoundingBox.set(boundingBox);
            expandedBoundingBox.offset(Math.round(expandedOffsetX), Math.round(expandedOffsetY));
            return expandedBoundingBox;
        }
        return boundingBox;
    }

    private Rect computeBoundingBox() {
        int snappingSize = inputControlsView.getSnappingSize();
        int halfWidth = 0;
        int halfHeight = 0;

        switch (type) {
            case BUTTON:
            case EXPANDABLE_BUTTON:
                switch (shape) {
                    case RECT:
                    case ROUND_RECT:
                        halfWidth = snappingSize * 4;
                        halfHeight = snappingSize * 2;
                        break;
                    case SQUARE:
                        halfWidth = (int)(snappingSize * 2.5f);
                        halfHeight = (int)(snappingSize * 2.5f);
                        break;
                    case CIRCLE:
                        halfWidth = snappingSize * 3;
                        halfHeight = snappingSize * 3;
                        break;
                }
                break;
            case D_PAD: {
                halfWidth = snappingSize * 7;
                halfHeight = snappingSize * 7;
                break;
            }
            case TRACKPAD:
            case STICK: {
                halfWidth = snappingSize * 6;
                halfHeight = snappingSize * 6;
                break;
            }
            case DYNAMIC_STICK: {
                halfWidth = (areaWidth > 0 ? areaWidth : 600) / 2;
                halfHeight = (areaHeight > 0 ? areaHeight : 600) / 2;
                break;
            }
            case MOUSE_AREA: {
                halfWidth = (areaWidth > 0 ? areaWidth : 800) / 2;
                halfHeight = (areaHeight > 0 ? areaHeight : 400) / 2;
                break;
            }
            case BUTTON_GRID: {
                int cols = getEffectiveGridCols();
                int rows = getEffectiveGridRows();
                Shape cellShape = getGridCellShape();
                float cellWidth = snappingSize
                        * (cellShape == Shape.SQUARE || cellShape == Shape.CIRCLE ? 4f : 6f);
                float cellHeight = snappingSize * 4f;
                float gap = snappingSize * gridSpacing;
                halfWidth = Math.round((cellWidth * cols + gap * (cols - 1)) * 0.5f);
                halfHeight = Math.round((cellHeight * rows + gap * (rows - 1)) * 0.5f);
                break;
            }
            case RANGE_BUTTON: {
                halfWidth = snappingSize * ((bindings.length * 4) / 2);
                halfHeight = snappingSize * 2;

                if (orientation == 1) {
                    int tmp = halfWidth;
                    halfWidth = halfHeight;
                    halfHeight = tmp;
                }
                break;
            }
        }

        halfWidth *= scale;
        halfHeight *= scale;
        boundingBox.set(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight);
        boundingBoxNeedsUpdate = false;
        return boundingBox;
    }



    private String getDisplayText() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        else if (type == Type.EXPANDABLE_BUTTON) return expanded ? "X" : "+";
        else {
            Binding binding = getBindingAt(0);
            String text = getCompactBindingLabel(binding);
            if (text.length() > 7) {
                String[] parts = text.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String part : parts) sb.append(part.charAt(0));
                return (binding.isMouse() ? "M" : "")+ sb;
            }
            else return text;
        }
    }

    private static float getTextSizeForWidth(Paint paint, String text, float desiredWidth) {
        final byte testTextSize = 48;
        paint.setTextSize(testTextSize);
        return testTextSize * desiredWidth / paint.measureText(text);
    }

    private static String getRangeTextForIndex(Range range, int index) {
        String text = "";
        switch (range) {
            case FROM_A_TO_Z:
                text = String.valueOf((char)(65 + index));
                break;
            case FROM_0_TO_9:
                text = String.valueOf((index + 1) % 10);
                break;
            case FROM_F1_TO_F12:
                text = "F"+(index + 1);
                break;
            case FROM_NP0_TO_NP9:
                text = "NP"+((index + 1) % 10);
                break;
        }
        return text;
    }

    public void draw(Canvas canvas) {
        if (inputControlsView.getVisualStyle() == VisualStyle.GAMEHUB) {
            drawGameHub(canvas);
            return;
        }
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        int primaryColor = inputControlsView.getPrimaryColor();
        int overlayAlpha = Color.alpha(primaryColor);
        int blackFill = Color.argb(overlayAlpha, 0, 0, 0);

        paint.setColor(selected ? inputControlsView.getAccentColor() : primaryColor);
        paint.setStyle(Paint.Style.STROKE);
        float strokeWidth = snappingSize * 0.25f;
        paint.setStrokeWidth(strokeWidth);
        Rect boundingBox = getBoundingBox();

        boolean isL3R3 = type == Type.BUTTON && (getBindingAt(0) == Binding.GAMEPAD_BUTTON_L3 || getBindingAt(0) == Binding.GAMEPAD_BUTTON_R3);
        boolean isShoulderButton = type == Type.BUTTON && (getBindingAt(0) == Binding.GAMEPAD_BUTTON_L1 || getBindingAt(0) == Binding.GAMEPAD_BUTTON_R1 || getBindingAt(0) == Binding.GAMEPAD_BUTTON_L2 || getBindingAt(0) == Binding.GAMEPAD_BUTTON_R2);

        switch (type) {
            case BUTTON:
            case EXPANDABLE_BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                Shape effectiveShape = isShoulderButton ? Shape.ROUND_RECT : shape;
                boolean pressed = type == Type.EXPANDABLE_BUTTON ? expanded : states[0];
                int activeColor = pressed ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor();

                boolean imageAsButtonRequested = shouldDrawCustomIconAsButton();
                boolean imageAsButtonDrawn = imageAsButtonRequested
                        && drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, true);
                if (shouldSkipStandardButtonRendering(imageAsButtonRequested, imageAsButtonDrawn)) {
                    paint.setColor(oldColor);
                    break;
                }

                if (isL3R3) {
                    // Render L3/R3 like joystick circles
                    float radius = boundingBox.width() * 0.5f;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(blackFill);
                    canvas.drawCircle(cx, cy, radius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(activeColor);
                    paint.setStrokeWidth(strokeWidth);
                    canvas.drawCircle(cx, cy, radius, paint);

                    if (!imageAsButtonRequested && iconId > 0) {
                        drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, false);
                    } else {
                        String text = getDisplayText();
                        paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                        paint.setTextAlign(Paint.Align.CENTER);
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(activeColor);
                        canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                    }
                    paint.setColor(oldColor);
                    break;
                }

                // Fill - black with opacity
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                switch (effectiveShape) {
                    case CIRCLE:
                        canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                        break;
                    case RECT:
                        canvas.drawRect(boundingBox, paint);
                        break;
                    case ROUND_RECT:
                    case SQUARE: {
                        float radius = effectiveShape == Shape.ROUND_RECT ? boundingBox.height() * 0.5f : snappingSize * 0.75f * scale;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                }

                // Stroke - glow blue when pressed
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(activeColor);
                paint.setStrokeWidth(strokeWidth);
                switch (effectiveShape) {
                    case CIRCLE:
                        canvas.drawCircle(cx, cy, boundingBox.width() * 0.5f, paint);
                        break;
                    case RECT:
                        canvas.drawRect(boundingBox, paint);
                        break;
                    case ROUND_RECT:
                    case SQUARE: {
                        float radius = effectiveShape == Shape.ROUND_RECT ? boundingBox.height() * 0.5f : snappingSize * 0.75f * scale;
                        canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                        break;
                    }
                }

                // Text/Icon - glow blue when pressed
                if (!imageAsButtonRequested && iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, false);
                }
                else {
                    String text = getDisplayText();
                    paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(activeColor);
                    canvas.drawText(text, x, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                }
                paint.setColor(oldColor);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                Path path = inputControlsView.getPath();
                path.reset();

                // 4 separate rounded rectangle buttons with arrows
                float btnSize = snappingSize * 3.5f * scale;
                float gap = snappingSize * 0.75f * scale;
                float arrowW = btnSize * 0.35f;
                float arrowH = btnSize * 0.5f;
                float btnRadius = snappingSize * 0.5f * scale;

                // Draw each directional button: up, down, left, right
                // Each button is a rounded rect with an arrow inside

                // Helper: draw one direction button
                // [up]
                float upCx = cx;
                float upCy = boundingBox.top + btnSize * 0.5f;
                // fill
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawRoundRect(upCx - btnSize * 0.5f, upCy - btnSize * 0.5f, upCx + btnSize * 0.5f, upCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                // stroke
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[0] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(upCx - btnSize * 0.5f, upCy - btnSize * 0.5f, upCx + btnSize * 0.5f, upCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                // up arrow
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[0] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 1.2f);
                path.moveTo(upCx, upCy - arrowH * 0.5f);
                path.lineTo(upCx - arrowW * 0.5f, upCy + arrowH * 0.5f);
                path.lineTo(upCx + arrowW * 0.5f, upCy + arrowH * 0.5f);
                path.close();
                canvas.drawPath(path, paint);

                // [down]
                float downCx = cx;
                float downCy = boundingBox.bottom - btnSize * 0.5f;
                path.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawRoundRect(downCx - btnSize * 0.5f, downCy - btnSize * 0.5f, downCx + btnSize * 0.5f, downCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[2] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(downCx - btnSize * 0.5f, downCy - btnSize * 0.5f, downCx + btnSize * 0.5f, downCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[2] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 1.2f);
                path.moveTo(downCx, downCy + arrowH * 0.5f);
                path.lineTo(downCx - arrowW * 0.5f, downCy - arrowH * 0.5f);
                path.lineTo(downCx + arrowW * 0.5f, downCy - arrowH * 0.5f);
                path.close();
                canvas.drawPath(path, paint);

                // [left]
                float leftCx = boundingBox.left + btnSize * 0.5f;
                float leftCy = cy;
                path.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawRoundRect(leftCx - btnSize * 0.5f, leftCy - btnSize * 0.5f, leftCx + btnSize * 0.5f, leftCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[3] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(leftCx - btnSize * 0.5f, leftCy - btnSize * 0.5f, leftCx + btnSize * 0.5f, leftCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[3] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 1.2f);
                path.moveTo(leftCx - arrowH * 0.5f, leftCy);
                path.lineTo(leftCx + arrowH * 0.5f, leftCy - arrowW * 0.5f);
                path.lineTo(leftCx + arrowH * 0.5f, leftCy + arrowW * 0.5f);
                path.close();
                canvas.drawPath(path, paint);

                // [right]
                float rightCx = boundingBox.right - btnSize * 0.5f;
                float rightCy = cy;
                path.reset();
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawRoundRect(rightCx - btnSize * 0.5f, rightCy - btnSize * 0.5f, rightCx + btnSize * 0.5f, rightCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[1] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(rightCx - btnSize * 0.5f, rightCy - btnSize * 0.5f, rightCx + btnSize * 0.5f, rightCy + btnSize * 0.5f, btnRadius, btnRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(states[1] ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 1.2f);
                path.moveTo(rightCx + arrowH * 0.5f, rightCy);
                path.lineTo(rightCx - arrowH * 0.5f, rightCy - arrowW * 0.5f);
                path.lineTo(rightCx - arrowH * 0.5f, rightCy + arrowW * 0.5f);
                path.close();
                canvas.drawPath(path, paint);

                // Rounded center square - black fill + blue stroke
                float centerSize = snappingSize * 1.2f * scale;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawRoundRect(cx - centerSize, cy - centerSize, cx + centerSize, cy + centerSize, centerSize * 0.3f, centerSize * 0.3f, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawRoundRect(cx - centerSize, cy - centerSize, cx + centerSize, cy + centerSize, centerSize * 0.3f, centerSize * 0.3f, paint);

                paint.setColor(oldColor);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                int oldColor = paint.getColor();
                float radius = snappingSize * 0.75f * scale;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                Path path = inputControlsView.getPath();
                path.reset();

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left;
                    canvas.drawRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(startX, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(path);
                    startX -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startX > boundingBox.left && startX  < boundingBox.right) canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        String text = getRangeTextForIndex(range, index);

                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (y - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top;
                    canvas.drawRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, paint);

                    canvas.save();
                    path.addRoundRect(boundingBox.left, startY, boundingBox.right, boundingBox.bottom, radius, radius, Path.Direction.CW);
                    canvas.clipPath(inputControlsView.getPath());
                    startY -= scrollOffset % elementSize;

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(oldColor);

                        if (startY > boundingBox.top && startY < boundingBox.bottom) canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        String text = getRangeTextForIndex(range, index);

                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, x, startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }

                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(oldColor);
                    canvas.restore();
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                float outerRadius = boundingBox.height() * 0.5f;
                float stickRadiusPx = snappingSize * 1.8f * scale;

                // Dead zone indicator
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(40, 255, 0, 0));
                canvas.drawCircle(cx, cy, stickRadiusPx * deadZone, paint);

                // Outer circle - black fill
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawCircle(cx, cy, outerRadius, paint);

                // Outer circle - light blue stroke
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawCircle(cx, cy, outerRadius, paint);

                // Inner thumbstick
                float thumbstickX = getCurrentPosition().x;
                float thumbstickY = getCurrentPosition().y;
                short thumbRadius = (short) (snappingSize * 3.5f * scale);

                // Thumb - black fill
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(blackFill);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius, paint);

                // Thumb - light blue stroke
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth);
                canvas.drawCircle(thumbstickX, thumbstickY, thumbRadius + strokeWidth * 0.5f, paint);

                paint.setColor(oldColor);
                break;
            }

            case TRACKPAD: {
                float radius = boundingBox.height() * 0.15f;
                canvas.drawRoundRect(boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom, radius, radius, paint);
                float offset = strokeWidth * 2.5f;
                float innerStrokeWidth = strokeWidth * 2;
                float innerHeight = boundingBox.height() - offset * 2;
                radius = (innerHeight / boundingBox.height()) * radius - (innerStrokeWidth * 0.5f + strokeWidth * 0.5f);
                paint.setStrokeWidth(innerStrokeWidth);
                canvas.drawRoundRect(boundingBox.left + offset, boundingBox.top + offset, boundingBox.right - offset, boundingBox.bottom - offset, radius, radius, paint);
                break;
            }
            case DYNAMIC_STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                float areaHalfW = boundingBox.width() * 0.5f;
                float areaHalfH = boundingBox.height() * 0.5f;

                // Draw detection area (semi-transparent)
                paint.setStyle(Paint.Style.FILL);
                // Detection tint follows the live accent (theme, or the profile's custom accent)
                // instead of a hardcoded blue, so it recolors with every theme.
                int detectionAccent = inputControlsView.getAccentColor();
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : Color.argb((int)(overlayAlpha * 0.3f),
                                Color.red(detectionAccent), Color.green(detectionAccent), Color.blue(detectionAccent)));
                canvas.drawRoundRect(cx - areaHalfW, cy - areaHalfH, cx + areaHalfW, cy + areaHalfH, 16, 16, paint);

                // Draw area border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 0.5f);
                canvas.drawRoundRect(cx - areaHalfW, cy - areaHalfH, cx + areaHalfW, cy + areaHalfH, 16, 16, paint);

                if (stickVisible && currentPosition != null) {
                    float sRadius = stickRadius > 0 ? stickRadius : 120;
                    float sx = currentPosition.x;
                    float sy = currentPosition.y;

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.argb(40, 255, 0, 0));
                    canvas.drawCircle(sx, sy, (sRadius * scale * deadZone), paint);

                    // Outer circle
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(blackFill);
                    canvas.drawCircle(sx, sy, sRadius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(inputControlsView.getAccentColor());
                    paint.setStrokeWidth(strokeWidth);
                    canvas.drawCircle(sx, sy, sRadius, paint);

                    // Inner thumb
                    float thumbRadius = sRadius * 0.55f;
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(inputControlsView.getAccentColor());
                    canvas.drawCircle(sx, sy, thumbRadius, paint);
                }
                paint.setColor(oldColor);
                break;
            }
            case MOUSE_AREA: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int oldColor = paint.getColor();
                float mw = boundingBox.width() * 0.5f;
                float mh = boundingBox.height() * 0.5f;

                // Draw mouse area (semi-transparent green tint)
                paint.setStyle(Paint.Style.FILL);
                int mouseAccent = inputControlsView.getAccentColor();
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : Color.argb((int)(overlayAlpha * 0.25f),
                                Color.red(mouseAccent), Color.green(mouseAccent), Color.blue(mouseAccent)));
                canvas.drawRoundRect(cx - mw, cy - mh, cx + mw, cy + mh, 12, 12, paint);

                // Border
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : inputControlsView.getAccentColor());
                paint.setStrokeWidth(strokeWidth * 0.5f);
                canvas.drawRoundRect(cx - mw, cy - mh, cx + mw, cy + mh, 12, 12, paint);

                // Mouse icon hint (simple crosshair at center)
                float chSize = snappingSize * 1.5f;
                paint.setStrokeWidth(strokeWidth);
                canvas.drawLine(cx - chSize, cy, cx + chSize, cy, paint);
                canvas.drawLine(cx, cy - chSize, cx, cy + chSize, paint);

                paint.setColor(oldColor);
                break;
            }
            case BUTTON_GRID: {
                int cols = getEffectiveGridCols();
                int rows = getEffectiveGridRows();
                float gap = getGridSpacingPx();
                float cellW = (boundingBox.width() - gap * (cols - 1)) / cols;
                float cellH = (boundingBox.height() - gap * (rows - 1)) / rows;
                int oldColor = paint.getColor();
                Shape cellShape = getGridCellShape(); // use configured cell shape
                long now = System.currentTimeMillis();

                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int cellIdx = r * cols + c;
                        float left = boundingBox.left + c * (cellW + gap);
                        float top = boundingBox.top + r * (cellH + gap);
                        float right = left + cellW;
                        float bottom = top + cellH;
                        // Build cell rect for shape drawing
                        Rect cellRect = getGridCellRect(left, top, right, bottom);
                        boolean pressed = cellIdx < states.length && states[cellIdx];

                        // --- Press flash animation ---
                        int cellFillColor = blackFill;
                        if (pressed) {
                            cellFillColor = inputControlsView.getAccentColor();
                        }
                        cellFillColor = applyGridPressFlash(cellIdx, cellFillColor, pressed, now);

                        // Cell fill with shape
                        paint.setStyle(Paint.Style.FILL);
                        paint.setColor(cellFillColor);
                        drawShapeForCell(canvas, paint, cellRect, cellShape);

                        // Cell border
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(inputControlsView.getAccentColor());
                        paint.setStrokeWidth(strokeWidth * 0.3f);
                        drawShapeForCell(canvas, paint, cellRect, cellShape);
                        paint.setStrokeWidth(strokeWidth);

                        // Cell label
                        if (cellIdx < bindings.length) {
                            String label = getGridCellLabel(cellIdx, 6);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(primaryColor);
                            paint.setTextSize(Math.min(cellH * 0.35f, snappingSize * 1.0f * scale));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(label, (left + right) * 0.5f,
                                (top + bottom) * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                    }
                }
                paint.setColor(oldColor);
                break;
            }
        }
    }

    public void drawEditorSelectionBorder(Canvas canvas) {
        if (!inputControlsView.isEditMode() || !selected) return;

        Rect bounds = getBoundingBox();
        Paint paint = inputControlsView.getPaint();
        float strokeWidth = Math.max(2f, inputControlsView.getSnappingSize() * 0.4f);
        float inset = strokeWidth * 0.5f;
        iconAspectFitDestinationRect.set(
                bounds.left + inset,
                bounds.top + inset,
                bounds.right - inset,
                bounds.bottom - inset);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(inputControlsView.getAccentBrightColor());
        paint.setColorFilter(null);
        paint.setAlpha(255);
        float radius = Math.max(6f, inputControlsView.getSnappingSize() * 0.75f);
        canvas.drawRoundRect(iconAspectFitDestinationRect, radius, radius, paint);
    }

    private boolean isEngaged() {
        return expanded || currentPointerId != -1 || buttonGridTouchState.hasTrackedPointers()
                || (toggleSwitch && selected);
    }

    private int resolveAccentColor() {
        // Phase 4: drive the GAMEHUB glass style off the live theme accent (full opacity)
        // instead of the hardcoded blue fallback. Never -1 now → the hasAccent path is live.
        return inputControlsView.getAccentColor();
    }

    private static int colorWithAlpha(int color, float opacity) {
        return Color.argb(
                Math.round(255 * Math.max(0f, Math.min(1f, opacity))),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private GameHubLayout.RenderShape gameHubTriggerShape() {
        return GameHubLayout.triggerShapeFor(GameHubLayout.roleFor(this));
    }

    private void drawGameHub(Canvas canvas) {
        int snappingSize = inputControlsView.getSnappingSize();
        Paint paint = inputControlsView.getPaint();
        float effectiveOpacity = inputControlsView.isEditMode() ? Math.max(0.15f, 1.0f) : 1.0f;
        float overlayOpacity = inputControlsView.getOverlayOpacity();
        boolean engaged = isEngaged();
        Rect boundingBox = getBoundingBox();
        ControlsIconStyle iconStyle = inputControlsView.getIconStyle();
        boolean flat = iconStyle != ControlsIconStyle.GLASS;

        int accent = resolveAccentColor();
        // resolveAccentColor() always returns a full-opacity ARGB (getAccentColor forces alpha
        // 0xff), so it never yields a real "no accent" sentinel. The old `accent != -1` check
        // collided with pure white (0xFFFFFFFF == -1 as a signed int), making white controls fall
        // back to the default blue. Accent is always live now. (issue #46)
        boolean hasAccent = true;

        // Map opacity linearly so the full slider range is usable: 0 = fully invisible,
        // 1 = fully solid. (Was 0.5 + 0.7*opacity, which floored visibility at ~50% and
        // saturated at ~71% — the slider felt like it did nothing.)
        float gameHubDim = overlayOpacity;
        int fillAlpha = (int) (90 * gameHubDim * effectiveOpacity);
        int strokeAlpha = (int) (150 * gameHubDim * effectiveOpacity);
        int pressedFillAlpha = (int) (60 * gameHubDim * effectiveOpacity);
        int pressedStrokeAlpha = (int) (220 * gameHubDim * effectiveOpacity);
        int textAlpha = (int) (255 * gameHubDim * effectiveOpacity);
        int glassEdgeAlpha = flat ? 0 : (int) (75 * gameHubDim * effectiveOpacity);

        int fillColor = Color.argb(fillAlpha, 0, 0, 0);
        int strokeColor = hasAccent
                ? Color.argb(Math.max(strokeAlpha, (int) (110 * gameHubDim)), Color.red(accent), Color.green(accent), Color.blue(accent))
                : Color.argb(strokeAlpha, 0x1C, 0x85, 0xFE);
        int pressedFillBase = hasAccent ? accent : 0xFF1C85FE;
        int pressedFillColor = Color.argb(pressedFillAlpha, Color.red(pressedFillBase), Color.green(pressedFillBase), Color.blue(pressedFillBase));
        int pressedStrokeColor = hasAccent
                ? Color.argb(Math.max(pressedStrokeAlpha, (int) (160 * gameHubDim)), Color.red(accent), Color.green(accent), Color.blue(accent))
                : Color.argb(pressedStrokeAlpha, 0x64, 0xDD, 0xFF);
        int textColor = hasAccent
                ? Color.argb(textAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
                : Color.argb(textAlpha, 0x1C, 0x85, 0xFE);

        // Drop shadow alpha must track opacity too — otherwise the fixed-alpha blue glow
        // (0x40) keeps showing through at low opacity, reading as a solid blue fill on the
        // compact SQUARE keys (MRB/BKSP/SPACE/ENTER) while their fill/stroke/text fade out.
        int shadowAlpha = flat ? 0 : (int) (0x40 * gameHubDim * effectiveOpacity);
        int shadowColor = Color.argb(shadowAlpha, Color.red(accent), Color.green(accent), Color.blue(accent));

        // Flat styles (everything but GLASS) share one palette; the face buttons of RETRO ask for their own per role.
        FlatPalette basePal = flat ? flatPalette(iconStyle, accent, gameHubDim, null) : null;
        if (flat) {
            fillColor = basePal.fill;
            pressedFillColor = basePal.pFill;
            strokeColor = basePal.stroke;
            pressedStrokeColor = basePal.pStroke;
            textColor = basePal.text;
        }

        if (selected && !hasAccent) {
            int highlightAlpha = (int) (255 * overlayOpacity);
            strokeColor = Color.argb(highlightAlpha, 2, 119, 189);
        }

        float strokeWidth = flat
                ? Math.max(2f, basePal.strokeDp * inputControlsView.getResources().getDisplayMetrics().density)
                : Math.max(2f, snappingSize * 0.18f);
        paint.setStrokeWidth(strokeWidth);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeCap(Paint.Cap.ROUND);

        switch (type) {
            case BUTTON:
            case EXPANDABLE_BUTTON: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();
                GameHubLayout.RenderShape triggerShape = gameHubTriggerShape();
                boolean isTrigger = triggerShape != null;

                boolean imageAsButtonRequested = shouldDrawCustomIconAsButton();
                boolean imageAsButtonDrawn = imageAsButtonRequested
                        && drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, true);
                if (shouldSkipStandardButtonRendering(imageAsButtonRequested, imageAsButtonDrawn)) {
                    break;
                }

                FlatPalette keyPal = flat ? flatPalette(iconStyle, accent, gameHubDim, GameHubLayout.roleFor(this)) : null;
                if (flat) {
                    drawFlatKey(canvas, paint, boundingBox, isTrigger, engaged, keyPal);
                } else if (isTrigger) {
                    GameHubLayout.buildTriggerPath(
                            path, triggerShape,
                            boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom);
                    paint.setShadowLayer(snappingSize * 0.08f, 0, snappingSize * 0.04f, shadowColor);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawPath(path, paint);
                    paint.setShadowLayer(0f, 0f, 0f, 0);
                    if (engaged) {
                        paint.setColor(pressedFillColor);
                        canvas.drawPath(path, paint);
                    }
                    drawGameHubGlassOnPath(
                            canvas, paint, path, cx, cy,
                            Math.max(boundingBox.width(), boundingBox.height()) * 0.5f, glassEdgeAlpha);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                    canvas.drawPath(path, paint);
                } else {
                    paint.setShadowLayer(snappingSize * 0.08f, 0, snappingSize * 0.04f, shadowColor);
                    drawGameHubShape(canvas, paint, boundingBox, fillColor, true);
                    paint.setShadowLayer(0f, 0f, 0f, 0);
                    if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true);
                    drawGameHubGlassShape(canvas, paint, boundingBox, glassEdgeAlpha);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                    drawGameHubShape(canvas, paint, boundingBox, 0, false);
                }

                if (!imageAsButtonRequested && iconId > 0) {
                    drawIcon(canvas, cx, cy, boundingBox.width(), boundingBox.height(), iconId, false);
                } else {
                    String label = getDisplayText();
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(flat ? (engaged ? keyPal.pText : keyPal.text) : textColor);
                    paint.setTextSize(
                            Math.min(
                                    getTextSizeForWidth(paint, label, boundingBox.width() - strokeWidth * 2),
                                    snappingSize * 2 * scale));
                    paint.setTextAlign(Paint.Align.CENTER);
                    paint.setFakeBoldText(true);
                    canvas.drawText(label, cx, (cy - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                    paint.setFakeBoldText(false);
                }
                break;
            }
            case STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                float ringRadius = boundingBox.height() * 0.5f;
                float stickRadiusPx = snappingSize * 1.8f * scale;

                // Dead zone indicator
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(Color.argb(40, 255, 0, 0));
                canvas.drawCircle(cx, cy, stickRadiusPx * deadZone, paint);

                if (flat) {
                    float knobX = engaged ? getCurrentPosition().x : cx;
                    float knobY = engaged ? getCurrentPosition().y : cy;
                    drawFlatStick(canvas, paint, cx, cy, ringRadius, knobX, knobY, engaged, basePal);
                    break;
                }

                int ringFillAlpha = fillAlpha;
                int ringFill = Color.argb(ringFillAlpha, 0, 0, 0);

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ringFill);
                canvas.drawCircle(cx, cy, ringRadius, paint);


                if (glassEdgeAlpha > 0) {
                    paint.setShader(new RadialGradient(
                            cx, cy, ringRadius,
                            Color.argb(0, 0, 0, 0), Color.argb(glassEdgeAlpha, 0, 0, 0),
                            Shader.TileMode.CLAMP));
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(cx, cy, ringRadius, paint);
                    paint.setShader(null);
                }

                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                canvas.drawCircle(cx, cy, ringRadius - strokeWidth * 0.5f, paint);

                float thumbX = engaged ? getCurrentPosition().x : cx;
                float thumbY = engaged ? getCurrentPosition().y : cy;
                float thumbRadius = ringRadius * 0.48f;
                int thumbFillAlpha = (int) ((engaged ? 100 : 77) * gameHubDim * effectiveOpacity);
                int thumbFill = hasAccent
                        ? Color.argb(thumbFillAlpha, Color.red(accent), Color.green(accent), Color.blue(accent))
                        : Color.argb(thumbFillAlpha, 0x1C, 0x85, 0xFE);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(thumbFill);
                canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                canvas.drawCircle(thumbX, thumbY, thumbRadius - strokeWidth * 0.5f, paint);
                break;
            }
            case D_PAD: {
                float cx = boundingBox.centerX();
                float cy = boundingBox.centerY();

                float radius = Math.min(boundingBox.width(), boundingBox.height()) * 0.5f;
                if (flat) {
                    drawFlatDpad(canvas, paint, cx, cy, radius, basePal);
                    break;
                }
                float[] arrowCenter = new float[2];
                float arrowGradR = radius * 0.5f;
                for (int side = 0; side < 4; side++) {
                    path.reset();
                    GameHubLayout.buildDpadArrow(path, side, cx, cy, radius);
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(fillColor);
                    canvas.drawPath(path, paint);
                    if (engaged) {
                        paint.setColor(pressedFillColor);
                        canvas.drawPath(path, paint);
                    }
                    if (glassEdgeAlpha > 0) {
                        GameHubLayout.dpadArrowCenter(side, cx, cy, radius, arrowCenter);
                        drawGameHubGlassOnPath(
                                canvas, paint, path, arrowCenter[0], arrowCenter[1], arrowGradR, glassEdgeAlpha);
                    }
                }
                GameHubLayout.buildDpadArrows(path, cx, cy, radius);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                canvas.drawPath(path, paint);
                break;
            }
            case TRACKPAD: {
                float radius = boundingBox.height() * 0.18f;
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(fillColor);
                canvas.drawRoundRect(
                        boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
                        radius, radius, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                canvas.drawRoundRect(
                        boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
                        radius, radius, paint);
                break;
            }
            case RANGE_BUTTON: {
                Range range = getRange();
                float radius = snappingSize * 0.75f * scale;
                float elementSize = scroller.getElementSize();
                float minTextSize = snappingSize * 2 * scale;
                float scrollOffset = scroller.getScrollOffset();
                byte[] rangeIndex = scroller.getRangeIndex();
                path.reset();

                drawGameHubShape(canvas, paint, boundingBox, fillColor, true, Shape.ROUND_RECT);
                if (engaged) drawGameHubShape(canvas, paint, boundingBox, pressedFillColor, true, Shape.ROUND_RECT);
                drawGameHubGlassShape(canvas, paint, boundingBox, glassEdgeAlpha, Shape.ROUND_RECT);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(engaged ? pressedStrokeColor : strokeColor);
                drawGameHubShape(canvas, paint, boundingBox, 0, false, Shape.ROUND_RECT);

                canvas.save();
                path.addRoundRect(
                        boundingBox.left, boundingBox.top, boundingBox.right, boundingBox.bottom,
                        radius, radius, Path.Direction.CW);
                canvas.clipPath(path);

                if (orientation == 0) {
                    float lineTop = boundingBox.top + strokeWidth * 0.5f;
                    float lineBottom = boundingBox.bottom - strokeWidth * 0.5f;
                    float startX = boundingBox.left - (scrollOffset % elementSize);

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(strokeColor);
                        if (startX > boundingBox.left && startX < boundingBox.right)
                            canvas.drawLine(startX, lineTop, startX, lineBottom, paint);
                        String text = getRangeTextForIndex(range, index);
                        if (startX < boundingBox.right && startX + elementSize > boundingBox.left) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(textColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, elementSize - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, startX + elementSize * 0.5f, (boundingBox.centerY() - ((paint.descent() + paint.ascent()) * 0.5f)), paint);
                        }
                        startX += elementSize;
                    }
                }
                else {
                    float lineLeft = boundingBox.left + strokeWidth * 0.5f;
                    float lineRight = boundingBox.right - strokeWidth * 0.5f;
                    float startY = boundingBox.top - (scrollOffset % elementSize);

                    for (byte i = rangeIndex[0]; i < rangeIndex[1]; i++) {
                        int index = i % range.max;
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(strokeColor);
                        if (startY > boundingBox.top && startY < boundingBox.bottom)
                            canvas.drawLine(lineLeft, startY, lineRight, startY, paint);
                        String text = getRangeTextForIndex(range, index);
                        if (startY < boundingBox.bottom && startY + elementSize > boundingBox.top) {
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(textColor);
                            paint.setTextSize(Math.min(getTextSizeForWidth(paint, text, boundingBox.width() - strokeWidth * 2), minTextSize));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(text, boundingBox.centerX(), startY + elementSize * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                        startY += elementSize;
                    }
                }
                canvas.restore();
                break;
            }
            case DYNAMIC_STICK: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                int ringFillAlpha = (int)(fillAlpha * 0.4f);
                int ringFill = Color.argb(ringFillAlpha, 0, 0, 0);

                float areaHalfW = boundingBox.width() * 0.5f;
                float areaHalfH = boundingBox.height() * 0.5f;
                float areaRadius = 16f;

                // A custom area remains visible in-game; the inherited area keeps the legacy
                // editor-only behavior.
                if (inputControlsView.isEditMode() || customAreaAppearanceEnabled) {
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(customAreaAppearanceEnabled
                            ? colorWithAlpha(customAreaColor, customAreaOpacity)
                            : Color.argb((int)(fillAlpha * 0.15f),
                                    Color.red(accent), Color.green(accent), Color.blue(accent)));
                    canvas.drawRoundRect(cx - areaHalfW, cy - areaHalfH, cx + areaHalfW, cy + areaHalfH, areaRadius, areaRadius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(customAreaAppearanceEnabled
                            ? colorWithAlpha(customAreaColor, customAreaOpacity)
                            : strokeColor);
                    paint.setStrokeWidth(strokeWidth * 0.5f);
                    canvas.drawRoundRect(cx - areaHalfW, cy - areaHalfH, cx + areaHalfW, cy + areaHalfH, areaRadius, areaRadius, paint);
                    paint.setStrokeWidth(strokeWidth);
                }

                // Smooth interpolation for stick position animation
                final float LERP = 0.3f;
                if (currentPosition != null && stickVisible) {
                    float tx = currentPosition.x;
                    float ty = currentPosition.y;
                    visualStickX += (tx - visualStickX) * LERP;
                    visualStickY += (ty - visualStickY) * LERP;
                }

                if (stickVisible && currentPosition != null) {
                    float sRadius = stickRadius > 0 ? stickRadius : 120;
                    float sx = visualStickX;
                    float sy = visualStickY;

                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(Color.argb(40, 255, 0, 0));
                    canvas.drawCircle(sx, sy, (sRadius * scale * deadZone), paint);

                    // Outer ring
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(ringFill);
                    canvas.drawCircle(sx, sy, sRadius, paint);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(strokeColor);
                    canvas.drawCircle(sx, sy, sRadius - strokeWidth * 0.5f, paint);

                    // Inner thumb (follows finger offset from stick center, with animation)
                    float thumbRadius = sRadius * 0.55f;
                    // Use stored finger position for smooth thumb tracking
                    float fingerDx = lastFingerX - sx;
                    float fingerDy = lastFingerY - sy;
                    float fingerDist = (float)Math.sqrt(fingerDx * fingerDx + fingerDy * fingerDy);
                    // Clamp thumb within stick
                    float maxThumbDist = sRadius - thumbRadius;
                    float thumbX, thumbY;
                    if (fingerDist > maxThumbDist) {
                        float scale = maxThumbDist / fingerDist;
                        thumbX = sx + fingerDx * scale;
                        thumbY = sy + fingerDy * scale;
                    } else {
                        thumbX = lastFingerX;
                        thumbY = lastFingerY;
                    }
                    paint.setStyle(Paint.Style.FILL);
                    paint.setColor(hasAccent ? accent : Color.rgb(0x1C, 0x85, 0xFE));
                    canvas.drawCircle(thumbX, thumbY, thumbRadius, paint);
                }
                break;
            }
            case MOUSE_AREA: {
                int cx = boundingBox.centerX();
                int cy = boundingBox.centerY();
                float mw = boundingBox.width() * 0.5f;
                float mh = boundingBox.height() * 0.5f;

                paint.setStyle(Paint.Style.FILL);
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : Color.argb((int)(fillAlpha * 0.12f),
                                Color.red(accent), Color.green(accent), Color.blue(accent)));
                canvas.drawRoundRect(cx - mw, cy - mh, cx + mw, cy + mh, 12, 12, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setColor(customAreaAppearanceEnabled
                        ? colorWithAlpha(customAreaColor, customAreaOpacity)
                        : strokeColor);
                paint.setStrokeWidth(strokeWidth * 0.5f);
                canvas.drawRoundRect(cx - mw, cy - mh, cx + mw, cy + mh, 12, 12, paint);
                paint.setStrokeWidth(strokeWidth);

                // Crosshair
                float chSize = snappingSize * 1.5f;
                canvas.drawLine(cx - chSize, cy, cx + chSize, cy, paint);
                canvas.drawLine(cx, cy - chSize, cx, cy + chSize, paint);
                break;
            }
            case BUTTON_GRID: {
                int cols = getEffectiveGridCols();
                int rows = getEffectiveGridRows();
                float gap = getGridSpacingPx();
                float cellW = (boundingBox.width() - gap * (cols - 1)) / cols;
                float cellH = (boundingBox.height() - gap * (rows - 1)) / rows;
                Shape cellShape = getGridCellShape();
                long now = System.currentTimeMillis();

                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int cellIdx = r * cols + c;
                        float left = boundingBox.left + c * (cellW + gap);
                        float top = boundingBox.top + r * (cellH + gap);
                        float right = left + cellW;
                        float bottom = top + cellH;
                        boolean pressed = cellIdx < states.length && states[cellIdx];

                        Rect cellRect = getGridCellRect(left, top, right, bottom);
                        int cellFillColor = applyGridPressFlash(cellIdx, pressed ? pressedFillColor : fillColor, pressed, now);
                        drawGameHubShape(canvas, paint, cellRect, cellFillColor, true, cellShape);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setColor(strokeColor);
                        paint.setStrokeWidth(strokeWidth * 0.3f);
                        drawGameHubShape(canvas, paint, cellRect, 0, false, cellShape);
                        paint.setStrokeWidth(strokeWidth);

                        if (cellIdx < bindings.length) {
                            String label = getGridCellLabel(cellIdx, 4);
                            paint.setStyle(Paint.Style.FILL);
                            paint.setColor(textColor);
                            paint.setTextSize(Math.min(cellH * 0.4f, snappingSize * 1.2f * scale));
                            paint.setTextAlign(Paint.Align.CENTER);
                            canvas.drawText(label, (left + right) * 0.5f,
                                (top + bottom) * 0.5f - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                        }
                    }
                }
                break;
            }

            default:
                drawOriginalLegacy(canvas);
                break;
        }
    }

    private void drawGameHubShape(Canvas canvas, Paint paint, Rect bb, int color, boolean fill) {
        drawGameHubShape(canvas, paint, bb, color, fill, shape);
    }

    /** Colours and flags of one flat icon style (everything except GLASS); built per element by flatPalette. */
    private static final class FlatPalette {
        int fill, pFill, stroke, pStroke, text, pText;
        int stickBase, stickBaseHeld, knob, knobHeld;
        int glow;            // 0 = no glow; otherwise the colour of the halo around strokes
        float strokeDp;
        boolean cross;       // D-pad drawn as one cross of arrows instead of four round pads
    }

    private static int shade(int tint, float opacity, int alpha, float factor) {
        return Color.argb((int) (alpha * opacity),
                (int) (Color.red(tint) * factor), (int) (Color.green(tint) * factor), (int) (Color.blue(tint) * factor));
    }

    private static int light(int tint, float opacity, int alpha, float factor) {
        return Color.argb((int) (alpha * opacity),
                (int) (Color.red(tint) + (255 - Color.red(tint)) * factor),
                (int) (Color.green(tint) + (255 - Color.green(tint)) * factor),
                (int) (Color.blue(tint) + (255 - Color.blue(tint)) * factor));
    }

    private static int rgb(int color, float opacity, int alpha) {
        return Color.argb((int) (alpha * opacity), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int retroColor(GameHubLayout.Role role) {
        if (role == null) return 0xFFC8C8CE;
        switch (role) {
            case BTN_A: return 0xFF4CAF50;
            case BTN_B: return 0xFFE53935;
            case BTN_X: return 0xFF4285F4;
            case BTN_Y: return 0xFFFBC02D;
            default: return 0xFFC8C8CE;
        }
    }

    /** FUSION is the DroidDeck pad recipe: the accent darkened/lightened and scaled by the overlay opacity. */
    private static FlatPalette flatPalette(ControlsIconStyle style, int accent, float o, GameHubLayout.Role role) {
        FlatPalette p = new FlatPalette();
        switch (style) {
            case NEON:
                p.fill = rgb(accent, o, 30);
                p.pFill = rgb(accent, o, 120);
                p.stroke = light(accent, o, 235, 0.35f);
                p.pStroke = light(accent, o, 255, 0.85f);
                p.text = light(accent, o, 255, 0.6f);
                p.pText = Color.WHITE;
                p.stickBase = rgb(accent, o, 22);
                p.stickBaseHeld = rgb(accent, o, 50);
                p.knob = rgb(accent, o, 80);
                p.knobHeld = rgb(accent, o, 150);
                p.glow = rgb(accent, o, 230);
                p.strokeDp = 1.6f;
                p.cross = true;
                break;
            case SOLID:
                p.fill = Color.argb((int) (235 * o), 32, 34, 40);
                p.pFill = rgb(accent, o, 255);
                p.stroke = 0;
                p.pStroke = 0;
                p.text = Color.argb((int) (255 * o), 235, 238, 242);
                p.pText = Color.argb(255, 16, 18, 22);
                p.stickBase = Color.argb((int) (235 * o), 24, 26, 31);
                p.stickBaseHeld = Color.argb((int) (235 * o), 24, 26, 31);
                p.knob = Color.argb((int) (255 * o), 62, 66, 76);
                p.knobHeld = rgb(accent, o, 255);
                p.strokeDp = 0f;
                p.cross = true;
                break;
            case MINIMAL:
                p.fill = 0;
                p.pFill = Color.argb((int) (70 * o), 255, 255, 255);
                p.stroke = Color.argb((int) (170 * o), 255, 255, 255);
                p.pStroke = Color.argb((int) (255 * o), 255, 255, 255);
                p.text = Color.argb((int) (220 * o), 255, 255, 255);
                p.pText = Color.WHITE;
                p.stickBase = 0;
                p.stickBaseHeld = Color.argb((int) (35 * o), 255, 255, 255);
                p.knob = Color.argb((int) (45 * o), 255, 255, 255);
                p.knobHeld = Color.argb((int) (90 * o), 255, 255, 255);
                p.strokeDp = 1.2f;
                break;
            case RETRO: {
                int c = retroColor(role);
                p.fill = Color.argb((int) (230 * o), 24, 24, 28);
                p.pFill = rgb(c, o, 255);
                p.stroke = rgb(c, o, 255);
                p.pStroke = rgb(c, o, 255);
                p.text = rgb(c, o, 255);
                p.pText = Color.argb(255, 20, 20, 24);
                p.stickBase = Color.argb((int) (230 * o), 24, 24, 28);
                p.stickBaseHeld = Color.argb((int) (230 * o), 40, 40, 46);
                p.knob = Color.argb((int) (255 * o), 120, 120, 128);
                p.knobHeld = Color.argb((int) (255 * o), 200, 200, 206);
                p.strokeDp = 2f;
                p.cross = true;
                break;
            }
            default: // FUSION
                p.fill = shade(accent, o, 80, 0.12f);
                p.pFill = shade(accent, o, 160, 1f);
                p.stroke = light(accent, o, 150, 0.25f);
                p.pStroke = light(accent, o, 230, 0.7f);
                p.text = light(accent, o, 210, 0.75f);
                p.pText = Color.WHITE;
                p.stickBase = shade(accent, o, 60, 0.12f);
                p.stickBaseHeld = p.pFill;
                p.knob = shade(accent, o, 120, 0.35f);
                p.knobHeld = p.pFill;
                p.strokeDp = 1.5f;
                break;
        }
        return p;
    }

    private void strokeWithGlow(Paint paint, FlatPalette pal, Runnable draw) {
        paint.setStyle(Paint.Style.STROKE);
        if (pal.glow != 0) paint.setShadowLayer(inputControlsView.getSnappingSize() * 0.6f, 0f, 0f, pal.glow);
        draw.run();
        if (pal.glow != 0) paint.setShadowLayer(0f, 0f, 0f, 0);
    }

    /** Flat key: triggers/bumpers are wide pills, everything else keeps its own shape. */
    private void drawFlatKey(Canvas canvas, Paint paint, Rect bb, boolean pill, boolean held, FlatPalette pal) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(held ? pal.pFill : pal.fill);
        if (pill) {
            float corner = bb.height() * 0.5f * 0.55f;
            canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, corner, corner, paint);
            paint.setColor(held ? pal.pStroke : pal.stroke);
            strokeWithGlow(paint, pal, () ->
                    canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, corner, corner, paint));
            return;
        }
        drawGameHubShape(canvas, paint, bb, held ? pal.pFill : pal.fill, true);
        paint.setColor(held ? pal.pStroke : pal.stroke);
        strokeWithGlow(paint, pal, () -> drawGameHubShape(canvas, paint, bb, 0, false));
    }

    /** Flat stick: base disc with a knob that follows the finger. */
    private void drawFlatStick(Canvas canvas, Paint paint, float cx, float cy, float ringRadius, float knobX,
                               float knobY, boolean held, FlatPalette pal) {
        int stroke = held ? pal.pStroke : pal.stroke;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(held ? pal.stickBaseHeld : pal.stickBase);
        canvas.drawCircle(cx, cy, ringRadius, paint);
        paint.setColor(stroke);
        strokeWithGlow(paint, pal, () -> canvas.drawCircle(cx, cy, ringRadius, paint));
        float knobRadius = ringRadius * 0.46f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(held ? pal.knobHeld : pal.knob);
        canvas.drawCircle(knobX, knobY, knobRadius, paint);
        paint.setColor(stroke);
        strokeWithGlow(paint, pal, () -> canvas.drawCircle(knobX, knobY, knobRadius, paint));
    }

    /** Flat D-pad: four round pads with arrows, or one cross of arrows (cross palettes), lit one direction at a time. */
    private void drawFlatDpad(Canvas canvas, Paint paint, float cx, float cy, float radius, FlatPalette pal) {
        if (pal.cross) {
            // GameHubLayout sides are up, down, left, right; states[] is up, right, down, left.
            int[] stateOfSide = {0, 2, 3, 1};
            for (int side = 0; side < 4; side++) {
                boolean held = states[stateOfSide[side]];
                path.reset();
                GameHubLayout.buildDpadArrow(path, side, cx, cy, radius);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(held ? pal.pFill : pal.fill);
                canvas.drawPath(path, paint);
                paint.setColor(held ? pal.pStroke : pal.stroke);
                strokeWithGlow(paint, pal, () -> canvas.drawPath(path, paint));
            }
            return;
        }
        float padRadius = radius / 3f;
        float offset = radius * 2f / 3f;
        float[][] dirs = {{0f, -1f}, {1f, 0f}, {0f, 1f}, {-1f, 0f}};
        for (int i = 0; i < 4; i++) {
            boolean held = states[i];
            float px = cx + dirs[i][0] * offset;
            float py = cy + dirs[i][1] * offset;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(held ? pal.pFill : pal.fill);
            canvas.drawCircle(px, py, padRadius, paint);
            paint.setColor(held ? pal.pStroke : pal.stroke);
            strokeWithGlow(paint, pal, () -> canvas.drawCircle(px, py, padRadius, paint));

            float size = padRadius * 0.34f;
            float fx = dirs[i][0];
            float fy = dirs[i][1];
            path.reset();
            path.moveTo(px + fx * size, py + fy * size);
            path.lineTo(px - fx * size * 0.7f - fy * size, py - fy * size * 0.7f + fx * size);
            path.lineTo(px - fx * size * 0.7f + fy * size, py - fy * size * 0.7f - fx * size);
            path.close();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(held ? pal.pText : pal.text);
            canvas.drawPath(path, paint);
        }
    }

    private void drawGameHubShape(Canvas canvas, Paint paint, Rect bb, int color, boolean fill, Shape overrideShape) {
        if (fill) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
        }
        int snappingSize = inputControlsView.getSnappingSize();
        Shape renderShape = overrideShape != null ? overrideShape : Shape.ROUND_RECT;
        switch (renderShape) {
            case CIRCLE:
                canvas.drawCircle(bb.centerX(), bb.centerY(), Math.min(bb.width(), bb.height()) * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(bb, paint);
                break;
            case ROUND_RECT: {
                float r = bb.height() * 0.5f;
                canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
                break;
            }
            case SQUARE: {
                float r = snappingSize * 0.85f * scale;
                float size = Math.min(bb.width(), bb.height());
                float left = bb.centerX() - size * 0.5f;
                float top = bb.centerY() - size * 0.5f;
                canvas.drawRoundRect(left, top, left + size, top + size, r, r, paint);
                break;
            }
        }
    }

    /** Draw a grid cell using the configured shape (fill or stroke based on paint style) */
    private void drawShapeForCell(Canvas canvas, Paint paint, Rect bb, Shape cellShape) {
        int snappingSize = inputControlsView.getSnappingSize();
        Shape renderShape = cellShape != null ? cellShape : Shape.ROUND_RECT;
        switch (renderShape) {
            case CIRCLE:
                canvas.drawCircle(bb.centerX(), bb.centerY(), Math.min(bb.width(), bb.height()) * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(bb, paint);
                break;
            case ROUND_RECT: {
                float r = Math.min(bb.width(), bb.height()) * 0.25f;
                canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
                break;
            }
            case SQUARE: {
                float r = snappingSize * 0.5f * scale;
                float size = Math.min(bb.width(), bb.height());
                float left = bb.centerX() - size * 0.5f;
                float top = bb.centerY() - size * 0.5f;
                canvas.drawRoundRect(left, top, left + size, top + size, r, r, paint);
                break;
            }
        }
    }

    private Rect getGridCellRect(float left, float top, float right, float bottom) {
        return new Rect(Math.round(left), Math.round(top), Math.round(right), Math.round(bottom));
    }

    private float getGridSpacingPx() {
        return inputControlsView.getSnappingSize() * gridSpacing * scale;
    }

    private int applyGridPressFlash(int cellIndex, int fillColor, boolean pressed, long now) {
        if (cellPressTimes == null || cellIndex < 0 || cellIndex >= cellPressTimes.length || cellPressTimes[cellIndex] <= 0) {
            return fillColor;
        }

        long elapsed = now - cellPressTimes[cellIndex];
        if (elapsed >= 0 && elapsed < GRID_FLASH_DURATION_MS) {
            float flashAlpha = 1.0f - (float)elapsed / GRID_FLASH_DURATION_MS;
            int flashColor = Color.argb((int)(255 * flashAlpha), 255, 255, 255);
            inputControlsView.invalidate();
            return pressed ? blendColors(fillColor, flashColor) : flashColor;
        }

        cellPressTimes[cellIndex] = 0;
        return fillColor;
    }

    private String getGridCellLabel(int cellIndex, int maxLength) {
        if (!isValidBindingIndex(cellIndex)) return "";

        Binding[] combo = getCombo(cellIndex);
        String label;
        if (combo != null && combo.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < combo.length && i < 3; i++) {
                String bindingLabel = getComboBindingLabel(combo[i]);
                if (bindingLabel.isEmpty()) continue;
                if (sb.length() > 0) sb.append("+");
                sb.append(bindingLabel);
            }
            label = sb.toString();
        }
        else {
            label = getCompactBindingLabel(bindings[cellIndex]);
        }

        if (maxLength > 0 && label.length() > maxLength) return label.substring(0, maxLength);
        return label;
    }

    private String getComboBindingLabel(Binding binding) {
        if (binding == null || binding == Binding.NONE) return "";
        if (binding == Binding.KEY_CTRL_L || binding == Binding.KEY_CTRL_R) return "C";
        if (binding == Binding.KEY_SHIFT_L || binding == Binding.KEY_SHIFT_R) return "S";
        if (binding == Binding.KEY_ALT_L || binding == Binding.KEY_ALT_R) return "A";

        String label = getCompactBindingLabel(binding).replace(" ", "");
        return label.length() > 3 ? label.substring(0, 3) : label;
    }

    static String getCompactBindingLabel(Binding binding) {
        return binding.toString().replace("NUMPAD ", "NP").replace("BUTTON ", "");
    }

    /** Blend two ARGB colors with 50% mix (used for press flash) */
    private static int blendColors(int color1, int color2) {
        int a1 = Color.alpha(color1), r1 = Color.red(color1), g1 = Color.green(color1), b1 = Color.blue(color1);
        int a2 = Color.alpha(color2), r2 = Color.red(color2), g2 = Color.green(color2), b2 = Color.blue(color2);
        return Color.argb(
            (a1 + a2) / 2,
            (r1 + r2) / 2,
            (g1 + g2) / 2,
            (b1 + b2) / 2
        );
    }

    private void drawGameHubGlassShape(Canvas canvas, Paint paint, Rect bb, int edgeAlpha) {
        drawGameHubGlassShape(canvas, paint, bb, edgeAlpha, shape);
    }

    private void drawGameHubGlassShape(Canvas canvas, Paint paint, Rect bb, int edgeAlpha, Shape overrideShape) {
        if (edgeAlpha <= 0) return;
        float cx = bb.exactCenterX();
        float cy = bb.exactCenterY();
        float gradR = Math.max(bb.width(), bb.height()) * 0.5f;
        paint.setShader(new RadialGradient(
                cx, cy, gradR,
                Color.argb(0, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0),
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        int snappingSize = inputControlsView.getSnappingSize();
        switch (overrideShape) {
            case CIRCLE:
                canvas.drawCircle(cx, cy, bb.width() * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(bb, paint);
                break;
            case ROUND_RECT: {
                float r = bb.height() * 0.5f;
                canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
                break;
            }
            case SQUARE: {
                float r = snappingSize * 0.85f * scale;
                canvas.drawRoundRect(bb.left, bb.top, bb.right, bb.bottom, r, r, paint);
                break;
            }
        }
        paint.setShader(null);
    }

    private void drawGameHubGlassOnPath(
            Canvas canvas, Paint paint, Path path, float cx, float cy, float gradR, int edgeAlpha) {
        if (edgeAlpha <= 0 || gradR <= 0) return;
        paint.setShader(new RadialGradient(
                cx, cy, gradR,
                Color.argb(0, 0, 0, 0), Color.argb(edgeAlpha, 0, 0, 0),
                Shader.TileMode.CLAMP));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
        paint.setShader(null);
    }

    private void drawOriginalLegacy(Canvas canvas) {
        VisualStyle saved = inputControlsView.getVisualStyle();
        inputControlsView.setVisualStyle(VisualStyle.ORIGINAL);
        draw(canvas);
        inputControlsView.setVisualStyle(saved);
    }

    private boolean shouldDrawCustomIconAsButton() {
        return customIconAsButton
                && iconId >= CustomIconManager.CUSTOM_ICON_ID_OFFSET
                && (type == Type.BUTTON || type == Type.EXPANDABLE_BUTTON);
    }

    static boolean shouldSkipStandardButtonRendering(boolean imageAsButtonRequested, boolean iconDrawn) {
        return imageAsButtonRequested && iconDrawn;
    }

    static float calculateAspectFitScale(
            int sourceWidth, int sourceHeight, float availableWidth, float availableHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || availableWidth <= 0 || availableHeight <= 0) return 0;
        return Math.min(availableWidth / sourceWidth, availableHeight / sourceHeight);
    }

    static int calculateCustomIconAlpha(VisualStyle visualStyle, float overlayOpacity, int primaryColorAlpha) {
        if (visualStyle == VisualStyle.GAMEHUB) {
            return Math.round(Math.max(0f, Math.min(1f, overlayOpacity)) * 255);
        }
        return Math.max(0, Math.min(255, primaryColorAlpha));
    }

    private boolean drawIcon(
            Canvas canvas, float cx, float cy, float width, float height, int iconId, boolean fitBoundingBox) {
        Bitmap icon = inputControlsView.getIcon(iconId);
        if (icon == null) return false;

        Paint paint = inputControlsView.getPaint();
        int previousAlpha = paint.getAlpha();
        ColorFilter previousColorFilter = paint.getColorFilter();
        try {
            boolean pressed = type == Type.BUTTON && states[0];
            boolean customIcon = iconId >= CustomIconManager.CUSTOM_ICON_ID_OFFSET;
            boolean tintIcon = !customIcon || customIconTintEnabled;
            if (tintIcon) {
                int tintColor = pressed ? inputControlsView.getAccentBrightColor() : inputControlsView.getAccentColor();
                if (iconColorFilter == null || iconColorFilterColor != tintColor) {
                    iconColorFilter = new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN);
                    iconColorFilterColor = tintColor;
                }
                paint.setColorFilter(iconColorFilter);
            }
            else paint.setColorFilter(null);

            if (customIcon && (!tintIcon || fitBoundingBox)) {
                paint.setAlpha(calculateCustomIconAlpha(
                        inputControlsView.getVisualStyle(),
                        inputControlsView.getOverlayOpacity(),
                        Color.alpha(inputControlsView.getPrimaryColor())));
            }

            iconSourceRect.set(0, 0, icon.getWidth(), icon.getHeight());
            if (fitBoundingBox) {
                float padding = Math.max(1f, inputControlsView.getSnappingSize() * 0.5f * scale);
                float fitScale = calculateAspectFitScale(
                        icon.getWidth(), icon.getHeight(), width - padding * 2, height - padding * 2);
                if (fitScale <= 0 || !Float.isFinite(fitScale)) return false;
                float halfWidth = icon.getWidth() * fitScale * 0.5f;
                float halfHeight = icon.getHeight() * fitScale * 0.5f;
                iconAspectFitDestinationRect.set(
                        cx - halfWidth, cy - halfHeight,
                        cx + halfWidth, cy + halfHeight);
                canvas.drawBitmap(icon, iconSourceRect, iconAspectFitDestinationRect, paint);
            }
            else {
                int margin = (int)(inputControlsView.getSnappingSize()
                        * (shape == Shape.CIRCLE || shape == Shape.SQUARE ? 2.0f : 1.0f) * scale);
                int halfSize = (int)((Math.min(width, height) - margin) * 0.5f);
                iconDestinationRect.set(
                        (int)(cx - halfSize), (int)(cy - halfSize),
                        (int)(cx + halfSize), (int)(cy + halfSize));
                canvas.drawBitmap(icon, iconSourceRect, iconDestinationRect, paint);
            }
            return true;
        }
        finally {
            paint.setAlpha(previousAlpha);
            paint.setColorFilter(previousColorFilter);
        }
    }

    public boolean shouldDrawExpandedChildren() {
        return type == Type.EXPANDABLE_BUTTON
                && (expanded || (inputControlsView.isEditMode() && selected));
    }

    public void drawExpandedChildren(Canvas canvas) {
        if (!shouldDrawExpandedChildren()) return;
        Paint paint = inputControlsView.getPaint();
        int snappingSize = inputControlsView.getSnappingSize();
        int primaryColor = inputControlsView.getPrimaryColor();
        boolean gameHubStyle = inputControlsView.getVisualStyle() == VisualStyle.GAMEHUB;
        int contentAlpha = gameHubStyle
                ? Math.round(255 * inputControlsView.getOverlayOpacity())
                : 255;
        int fillAlpha = gameHubStyle
                ? Math.round(90 * inputControlsView.getOverlayOpacity())
                : Color.alpha(primaryColor);
        int blackFill = Color.argb(fillAlpha, 0, 0, 0);
        float strokeWidth = snappingSize * 0.25f;
        Rect childBounds = new Rect();

        ControlsIconStyle iconStyle = inputControlsView.getIconStyle();
        boolean flat = iconStyle != ControlsIconStyle.GLASS;
        FlatPalette pal = flat
                ? flatPalette(iconStyle, inputControlsView.getAccentColor(), inputControlsView.getOverlayOpacity(), null)
                : null;

        for (int index = 0; index < bindings.length; index++) {
            getExpandableChildBounds(index, childBounds);
            if (flat) {
                boolean held = states[index];
                drawExpandableShape(canvas, paint, childBounds, held ? pal.pFill : pal.fill, true);
                paint.setStrokeWidth(Math.max(2f, pal.strokeDp * inputControlsView.getResources().getDisplayMetrics().density));
                paint.setColor(held ? pal.pStroke : pal.stroke);
                strokeWithGlow(paint, pal, () ->
                        drawExpandableShape(canvas, paint, childBounds, held ? pal.pStroke : pal.stroke, false));
                String flatLabel = getGridCellLabel(index, 6);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(held ? pal.pText : pal.text);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTextSize(Math.min(
                        getTextSizeForWidth(paint, flatLabel, childBounds.width() - strokeWidth * 2),
                        snappingSize * 1.6f * scale));
                canvas.drawText(flatLabel, childBounds.centerX(),
                        childBounds.centerY() - ((paint.descent() + paint.ascent()) * 0.5f), paint);
                continue;
            }
            int rawAccent = states[index]
                    ? inputControlsView.getAccentBrightColor()
                    : inputControlsView.getAccentColor();
            int accent = Color.argb(contentAlpha,
                    Color.red(rawAccent), Color.green(rawAccent), Color.blue(rawAccent));
            drawExpandableShape(canvas, paint, childBounds, blackFill, true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(accent);
            drawExpandableShape(canvas, paint, childBounds, accent, false);

            String label = getGridCellLabel(index, 6);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(accent);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.min(
                    getTextSizeForWidth(paint, label, childBounds.width() - strokeWidth * 2),
                    snappingSize * 1.6f * scale));
            canvas.drawText(label, childBounds.centerX(),
                    childBounds.centerY() - ((paint.descent() + paint.ascent()) * 0.5f), paint);
        }
    }

    private void drawExpandableShape(Canvas canvas, Paint paint, Rect bounds, int color, boolean fill) {
        paint.setStyle(fill ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setColor(color);
        switch (shape) {
            case CIRCLE:
                canvas.drawCircle(bounds.centerX(), bounds.centerY(), bounds.width() * 0.5f, paint);
                break;
            case RECT:
                canvas.drawRect(bounds, paint);
                break;
            case ROUND_RECT:
            case SQUARE:
                float radius = shape == Shape.ROUND_RECT
                        ? bounds.height() * 0.25f
                        : inputControlsView.getSnappingSize() * 0.75f * scale;
                canvas.drawRoundRect(bounds.left, bounds.top, bounds.right, bounds.bottom, radius, radius, paint);
                break;
        }
    }

    public void getExpandableChildBounds(int index, Rect outBounds) {
        Rect launcher = getBoundingBox();
        int width = launcher.width();
        int height = launcher.height();
        int childWidth = Math.max(1, Math.round(width * expandedChildScale));
        int childHeight = Math.max(1, Math.round(height * expandedChildScale));
        float childCx;
        float childCy;
        if (expandableLayout == ExpandableLayout.RADIAL) {
            float gap = inputControlsView.getSnappingSize() * 0.75f;
            float radius = Math.max(
                    (Math.max(width, height) + Math.max(childWidth, childHeight)) * 0.5f + gap,
                    Math.max(childWidth, childHeight) * bindings.length / (float)(Math.PI * 2.0) + gap);
            double angle = -Math.PI * 0.5 + Math.PI * 2.0 * index / Math.max(1, bindings.length);
            childCx = launcher.centerX() + (float)Math.cos(angle) * radius;
            childCy = launcher.centerY() + (float)Math.sin(angle) * radius;
        } else {
            float gap = inputControlsView.getSnappingSize() * 0.75f;
            boolean vertical = expandableDirection == ExpandableDirection.UP
                    || expandableDirection == ExpandableDirection.DOWN;
            int available = vertical
                    ? (expandableDirection == ExpandableDirection.UP
                        ? launcher.top : inputControlsView.getMaxHeight() - launcher.bottom)
                    : (expandableDirection == ExpandableDirection.LEFT
                        ? launcher.left : inputControlsView.getMaxWidth() - launcher.right);
            int primarySize = vertical ? childHeight : childWidth;
            int perLane = expanded && expandedItemsPerLane > 0
                    ? expandedItemsPerLane
                    : calculateExpandableItemsPerLane(available, primarySize, gap, bindings.length);
            int step = index % perLane + 1;
            int lane = index / perLane;
            int launcherPrimarySize = vertical ? height : width;
            float distance = launcherPrimarySize * 0.5f + gap + primarySize * 0.5f
                    + (primarySize + gap) * (step - 1);
            childCx = launcher.centerX();
            childCy = launcher.centerY();
            switch (expandableDirection) {
                case UP: childCy -= distance; break;
                case RIGHT: childCx += distance; break;
                case DOWN: childCy += distance; break;
                case LEFT: childCx -= distance; break;
            }
            if (lane > 0) {
                if (vertical) {
                    int crossSign = launcher.centerX() < inputControlsView.getMaxWidth() / 2 ? 1 : -1;
                    childCx += crossSign * lane * (childWidth + gap);
                } else {
                    int crossSign = launcher.centerY() < inputControlsView.getMaxHeight() / 2 ? 1 : -1;
                    childCy += crossSign * lane * (childHeight + gap);
                }
            }
        }
        outBounds.set(
                Math.round(childCx - childWidth * 0.5f),
                Math.round(childCy - childHeight * 0.5f),
                Math.round(childCx + childWidth * 0.5f),
                Math.round(childCy + childHeight * 0.5f));
    }

    public int getExpandableChildIndex(float touchX, float touchY) {
        if (type != Type.EXPANDABLE_BUTTON || !expanded) return -1;
        Rect childBounds = new Rect();
        for (int index = bindings.length - 1; index >= 0; index--) {
            getExpandableChildBounds(index, childBounds);
            if (containsShape(childBounds, touchX, touchY)) return index;
        }
        return -1;
    }

    public void setExpanded(boolean expanded) {
        if (type != Type.EXPANDABLE_BUTTON || this.expanded == expanded) return;
        if (!expanded) {
            for (int index = 0; index < activeBindingSlots.length; index++) {
                if (activeBindingSlots[index]) releaseBindingSlot(index);
            }
            Arrays.fill(states, false);
            currentPointerId = -1;
            activeExpandableChild = -1;
            expandedOffsetX = 0;
            expandedOffsetY = 0;
            expandedItemsPerLane = 0;
            expandedChildScale = 1.0f;
        } else {
            expandedOffsetX = 0;
            expandedOffsetY = 0;
            expandedChildScale = 1.0f;
            if (expandableLayout == ExpandableLayout.LIST) {
                Rect launcher = getBoundingBox();
                boolean vertical = expandableDirection == ExpandableDirection.UP
                        || expandableDirection == ExpandableDirection.DOWN;
                int available = vertical
                        ? (expandableDirection == ExpandableDirection.UP
                            ? launcher.top : inputControlsView.getMaxHeight() - launcher.bottom)
                        : (expandableDirection == ExpandableDirection.LEFT
                            ? launcher.left : inputControlsView.getMaxWidth() - launcher.right);
                int primarySize = vertical ? launcher.height() : launcher.width();
                expandedItemsPerLane = calculateExpandableItemsPerLane(
                        available, primarySize, inputControlsView.getSnappingSize() * 0.75f, bindings.length);
            }
            this.expanded = true;
            Rect envelope = calculateExpandedEnvelope();
            float fitScale = Math.min(
                    (float)inputControlsView.getMaxWidth() / Math.max(1, envelope.width()),
                    (float)inputControlsView.getMaxHeight() / Math.max(1, envelope.height()));
            if (fitScale < 1.0f) {
                expandedChildScale = Math.max(0.15f, fitScale * 0.95f);
                if (expandableLayout == ExpandableLayout.LIST) {
                    Rect launcher = getBoundingBox();
                    boolean vertical = expandableDirection == ExpandableDirection.UP
                            || expandableDirection == ExpandableDirection.DOWN;
                    int available = vertical
                            ? (expandableDirection == ExpandableDirection.UP
                                ? launcher.top : inputControlsView.getMaxHeight() - launcher.bottom)
                            : (expandableDirection == ExpandableDirection.LEFT
                                ? launcher.left : inputControlsView.getMaxWidth() - launcher.right);
                    float primarySize = (vertical ? launcher.height() : launcher.width()) * expandedChildScale;
                    expandedItemsPerLane = calculateExpandableItemsPerLane(
                            available, primarySize,
                            inputControlsView.getSnappingSize() * 0.75f, bindings.length);
                }
            }
            calculateExpandedOffset();
        }
        this.expanded = expanded;
        inputControlsView.invalidate();
    }

    private void calculateExpandedOffset() {
        Rect envelope = calculateExpandedEnvelope();
        int minLeft = envelope.left;
        int minTop = envelope.top;
        int maxRight = envelope.right;
        int maxBottom = envelope.bottom;
        if (minLeft < 0) expandedOffsetX = -minLeft;
        if (maxRight + expandedOffsetX > inputControlsView.getMaxWidth()) {
            expandedOffsetX += inputControlsView.getMaxWidth() - (maxRight + expandedOffsetX);
        }
        if (minTop < 0) expandedOffsetY = -minTop;
        if (maxBottom + expandedOffsetY > inputControlsView.getMaxHeight()) {
            expandedOffsetY += inputControlsView.getMaxHeight() - (maxBottom + expandedOffsetY);
        }
    }

    private Rect calculateExpandedEnvelope() {
        Rect launcher = getBoundingBox();
        int minLeft = launcher.left;
        int minTop = launcher.top;
        int maxRight = launcher.right;
        int maxBottom = launcher.bottom;
        Rect childBounds = new Rect();
        for (int index = 0; index < bindings.length; index++) {
            getExpandableChildBounds(index, childBounds);
            minLeft = Math.min(minLeft, childBounds.left);
            minTop = Math.min(minTop, childBounds.top);
            maxRight = Math.max(maxRight, childBounds.right);
            maxBottom = Math.max(maxBottom, childBounds.bottom);
        }
        return new Rect(minLeft, minTop, maxRight, maxBottom);
    }

    public boolean handleExpandableChildDown(int pointerId, float touchX, float touchY) {
        if (currentPointerId != -1) return false;
        int childIndex = getExpandableChildIndex(touchX, touchY);
        if (childIndex < 0) return false;
        currentPointerId = pointerId;
        activeExpandableChild = childIndex;
        states[childIndex] = true;
        pressBindingSlot(childIndex);
        inputControlsView.invalidate();
        return true;
    }

    public boolean handleExpandableChildMove(int pointerId) {
        return pointerId == currentPointerId && activeExpandableChild >= 0;
    }

    public boolean handleExpandableChildUp(int pointerId) {
        if (pointerId != currentPointerId || activeExpandableChild < 0) return false;
        releaseBindingSlot(activeExpandableChild);
        states[activeExpandableChild] = false;
        activeExpandableChild = -1;
        currentPointerId = -1;
        inputControlsView.invalidate();
        return true;
    }

    public JSONObject toJSONObject() {
        try {
            JSONObject elementJSONObject = copyForSerialization(sourceJSONObject);
            elementJSONObject.put("type", type.name());
            elementJSONObject.put("shape", shape.name());

            JSONArray bindingsJSONArray = new JSONArray();
            JSONArray sourceBindings = sourceJSONObject != null ? sourceJSONObject.optJSONArray("bindings") : null;
            for (int i = 0; i < bindings.length; i++) {
                String sourceName = sourceBindings != null ? sourceBindings.optString(i, null) : null;
                bindingsJSONArray.put(getSerializedBindingName(bindings[i], sourceName));
            }

            elementJSONObject.put("bindings", bindingsJSONArray);
            JSONArray blockTouchscreenMouseButtonsJSONArray = new JSONArray();
            for (boolean blocked : blockTouchscreenMouseButtons) {
                blockTouchscreenMouseButtonsJSONArray.put(blocked);
            }
            elementJSONObject.put("blockTouchscreenMouseButtons", blockTouchscreenMouseButtonsJSONArray);
            elementJSONObject.put("scale", Float.valueOf(scale));
            elementJSONObject.put("x", (float)x / inputControlsView.getMaxWidth());
            elementJSONObject.put("y", (float)y / inputControlsView.getMaxHeight());
            elementJSONObject.put("toggleSwitch", toggleSwitch);
            elementJSONObject.put("swipeable", swipeable);
            elementJSONObject.put("text", text);
            elementJSONObject.put("iconId", iconId);
            writeCustomIconOptions(elementJSONObject);

            if (type == Type.RANGE_BUTTON && range != null) {
                elementJSONObject.put("range", range.name());
                if (orientation != 0) elementJSONObject.put("orientation", orientation);
            }
            elementJSONObject.put("deadZone", getDeadZone());
            if (groupId != null) elementJSONObject.put("groupId", groupId);
            if (type == Type.DYNAMIC_STICK) {
                elementJSONObject.put("areaWidthRatio", (float)areaWidth / Math.max(1, inputControlsView.getMaxWidth()));
                elementJSONObject.put("areaHeightRatio", (float)areaHeight / Math.max(1, inputControlsView.getMaxHeight()));
                int shortSide = Math.max(1, Math.min(inputControlsView.getMaxWidth(), inputControlsView.getMaxHeight()));
                elementJSONObject.put("stickRadiusRatio", (float)stickRadius / shortSide);
            }
            if (usesMouseSensitivity(type)) {
                elementJSONObject.put("mouseSensitivity", Float.valueOf(mouseSensitivity));
            }
            if (type == Type.MOUSE_AREA) {
                elementJSONObject.put("areaWidthRatio", (float)areaWidth / Math.max(1, inputControlsView.getMaxWidth()));
                elementJSONObject.put("areaHeightRatio", (float)areaHeight / Math.max(1, inputControlsView.getMaxHeight()));
            }
            if ((type == Type.DYNAMIC_STICK || type == Type.MOUSE_AREA) && customAreaAppearanceEnabled) {
                elementJSONObject.put("customAreaAppearanceEnabled", true);
                elementJSONObject.put("customAreaColor", customAreaColor);
                elementJSONObject.put("customAreaOpacity", Float.valueOf(customAreaOpacity));
            }
            if (type == Type.BUTTON_GRID) {
                elementJSONObject.put("gridRows", getEffectiveGridRows());
                elementJSONObject.put("gridCols", getEffectiveGridCols());
                elementJSONObject.put("gridMultitouchEnabled", gridMultitouchEnabled);
                if (gridCellShape != null && gridCellShape != Shape.ROUND_RECT) {
                    elementJSONObject.put("gridCellShape", gridCellShape.name());
                }
                if (gridSpacing > 0) elementJSONObject.put("gridSpacing", Float.valueOf(gridSpacing));
            }
            if (type == Type.EXPANDABLE_BUTTON) {
                elementJSONObject.put("expandableChildCount", bindings.length);
                elementJSONObject.put("expandableLayout", expandableLayout.name());
                elementJSONObject.put("expandableDirection", expandableDirection.name());
            }
            // Serialize combos if any
            if (comboBindings != null || rawComboBindingNames != null) {
                JSONArray combosArr = new JSONArray();
                int comboCount = bindings.length;
                for (int i = 0; i < comboCount; i++) {
                    Binding[] combo = comboBindings != null && i < comboBindings.length ? comboBindings[i] : null;
                    String[] rawNames = rawComboBindingNames != null && i < rawComboBindingNames.length
                            ? rawComboBindingNames[i]
                            : null;
                    if ((combo == null || combo.length == 0) && (rawNames == null || rawNames.length == 0)) continue;

                    JSONArray entry = new JSONArray();
                    entry.put(i); // index
                    JSONArray keys = new JSONArray();
                    if (rawNames != null) {
                        for (int j = 0; j < Math.min(rawNames.length, MAX_COMBO_BINDINGS); j++) {
                            if (rawNames[j] != null) keys.put(rawNames[j]);
                        }
                    }
                    else {
                        for (int j = 0; j < Math.min(combo.length, MAX_COMBO_BINDINGS); j++) {
                            Binding binding = combo[j];
                            if (binding != null && binding != Binding.NONE) keys.put(binding.name());
                        }
                    }
                    if (keys.length() == 0) continue;
                    entry.put(keys);
                    combosArr.put(entry);
                }
                if (combosArr.length() > 0) elementJSONObject.put("combos", combosArr);
            }
            // Serialize hold key if set
            String sourceHoldKey = sourceJSONObject != null ? sourceJSONObject.optString("holdKey", null) : null;
            if (!holdKeyEdited && sourceHoldKey != null && !Binding.isKnownSerializedName(sourceHoldKey)) {
                elementJSONObject.put("holdKey", sourceHoldKey);
            }
            else if (holdKey != null && holdKey != Binding.NONE) {
                elementJSONObject.put("holdKey", holdKey.name());
            }
            return elementJSONObject;
        }
        catch (JSONException e) {
            return null;
        }
    }

    static JSONObject copyForSerialization(JSONObject sourceJSONObject) throws JSONException {
        JSONObject elementJSONObject = sourceJSONObject != null
                ? new JSONObject(sourceJSONObject.toString())
                : new JSONObject();
        String[] optionalKeys = {
                "range", "orientation", "groupId", "areaWidthRatio", "areaHeightRatio",
                "stickRadiusRatio", "areaWidth", "areaHeight", "stickRadius",
                "mouseSensitivity", "gridRows", "gridCols", "gridCellShape", "gridSpacing",
                "gridMultitouchEnabled", "combos", "holdKey",
                "blockTouchscreenMouseButtons",
                "expandableChildCount", "expandableLayout", "expandableDirection",
                "customAreaAppearanceEnabled", "customAreaColor", "customAreaOpacity",
                "customIconTintEnabled", "customIconAsButton"
        };
        for (String key : optionalKeys) elementJSONObject.remove(key);
        return elementJSONObject;
    }

    static String getSerializedBindingName(Binding binding, String sourceName) {
        return binding == Binding.NONE && sourceName != null && !Binding.isKnownSerializedName(sourceName)
                ? sourceName
                : binding.name();
    }

    public boolean containsPoint(float x, float y) {
        Rect bounds = getBoundingBox();
        return type == Type.EXPANDABLE_BUTTON
                ? containsShape(bounds, x, y)
                : bounds.contains((int)(x + 0.5f), (int)(y + 0.5f));
    }

    private boolean containsShape(Rect bounds, float touchX, float touchY) {
        if (!bounds.contains(Math.round(touchX), Math.round(touchY))) return false;
        if (shape == Shape.RECT) return true;
        if (shape == Shape.CIRCLE) {
            float radius = bounds.width() * 0.5f;
            float dx = touchX - bounds.centerX();
            float dy = touchY - bounds.centerY();
            return dx * dx + dy * dy <= radius * radius;
        }
        float radius = shape == Shape.ROUND_RECT
                ? bounds.height() * 0.25f
                : inputControlsView.getSnappingSize() * 0.75f * scale;
        float innerLeft = bounds.left + radius;
        float innerRight = bounds.right - radius;
        float innerTop = bounds.top + radius;
        float innerBottom = bounds.bottom - radius;
        if (touchX >= innerLeft && touchX <= innerRight) return true;
        if (touchY >= innerTop && touchY <= innerBottom) return true;
        float cornerX = touchX < innerLeft ? innerLeft : innerRight;
        float cornerY = touchY < innerTop ? innerTop : innerBottom;
        float dx = touchX - cornerX;
        float dy = touchY - cornerY;
        return dx * dx + dy * dy <= radius * radius;
    }

    private boolean isKeepButtonPressedAfterMinTime() {
        Binding binding = getBindingAt(0);
        return !toggleSwitch && (binding == Binding.GAMEPAD_BUTTON_L3 || binding == Binding.GAMEPAD_BUTTON_R3);
    }

    public boolean isAndroidKeyboardButton() {
        for (int index = 0; index < Math.min(2, bindings.length); index++) {
            if (getBindingAt(index) == Binding.SHOW_ANDROID_KEYBOARD) return true;
            Binding[] combo = getCombo(index);
            if (combo == null) continue;
            for (Binding binding : combo) {
                if (binding == Binding.SHOW_ANDROID_KEYBOARD) return true;
            }
        }
        return false;
    }

    public boolean handleTouchDown(int pointerId, float x, float y) {
        if (type == Type.BUTTON_GRID && gridMultitouchEnabled) {
            return handleGridMultitouchDown(pointerId, x, y);
        }
        if (currentPointerId == -1 && containsPoint(x, y)) {
            currentPointerId = pointerId;
            if (type == Type.BUTTON) {
                states[0] = true;
                inputControlsView.invalidate();
                if (isKeepButtonPressedAfterMinTime()) touchTime = System.currentTimeMillis();
                if (!toggleSwitch || !selected || isAndroidKeyboardButton()) {
                    pressBindingSlot(0);
                    pressBindingSlot(1);
                }
                return true;
            }
            else if (type == Type.RANGE_BUTTON) {
                scroller.handleTouchDown(x, y);
                return true;
            }
            else if (type == Type.DYNAMIC_STICK) {
                // Hold key stays pressed while stick is active
                pressHoldKey();
                // Stick appears at touch point within the detection area
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.set(x, y);
                stickVisible = true;
                // Jump visual position to target on initial touch (no interpolation for first frame)
                visualStickX = x;
                visualStickY = y;
                lastFingerX = x;
                lastFingerY = y;
                // Bindings are W/A/S/D style directional
                states[0] = false; states[1] = false; states[2] = false; states[3] = false;
                inputControlsView.invalidate();
                return true;
            }
            else if (type == Type.MOUSE_AREA) {
                // Hold key stays pressed while mouse area is active
                pressHoldKey();
                // Start mouse tracking from this position
                if (mouseAreaLastPos == null) mouseAreaLastPos = new PointF();
                mouseAreaLastPos.set(x, y);
                return true;
            }
            else if (type == Type.BUTTON_GRID) {
                // Determine which grid cell was touched
                int cellIndex = getGridCellIndex(x, y);
                if (isValidBindingIndex(cellIndex)) {
                    states[cellIndex] = true;
                    // Record press time for flash animation
                    setCellPressTime(cellIndex, System.currentTimeMillis());
                    pressBindingSlot(cellIndex);
                    inputControlsView.invalidate();
                }
                return true;
            }
            else {
                if (type == Type.TRACKPAD || type == Type.STICK) {
                    // Hold key stays pressed while trackpad/stick is active
                    pressHoldKey();
                }
                if (type == Type.TRACKPAD) {
                    if (currentPosition == null) currentPosition = new PointF();
                    currentPosition.set(x, y);
                }
                return handleTouchMove(pointerId, x, y);
            }
        }
        else return false;
    }

    /** Calculate grid cell index from touch coordinates */
    private int getGridCellIndex(float x, float y) {
        int rows = getEffectiveGridRows();
        int cols = getEffectiveGridCols();
        Rect box = getBoundingBox();
        if (box.width() <= 0 || box.height() <= 0) return -1;
        if (x < box.left || x >= box.right || y < box.top || y >= box.bottom) return -1;
        float gap = getGridSpacingPx();
        float cellW = (box.width() - gap * (cols - 1)) / cols;
        float cellH = (box.height() - gap * (rows - 1)) / rows;
        int col = Math.min(cols - 1, (int)((x - box.left + gap * 0.5f) / (cellW + gap)));
        int row = Math.min(rows - 1, (int)((y - box.top + gap * 0.5f) / (cellH + gap)));
        return row * cols + col;
    }

    private boolean handleGridMultitouchDown(int pointerId, float x, float y) {
        if (!containsPoint(x, y)) return false;
        int cell = getGridCellIndex(x, y);
        if (!isValidBindingIndex(cell)) cell = ButtonGridTouchState.NO_CELL;
        if (!buttonGridTouchState.trackPointer(pointerId, cell)) return false;
        if (cell != ButtonGridTouchState.NO_CELL && buttonGridTouchState.getCellOwnerCount(cell) == 1) {
            states[cell] = true;
            setCellPressTime(cell, System.currentTimeMillis());
            pressBindingSlot(cell);
            inputControlsView.invalidate();
        }
        return true;
    }

    private boolean handleGridMultitouchMove(int pointerId, float x, float y) {
        if (!buttonGridTouchState.isPointerTracked(pointerId)) return false;
        int oldCell = buttonGridTouchState.getPointerCell(pointerId);
        int newCell = getGridCellIndex(x, y);
        if (!isValidBindingIndex(newCell)) newCell = ButtonGridTouchState.NO_CELL;
        if (newCell != oldCell && buttonGridTouchState.movePointer(pointerId, newCell)) {
            if (oldCell != ButtonGridTouchState.NO_CELL
                    && buttonGridTouchState.getCellOwnerCount(oldCell) == 0) {
                states[oldCell] = false;
                releaseBindingSlot(oldCell);
            }
            if (newCell != ButtonGridTouchState.NO_CELL
                    && buttonGridTouchState.getCellOwnerCount(newCell) == 1) {
                states[newCell] = true;
                setCellPressTime(newCell, System.currentTimeMillis());
                pressBindingSlot(newCell);
            }
        }
        inputControlsView.invalidate();
        return true;
    }

    private boolean handleGridMultitouchUp(int pointerId) {
        if (!buttonGridTouchState.isPointerTracked(pointerId)) return false;
        int cell = buttonGridTouchState.getPointerCell(pointerId);
        buttonGridTouchState.untrackPointer(pointerId);
        if (cell != ButtonGridTouchState.NO_CELL && buttonGridTouchState.getCellOwnerCount(cell) == 0) {
            states[cell] = false;
            releaseBindingSlot(cell);
        }
        inputControlsView.invalidate();
        return true;
    }

    /** Press all keys in a slot's combo (or single binding). */
    private void pressBindingSlot(int index) {
        handleBindingSlot(index, true, 0);
    }

    /** Release all keys in a slot's combo (or single binding). */
    private void releaseBindingSlot(int index) {
        handleBindingSlot(index, false, 0);
    }

    private void handleBindingSlot(int index, boolean state, float value) {
        if (!isValidBindingIndex(index)) return;
        boolean wasActive = activeBindingSlots[index];
        boolean analogUpdate = state && wasActive && slotContainsMouseMove(index);
        if (state == wasActive && !analogUpdate) return;
        if (!analogUpdate) activeBindingSlots[index] = state;
        if (hasCombo(index)) {
            for (Binding b : getEffectiveBindingsForSlot(index)) {
                // Suppress per-binding gamepad state sends; batch them at the end
                // so the game receives the full combo as one atomic update.
                if (b.isMouseMove()) {
                    inputControlsView.handleMouseMoveInput(this, index, b, state, value);
                } else if (!analogUpdate) {
                    inputControlsView.handleCountedInputEvent(b, state, value, false);
                }
            }
            inputControlsView.sendGamepadUpdate();
        } else {
            Binding binding = getBindingAt(index);
            if (binding.isMouseMove()) inputControlsView.handleMouseMoveInput(this, index, binding, state, value);
            else if (analogUpdate) inputControlsView.handleInputEvent(binding, true, value);
            else inputControlsView.handleCountedInputEvent(binding, state, value, true);
        }
    }

    private boolean slotContainsMouseMove(int index) {
        if (getBindingAt(index).isMouseMove()) return true;
        Binding[] combo = getCombo(index);
        if (combo == null) return false;
        for (Binding binding : combo) if (binding.isMouseMove()) return true;
        return false;
    }

    private void pressHoldKey() {
        Binding holdKey = getHoldKey();
        if (holdKeyActive || holdKey == Binding.NONE) return;
        holdKeyActive = true;
        inputControlsView.handleCountedInputEvent(holdKey, true, 0, true);
    }

    private void releaseHoldKey() {
        if (!holdKeyActive) return;
        holdKeyActive = false;
        inputControlsView.handleCountedInputEvent(getHoldKey(), false, 0, true);
    }

    boolean usesUnifiedGamepadStick() {
        return (type == Type.STICK || type == Type.DYNAMIC_STICK || type == Type.TRACKPAD)
                && InputControlsView.isThumbBinding(getBindingAt(0));
    }

    private void releaseUnifiedGamepadStick() {
        if (usesUnifiedGamepadStick()) inputControlsView.handleStickInput(this, getBindingAt(0), 0, 0);
    }

    public void releaseActiveInputs() {
        if (type == Type.EXPANDABLE_BUTTON) setExpanded(false);
        if (activeBindingSlots != null) {
            for (int i = 0; i < activeBindingSlots.length; i++) {
                if (activeBindingSlots[i]) releaseBindingSlot(i);
            }
        }
        if (states != null) {
            Arrays.fill(states, false);
        }
        buttonGridTouchState.clear();
        if (type == Type.BUTTON && selected) {
            releaseBindingSlot(0);
            releaseBindingSlot(1);
            selected = false;
        }

        releaseUnifiedGamepadStick();
        releaseHoldKey();
        if (type == Type.RANGE_BUTTON && scroller != null) {
            scroller.releaseActiveInputs();
        }
        if (type == Type.DYNAMIC_STICK) {
            stickVisible = false;
            visualStickX = 0;
            visualStickY = 0;
            lastFingerX = 0;
            lastFingerY = 0;
        }

        currentPointerId = -1;
        currentPosition = null;
        mouseAreaLastPos = null;
        touchTime = null;
        inputControlsView.invalidate();
    }

    public boolean handleTouchMove(int pointerId, float x, float y) {
        if (type == Type.BUTTON_GRID && gridMultitouchEnabled) {
            return handleGridMultitouchMove(pointerId, x, y);
        }
        if (pointerId == currentPointerId && type == Type.BUTTON) {
            // Legacy buttons ignore moves (pressed on DOWN, held until UP). A swipeable button
            // instead releases itself the moment the owning finger slides off its bounds, so the
            // finger can chain onto the next swipe target (handled in InputControlsView). Gated by
            // BOTH the per-element flag AND the live Buttons category toggle; either off -> legacy.
            if (!swipeable || !inputControlsView.isSwipeButtonsEnabled()) return false;
            if (!containsPoint(x, y)) handleTouchUp(pointerId);
            return true;
        }
        if (pointerId == currentPointerId && (type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.DYNAMIC_STICK || type == Type.MOUSE_AREA || type == Type.BUTTON_GRID)) {
            float deltaX, deltaY;
            Rect boundingBox = getBoundingBox();
            float radius = boundingBox.width() * 0.5f;
            TouchpadView touchpadView =  inputControlsView.getTouchpadView();

            if (type == Type.TRACKPAD) {
                if (currentPosition == null) currentPosition = new PointF();
                float[] deltaPoint = touchpadView.computeDeltaPoint(currentPosition.x, currentPosition.y, x, y);
                deltaX = deltaPoint[0];
                deltaY = deltaPoint[1];
                currentPosition.set(x, y);
            }
            else {
                float localX = x - boundingBox.left;
                float localY = y - boundingBox.top;
                float offsetX = localX - radius;
                float offsetY = localY - radius;

                float distance = Mathf.lengthSq(radius - localX, radius - localY);
                if (distance > radius * radius) {
                    float angle = (float)Math.atan2(offsetY, offsetX);
                    offsetX = (float)(Math.cos(angle) * radius);
                    offsetY = (float)(Math.sin(angle) * radius);
                }

                deltaX = Mathf.clamp(offsetX / radius, -1, 1);
                deltaY = Mathf.clamp(offsetY / radius, -1, 1);
            }

            if (type == Type.DYNAMIC_STICK) {
                // Store finger position for thumb animation
                lastFingerX = x;
                lastFingerY = y;
                // Calculate delta from initial touch position (currentPosition)
                if (currentPosition == null) currentPosition = new PointF();
                float stickCx = currentPosition.x;
                float stickCy = currentPosition.y;
                float sRadius = stickRadius > 0 ? stickRadius : 120;
                float dx = x - stickCx;
                float dy = y - stickCy;
                float dist = (float)Math.sqrt(dx * dx + dy * dy);
                if (dist > sRadius) {
                    dx = dx / dist * sRadius;
                    dy = dy / dist * sRadius;
                    dist = sRadius;
                }
                float normX = dist > 0 ? dx / sRadius : 0;
                float normY = dist > 0 ? dy / sRadius : 0;

                Binding firstBinding = getBindingAt(0);
                if (usesUnifiedGamepadStick()) {
                    float magnitude = (float)Math.sqrt(normX * normX + normY * normY);
                    float finalX = 0, finalY = 0;
                    if (magnitude > deadZone) {
                        float scale = Math.min(1.0f, (magnitude - deadZone) * STICK_SENSITIVITY);
                        finalX = (normX / magnitude) * scale;
                        finalY = (normY / magnitude) * scale;
                    }
                    inputControlsView.handleStickInput(this, firstBinding, finalX, finalY);
                    for (byte i = 0; i < 4; i++) this.states[i] = true;
                } else {
                    final boolean[] st = {normY < -deadZone, normX > deadZone,
                                          normY > deadZone, normX < -deadZone};
                    for (byte i = 0; i < 4; i++) {
                        float value = i == 1 || i == 3 ? normX : normY;
                        Binding binding = getBindingAt(i);
                        boolean state = binding.isMouseMove() ? (st[i] || st[(i+2)%4]) : st[i];
                        handleBindingSlot(i, state, value);
                        this.states[i] = state;
                    }
                }
                inputControlsView.invalidate();
                return true;
            }

            if (type == Type.MOUSE_AREA) {
                if (mouseAreaLastPos == null) mouseAreaLastPos = new PointF();
                float rawDx = (x - mouseAreaLastPos.x) * mouseSensitivity;
                float rawDy = (y - mouseAreaLastPos.y) * mouseSensitivity;
                mouseAreaLastPos.set(x, y);
                XServer xServer = inputControlsView.getXServer();
                if (xServer != null) {
                    if (xServer.isRelativeMouseMovement())
                        xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, (int)rawDx, (int)rawDy, 0);
                    else
                        xServer.injectPointerMoveDelta((int)rawDx, (int)rawDy);
                }
                return true;
            }

            if (type == Type.BUTTON_GRID) {
                int newCell = getGridCellIndex(x, y);
                // Find which cell was previously pressed
                int oldCell = -1;
                for (int i = 0; i < states.length; i++) {
                    if (states[i]) { oldCell = i; break; }
                }
                if (newCell != oldCell) {
                    // Release old cell
                    if (isValidBindingIndex(oldCell)) {
                        states[oldCell] = false;
                        releaseBindingSlot(oldCell);
                    }
                    // Press new cell
                    if (isValidBindingIndex(newCell)) {
                        states[newCell] = true;
                        setCellPressTime(newCell, System.currentTimeMillis());
                        pressBindingSlot(newCell);
                    }
                }
                inputControlsView.invalidate();
                return true;
            }

            if (type == Type.STICK) {
                if (currentPosition == null) currentPosition = new PointF();
                currentPosition.x = boundingBox.left + deltaX * radius + radius;
                currentPosition.y = boundingBox.top + deltaY * radius + radius;

                // Directional thumb bindings use unified axes; all others dispatch per slot.
                Binding firstBinding = getBindingAt(0);
                if (usesUnifiedGamepadStick()) {
                    // Use radial deadzone to prevent angle snapping
                    float magnitude = (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY);

                    float finalX = 0;
                    float finalY = 0;

                    if (magnitude > deadZone) {
                        // Normalize and apply sensitivity
                        float normalizedX = deltaX / magnitude;
                        float normalizedY = deltaY / magnitude;

                        // Scale magnitude by sensitivity, respecting deadzone
                        float scaledMagnitude = Math.max(0, magnitude - deadZone) * STICK_SENSITIVITY;
                        scaledMagnitude = Math.min(scaledMagnitude, 1.0f);

                        finalX = normalizedX * scaledMagnitude;
                        finalY = normalizedY * scaledMagnitude;
                    }

                    // Use unified stick input method - sets both X and Y together
                    inputControlsView.handleStickInput(this, firstBinding, finalX, finalY);

                    // Mark all directions as active for proper release handling
                    for (byte i = 0; i < 4; i++) {
                        this.states[i] = true;
                    }
                } else {
                    // Fallback to per-direction handling for mouse/keyboard bindings
                    final boolean[] states = {deltaY < -deadZone, deltaX > deadZone, deltaY > deadZone, deltaX < -deadZone};
                    for (byte i = 0; i < 4; i++) {
                        float value = i == 1 || i == 3 ? deltaX : deltaY;
                        Binding binding = getBindingAt(i);
                        boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                        handleBindingSlot(i, state, value);
                        this.states[i] = state;
                    }
                }

                inputControlsView.invalidate();
            }
            else if (type == Type.TRACKPAD) {
                // Directional thumb bindings use unified axes; all others dispatch per slot.
                Binding firstBinding = getBindingAt(0);
                if (usesUnifiedGamepadStick()) {
                    // Apply interpolation to both axes
                    if (interpolator == null) interpolator = new CubicBezierInterpolator();
                    interpolator.set(0.075f, 0.95f, 0.45f, 0.95f);
                    
                    float valueX = scaleTrackpadDelta(deltaX, mouseSensitivity);
                    float valueY = scaleTrackpadDelta(deltaY, mouseSensitivity);
                    if (Math.abs(valueX) > TRACKPAD_ACCELERATION_THRESHOLD) valueX *= STICK_SENSITIVITY;
                    if (Math.abs(valueY) > TRACKPAD_ACCELERATION_THRESHOLD) valueY *= STICK_SENSITIVITY;
                    
                    float interpX = interpolator.getInterpolation(Math.min(1.0f, Math.abs(valueX / TRACKPAD_MAX_SPEED)));
                    float interpY = interpolator.getInterpolation(Math.min(1.0f, Math.abs(valueY / TRACKPAD_MAX_SPEED)));
                    
                    float finalX = Mathf.clamp(interpX * Mathf.sign(valueX), -1, 1);
                    float finalY = Mathf.clamp(interpY * Mathf.sign(valueY), -1, 1);
                    
                    // Use unified stick input
                    inputControlsView.handleStickInput(this, firstBinding, finalX, finalY);
                    
                    // Mark all as active
                    for (byte i = 0; i < 4; i++) {
                        this.states[i] = true;
                    }
                } else {
                    // Per-direction handling for mouse, keyboard, and non-thumb gamepad bindings.
                    final boolean[] states = {deltaY <= -TRACKPAD_MIN_SPEED, deltaX >= TRACKPAD_MIN_SPEED, deltaY >= TRACKPAD_MIN_SPEED, deltaX <= -TRACKPAD_MIN_SPEED};
                    int cursorDx = 0;
                    int cursorDy = 0;

                    for (byte i = 0; i < 4; i++) {
                        float value = scaleTrackpadDelta(i == 1 || i == 3 ? deltaX : deltaY, mouseSensitivity);
                        Binding binding = getBindingAt(i);
                        if (Math.abs(value) > TouchpadView.CURSOR_ACCELERATION_THRESHOLD) value *= TouchpadView.CURSOR_ACCELERATION;
                        if (hasCombo(i)) {
                            handleBindingSlot(i, states[i], value);
                            this.states[i] = states[i];
                        }
                        else if (binding == Binding.MOUSE_MOVE_LEFT || binding == Binding.MOUSE_MOVE_RIGHT) {
                            cursorDx = Mathf.roundPoint(value);
                        }
                        else if (binding == Binding.MOUSE_MOVE_UP || binding == Binding.MOUSE_MOVE_DOWN) {
                            cursorDy = Mathf.roundPoint(value);
                        }
                        else {
                            handleBindingSlot(i, states[i], value);
                            this.states[i] = states[i];
                        }
                    }

                    if (cursorDx != 0 || cursorDy != 0)  {
                        XServer xServer = inputControlsView.getXServer();
                        if (xServer.isRelativeMouseMovement())
                            xServer.getWinHandler().mouseEvent(MouseEventFlags.MOVE, cursorDx, cursorDy, 0);
                        else
                            inputControlsView.getXServer().injectPointerMoveDelta(cursorDx, cursorDy);
                    }
                }
            }
            else {
                final boolean[] states = {deltaY < -deadZone, deltaX > deadZone, deltaY > deadZone, deltaX < -deadZone};

                for (byte i = 0; i < 4; i++) {
                    float value = i == 1 || i == 3 ? deltaX : deltaY;
                    Binding binding = getBindingAt(i);
                    boolean state = binding.isMouseMove() ? (states[i] || states[(i+2)%4]) : states[i];
                    handleBindingSlot(i, state, value);
                    this.states[i] = state;
                }
                inputControlsView.invalidate();
            }

            return true;
        }
        else if (pointerId == currentPointerId && type == Type.RANGE_BUTTON) {
            scroller.handleTouchMove(x, y);
            return true;
        }
        else return false;
    }

    public boolean handleTouchUp(int pointerId) {
        if (type == Type.BUTTON_GRID && gridMultitouchEnabled) {
            return handleGridMultitouchUp(pointerId);
        }
        if (pointerId == currentPointerId) {
            if (type == Type.BUTTON) {
                states[0] = false;
                inputControlsView.invalidate();
                if (isKeepButtonPressedAfterMinTime() && touchTime != null) {
                    selected = (System.currentTimeMillis() - (long)touchTime) > BUTTON_MIN_TIME_TO_KEEP_PRESSED;
                    if (!selected) {
                        releaseBindingSlot(0);
                        releaseBindingSlot(1);
                    }
                    touchTime = null;
                }
                else if (!toggleSwitch || selected || isAndroidKeyboardButton()) {
                    releaseBindingSlot(0);
                    releaseBindingSlot(1);
                }

                if (toggleSwitch && !isAndroidKeyboardButton()) {
                    selected = !selected;
                }
            }
            else if (type == Type.RANGE_BUTTON || type == Type.D_PAD || type == Type.STICK || type == Type.TRACKPAD || type == Type.DYNAMIC_STICK || type == Type.MOUSE_AREA || type == Type.BUTTON_GRID) {
                for (int i = 0; i < states.length; i++) {
                    if (states[i]) {
                        releaseBindingSlot(i);
                    }
                    states[i] = false;
                }

                // Release hold key for movement controls
                releaseUnifiedGamepadStick();
                releaseHoldKey();

                if (type == Type.RANGE_BUTTON) {
                    scroller.handleTouchUp();
                }
                else if (type == Type.D_PAD || type == Type.STICK || type == Type.DYNAMIC_STICK) {
                    if (type == Type.DYNAMIC_STICK) {
                        stickVisible = false;
                        visualStickX = 0;
                        visualStickY = 0;
                        lastFingerX = 0;
                        lastFingerY = 0;
                    }
                    inputControlsView.invalidate();
                }
                else if (type == Type.MOUSE_AREA) {
                    mouseAreaLastPos = null;
                }
                else if (type == Type.BUTTON_GRID) {
                    inputControlsView.invalidate();
                }

                if (currentPosition != null) currentPosition = null;
            }
            currentPointerId = -1;
            return true;
        }
        return false;
    }

    public PointF getCurrentPosition() {
        if (currentPosition == null) {
            currentPosition = new PointF(x, y); // Initialize to the center (same as outer circle)
        }
        return currentPosition;
    }

    static boolean usesMouseSensitivity(Type type) {
        return type == Type.TRACKPAD || type == Type.MOUSE_AREA;
    }

    static float scaleTrackpadDelta(float delta, float sensitivity) {
        return delta * sensitivity;
    }

    // New setter for current position to allow resetting
    public void setCurrentPosition(float x, float y) {
        if (currentPosition == null) {
            currentPosition = new PointF();
        }
        currentPosition.set(x, y);
        // Optionally invalidate the view to trigger a redraw
        inputControlsView.invalidate();
    }
}
