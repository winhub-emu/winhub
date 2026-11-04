package com.winhub.hernandez.widget;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.SystemClock;

import com.winhub.hernandez.core.GPUInformation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Shared live-metric collector for every performance HUD overlay. This is the single, device-complete
 * implementation of the sysfs / Android readers — no overlay should re-read sysfs itself, so the GPU
 * discovery / thermal / battery logic never diverges.
 *
 * <p>Ported and hardened from GameNative's {@code PerformanceHudView} readers, which are far more
 * device-complete than the two-fixed-path versions previously inlined in the classic overlays:
 * <ul>
 *   <li>GPU load — ~10 static candidates plus a dynamic {@code /sys/class/devfreq} +
 *       {@code /sys/devices/virtual/devfreq} walk for gpu/mali/g3d/kgsl nodes; handles gpubusy
 *       (busy/total), Mali gpuinfo (delta-ms/wall-ms, needs state between calls) and generic percent
 *       nodes. Discovery cached.</li>
 *   <li>CPU usage — {@code /proc/stat} aggregate delta with a scaling-frequency fallback for devices
 *       where {@code /proc/stat} is restricted.</li>
 *   <li>Thermal — prioritized CPU zone discovery (cpu-silicon &gt; cpu-0 &gt; cpu &gt; soc &gt;
 *       s5p-tmu &gt; cputop &gt; tsens &gt; cluster &gt; big/little) and a separate prioritized GPU zone
 *       discovery plus kgsl/mali direct paths. milli-°C normalization + 1..150 sanity clamp.</li>
 *   <li>Battery — % capacity, power W, runtime-left estimate (charge_counter / current_now, smoothed),
 *       battery temperature, plus the dual-battery current-sum for the classic overlays.</li>
 * </ul>
 *
 * <p>Every getter is null / absent-safe: nullable getters return {@code null} when a metric cannot be
 * read (the overlay hides that row, matching GameNative behavior); the legacy non-null getters return
 * 0 in that case. Not thread-safe; call from a single refresh thread per instance.
 */
public class HudMetrics {
    private final Context context;

    public HudMetrics(Context context) { this.context = context; }

    // =======================================================================
    // CPU usage
    // =======================================================================
    private Long lastCpuTotal = null;
    private Long lastCpuIdle = null;

    /**
     * Overall device CPU usage 0..100 computed from the /proc/stat delta since the last call, with a
     * scaling-frequency fallback. Returns null only when neither source is readable.
     */
    public Integer getCpuUsagePercent() {
        String line = readFirstLine("/proc/stat");
        if (line != null) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 5 && "cpu".equals(parts[0])) {
                long total = 0, idle = 0, iowait = 0;
                boolean ok = true;
                for (int i = 1; i < parts.length; i++) {
                    try {
                        long v = Long.parseLong(parts[i]);
                        total += v;
                        if (i == 4) idle = v;
                        else if (i == 5) iowait = v;
                    } catch (NumberFormatException e) { ok = false; break; }
                }
                if (ok) {
                    long idleTotal = idle + iowait;
                    Long prevTotal = lastCpuTotal, prevIdle = lastCpuIdle;
                    lastCpuTotal = total;
                    lastCpuIdle = idleTotal;
                    if (prevTotal != null && prevIdle != null) {
                        long dTotal = total - prevTotal;
                        long dIdle = idleTotal - prevIdle;
                        if (dTotal > 0) {
                            long usage = (Math.max(0, dTotal - dIdle) * 100L) / dTotal;
                            return clampPercent((int) usage);
                        }
                    }
                    // First sample seeds the delta — fall through to the frequency estimate so the
                    // very first read still returns something.
                }
            }
        }
        return readCpuUsagePercentFromFrequency();
    }

    private Integer readCpuUsagePercentFromFrequency() {
        long currentTotal = 0, maxTotal = 0;
        int cores = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < cores; i++) {
            Long cur = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            Long max = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (cur != null && max != null && max > 0L) {
                currentTotal += Math.max(0, Math.min(cur, max));
                maxTotal += max;
            }
        }
        if (maxTotal <= 0L) return null;
        return clampPercent((int) ((currentTotal * 100L) / maxTotal));
    }

    /** Legacy non-null accessor (0 when unavailable). Kept for {@code PerfHudView}. */
    public float getCPUUsage() {
        Integer p = getCpuUsagePercent();
        return p == null ? 0f : p;
    }

    // =======================================================================
    // GPU load
    // =======================================================================
    private List<String> gpuUsagePathsCache = null;
    private Long lastMaliGpuInfoMs = null;
    private long lastMaliGpuInfoWallMs = 0L;

    /** GPU utilisation 0..100, or null when no readable source is found on this device. */
    public Integer getGpuUsagePercent() {
        for (String path : discoverGpuUsagePaths()) {
            Integer p = readGpuUsageSample(path);
            if (p != null) return p;
        }
        // Every sysfs node failed: on a CSF-generation Mali, read the GPU's hardware counters instead.
        return MaliGpuCounters.usagePercent();
    }

    /** Legacy non-null accessor (0 when unavailable). Kept for {@code PerfHudView}. */
    public int getGPULoad() {
        Integer p = getGpuUsagePercent();
        return p == null ? 0 : p;
    }

    private List<String> discoverGpuUsagePaths() {
        if (gpuUsagePathsCache != null) return gpuUsagePathsCache;
        LinkedHashSet<String> candidates = new LinkedHashSet<>();

        for (String p : GPU_USAGE_STATIC_PATHS) {
            if (new File(p).canRead()) candidates.add(p);
        }

        // Vendor platform nodes with hashed/addressed names we can't hard-code (e.g.
        // /sys/devices/platform/13000000.mali/utilisation, .../gpusysfs/gpu_busy, panfrost, sgpu).
        File platformDir = new File("/sys/devices/platform");
        File[] platformNodes = platformDir.listFiles(File::isDirectory);
        if (platformNodes != null) {
            for (File node : platformNodes) {
                String name = node.getName().toLowerCase(Locale.US);
                boolean looksLikeGpu = false;
                for (String t : GPU_NODE_TOKENS) {
                    if (name.contains(t)) { looksLikeGpu = true; break; }
                }
                if (!looksLikeGpu) continue;
                for (String fileName : GPU_USAGE_FILES) {
                    File f = new File(node, fileName);
                    if (f.canRead()) candidates.add(f.getPath());
                }
            }
        }

        File[] devfreqRoots = {
            new File("/sys/class/devfreq"),
            new File("/sys/devices/virtual/devfreq"),
        };
        for (File root : devfreqRoots) {
            if (!root.isDirectory()) continue;
            File[] nodeDirs = root.listFiles(File::isDirectory);
            if (nodeDirs == null) continue;
            for (File node : nodeDirs) {
                String nodePath = node.getPath().toLowerCase(Locale.US);
                boolean looksLikeGpu = false;
                for (String t : GPU_NODE_TOKENS) {
                    if (nodePath.contains(t)) { looksLikeGpu = true; break; }
                }
                for (String fileName : GPU_USAGE_FILES) {
                    File f = new File(node, fileName);
                    if (!f.canRead()) continue;
                    if (looksLikeGpu || fileName.equals("gpu_busy_percentage")
                            || fileName.equals("gpu_busy_percent") || fileName.equals("gpuinfo")) {
                        candidates.add(f.getPath());
                    }
                }
            }
        }

        for (String p : GPU_USAGE_LATE_PATHS) {
            if (new File(p).canRead()) candidates.add(p);
        }

        gpuUsagePathsCache = new ArrayList<>(candidates);
        return gpuUsagePathsCache;
    }

    // Ordered, world-readable-when-present GPU utilisation nodes across the vendors we've seen:
    // Adreno KGSL, Mali (misc + Exynos vendor), PowerVR, and amdgpu-style sysfs (Samsung Xclipse /
    // "sgpu", AMD-on-Exynos). readGpuUsageSample() knows how to parse each filename shape.
    private static final String[] GPU_USAGE_STATIC_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/gpubusy",
        "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
        "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
        "/sys/class/misc/mali0/device/utilisation",
        "/sys/class/misc/mali0/device/utilization",
        "/sys/class/misc/mali0/device/gpuinfo",
        "/sys/devices/platform/mali/utilization",
        "/sys/kernel/gpu/gpu_busy",
        "/sys/devices/platform/gpusysfs/gpu_busy",   // Exynos Mali vendor node
        "/sys/class/misc/pvrsrvkm/device/utilisation",
        "/sys/class/pvr/utilisation",                // PowerVR (Rogue) sysfs
        "/sys/class/pvr/gpu_utilisation",
        "/sys/class/drm/card0/device/gpu_busy_percent", // amdgpu / Xclipse (sgpu), 0..100
        "/sys/class/devfreq/gpu/load",
        // ── Additive cross-vendor fallbacks (appended LAST: tried only after every path above
        //    misses, so a device that already resolves — e.g. Adreno kgsl — never reaches these). ──
        "/sys/kernel/ged/hal/gpu_utilization",       // MediaTek GED: "<loading> <block> <idle>" → loading%
        "/sys/module/ged/parameters/gpu_loading",    // MediaTek GED module param, plain percent
    };
    // Tried after every path above AND the platform/devfreq scans, so a GPU that already resolves keeps
    // its node. From WinlatorMali's HUD (GunaCharanTeja), for MediaTek Mali phones whose mali0 and
    // platform nodes are blocked.
    private static final String[] GPU_USAGE_LATE_PATHS = {
        "/sys/class/devfreq/mtk-dvfs-gpu/gpu_loading",            // MediaTek GPU devfreq, plain percent
        "/sys/module/mali_kbase/parameters/mali_gpu_utilization", // MediaTek kbase module param, percent
    };
    // Percent-bearing filenames scanned under each devfreq / platform GPU node.
    private static final String[] GPU_USAGE_FILES = {
        "gpu_busy_percentage", "gpu_busy_percent", "gpu_load", "utilisation", "utilization",
        "load", "gpu_busy", "gpuinfo",
    };
    // Substrings that mark a devfreq / platform node as the GPU, across vendors.
    private static final String[] GPU_NODE_TOKENS = {
        "gpu", "mali", "g3d", "kgsl", "panfrost", "pvr", "powervr", "xclipse", "sgpu",
    };

    private Integer readGpuUsageSample(String path) {
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        switch (fileName) {
            case "gpubusy": {
                String raw = readFirstLine(path);
                if (raw == null) return null;
                String[] parts = raw.trim().split("\\s+");
                if (parts.length < 2) return null;
                Long busy = parseLong(parts[0]);
                Long total = parseLong(parts[1]);
                if (busy == null || total == null || total <= 0L) return null;
                return clampPercent((int) ((busy * 100L) / total));
            }
            case "gpuinfo": {
                // Mali multi-line node: the GPU busy time (ms) is the last token of line 1. Utilisation
                // is the delta over wall-clock delta between two reads, so it needs state.
                String line = readNthLine(path, 1);
                if (line == null) return null;
                String[] toks = line.trim().split("\\s+");
                Long gpuMs = parseLong(toks[toks.length - 1]);
                if (gpuMs == null) return null;
                long now = SystemClock.elapsedRealtime();
                Long prevMs = lastMaliGpuInfoMs;
                long prevWall = lastMaliGpuInfoWallMs;
                lastMaliGpuInfoMs = gpuMs;
                lastMaliGpuInfoWallMs = now;
                if (prevMs == null || prevWall <= 0L) return null;
                long wallDelta = now - prevWall;
                if (wallDelta <= 0L) return null;
                long gpuDelta = Math.max(0L, gpuMs - prevMs);
                return clampPercent((int) ((gpuDelta * 100L) / wallDelta));
            }
            default:
                return readPercentFromLine(path);
        }
    }

    private Integer readPercentFromLine(String path) {
        String raw = readFirstLine(path);
        if (raw == null) return null;
        for (String tok : raw.trim().split("\\s+")) {
            String digits = tok.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                Integer v = parseInt(digits);
                return v == null ? null : clampPercent(v);
            }
        }
        return null;
    }

    // =======================================================================
    // RAM
    // =======================================================================
    public float getRAMPercent() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        if (mi.totalMem <= 0) return 0;
        return (mi.totalMem - mi.availMem) * 100f / mi.totalMem;
    }

    /** Used RAM as "x.xGB" (or "yMB" below 1 GB), matching GameNative's HUD text. */
    public String getUsedRamText() {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return "—";
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long usedBytes = Math.max(0L, mi.totalMem - mi.availMem);
        double usedGb = usedBytes / (1024.0 * 1024.0 * 1024.0);
        if (usedGb >= 1.0) return String.format(Locale.US, "%.1fGB", usedGb);
        return (usedBytes / (1024L * 1024L)) + "MB";
    }

    // =======================================================================
    // Temperature
    // =======================================================================
    private List<String[]> thermalZonesCache = null; // each entry: {type, tempPath}

    /** Legacy non-null CPU-temperature accessor in °C (0 when unavailable). Kept for {@code PerfHudView}. */
    public float getTemperature() {
        Integer t = getCpuTempC();
        return t == null ? 0f : t;
    }

    /** Hottest representative CPU temperature in °C, or null when no CPU zone is readable. */
    public Integer getCpuTempC() {
        return readTemperatureC(discoverPrioritizedCpuTempPaths());
    }

    /** GPU temperature in °C, or null when no GPU sensor is readable. */
    public Integer getGpuTempC() {
        ArrayList<String> paths = new ArrayList<>();
        paths.add("/sys/class/kgsl/kgsl-3d0/temp");
        paths.add("/sys/class/kgsl/kgsl-3d0/devfreq/temp");
        paths.add("/sys/class/misc/mali0/device/temp");
        paths.add("/sys/kernel/gpu/temp");
        paths.addAll(discoverPrioritizedGpuTempPaths());
        return readTemperatureC(paths);
    }

    private List<String> discoverPrioritizedCpuTempPaths() {
        return prioritizePaths(type -> {
            if (type.contains("gpu")) return null;                 // never mistake a GPU zone for CPU
            if (type.contains("cpu-silicon")) return 0;
            if (type.contains("cpu-0")) return 1;
            if (type.contains("cpuss")) return 2;                  // Qualcomm cpuss composite
            if (type.contains("mtktscpu")) return 2;               // MediaTek CPU thermal
            if (type.contains("cpu")) return 2;                    // cpu, cpu-*-usr, cpu_thermal, cpu_center
            if (type.contains("s5p-tmu")) return 3;                // Exynos TMU
            if (type.contains("soc")) return 4;
            if (type.contains("cputop")) return 5;
            if (type.contains("tsens")) return 6;                  // Qualcomm tsens aggregate
            if (type.contains("cluster")) return 7;
            if (type.contains("big") || type.contains("little")) return 8;
            return null;
        });
    }

    private List<String> discoverPrioritizedGpuTempPaths() {
        return prioritizePaths(type -> {
            if (type.contains("gpu-silicon")) return 0;
            if (type.contains("gpuss")) return 1;                  // Qualcomm gpuss
            if (type.contains("mtktsgpu")) return 1;               // MediaTek GPU thermal
            if (type.contains("gpu-virt")) return 2;
            if (type.contains("gpu")) return 2;
            if (type.contains("g3d")) return 3;                    // Exynos Mali (g3d)
            if (type.contains("kgsl")) return 4;
            if (type.contains("mali")) return 5;
            if (type.contains("xclipse") || type.contains("sgpu")) return 6;
            return null;
        });
    }

    private interface Ranker { Integer rank(String type); }

    private List<String> prioritizePaths(Ranker ranker) {
        List<String[]> zones = discoverAllThermalZones();
        ArrayList<int[]> order = new ArrayList<>(); // {index, rank}
        for (int i = 0; i < zones.size(); i++) {
            Integer r = ranker.rank(zones.get(i)[0]);
            if (r != null) order.add(new int[]{i, r});
        }
        // Sort by rank, then by path for determinism.
        order.sort((a, b) -> {
            if (a[1] != b[1]) return Integer.compare(a[1], b[1]);
            return zones.get(a[0])[1].compareTo(zones.get(b[0])[1]);
        });
        ArrayList<String> result = new ArrayList<>();
        for (int[] e : order) result.add(zones.get(e[0])[1]);
        return result;
    }

    private List<String[]> discoverAllThermalZones() {
        if (thermalZonesCache != null) return thermalZonesCache;
        ArrayList<String[]> zones = new ArrayList<>();
        LinkedHashSet<String> seenPaths = new LinkedHashSet<>();
        File[] thermalDirs = {
            new File("/sys/class/thermal"),
            new File("/sys/devices/virtual/thermal"),
        };
        for (File dir : thermalDirs) {
            File[] zoneDirs = dir.listFiles((d, name) -> name.startsWith("thermal_zone"));
            if (zoneDirs == null) continue;
            for (File zone : zoneDirs) {
                if (!zone.isDirectory()) continue;
                String type = readFirstLine(new File(zone, "type").getPath());
                if (type == null) continue;
                type = type.trim().toLowerCase(Locale.US);
                String tempPath = new File(zone, "temp").getPath();
                if (seenPaths.add(tempPath)) zones.add(new String[]{type, tempPath});
            }
        }
        thermalZonesCache = zones;
        return zones;
    }

    private Integer readTemperatureC(List<String> paths) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String path : paths) {
            if (!seen.add(path)) continue;
            Integer raw = readIntFromLine(path);
            if (raw == null) continue;
            // Round to nearest degree for milli-°C sources; sanity-clamp to reject offline sensors.
            int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
            if (celsius >= 1 && celsius <= 150) return celsius;
        }
        return null;
    }

    /** The first path in {@code paths} that yields a plausible reading, or null. */
    private String resolveTempPath(List<String> paths) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String path : paths) {
            if (!seen.add(path)) continue;
            Integer raw = readIntFromLine(path);
            if (raw == null) continue;
            int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
            if (celsius >= 1 && celsius <= 150) return path;
        }
        return null;
    }

    // =======================================================================
    // Temperature danger bands (green / amber / red)
    // =======================================================================

    public enum TempSensor { CPU, GPU, BATTERY }

    /** Amber and red onset in °C. Always °C internally — conversion happens at format time only. */
    public static final class Thresholds {
        public final int amberC;
        public final int redC;
        Thresholds(int amberC, int redC) { this.amberC = amberC; this.redC = redC; }
    }

    public static final int TEMP_COLOR_OK    = 0xFF66BB6A;
    public static final int TEMP_COLOR_AMBER = 0xFFFFB74D;
    public static final int TEMP_COLOR_RED   = 0xFFEF5350;

    // Tier-2 fallbacks, used when the device declares no usable trip points. Battery is the only one
    // with a well-grounded number (Li-ion); CPU/GPU vary enough between parts that these are a
    // deliberately conservative guess — which is exactly why trip points are preferred.
    private static final int DEFAULT_RED_CPU = 90;
    private static final int DEFAULT_RED_GPU = 90;
    private static final int DEFAULT_RED_BAT = 48;

    /** Amber sits a proportional step below red, which scales sensibly across both CPU/GPU and battery ranges. */
    public static int amberForRed(int redC) {
        return Math.round(redC * 0.88f);
    }

    /** Parsed temperature display options, shared by every overlay so they can't disagree. */
    public static final class TempDisplay {
        public final boolean fahrenheit;
        public final boolean colorBands;
        public final boolean auto;
        public final int redCpu, redGpu, redBat;

        public TempDisplay(boolean fahrenheit, boolean colorBands, boolean auto,
                           int redCpu, int redGpu, int redBat) {
            this.fahrenheit = fahrenheit;
            this.colorBands = colorBands;
            this.auto = auto;
            this.redCpu = redCpu;
            this.redGpu = redGpu;
            this.redBat = redBat;
        }

        public static TempDisplay from(com.winhub.hernandez.core.KeyValueSet cfg) {
            if (cfg == null) return new TempDisplay(false, true, true,
                DEFAULT_RED_CPU, DEFAULT_RED_GPU, DEFAULT_RED_BAT);
            return new TempDisplay(
                cfg.get("tempUnit", "c").equalsIgnoreCase("f"),
                !cfg.get("tempBands", "1").equals("0"),
                !cfg.get("tempAuto", "1").equals("0"),
                parseInt(cfg.get("tempRedCpu", ""), DEFAULT_RED_CPU),
                parseInt(cfg.get("tempRedGpu", ""), DEFAULT_RED_GPU),
                parseInt(cfg.get("tempRedBat", ""), DEFAULT_RED_BAT));
        }

        int manualRedFor(TempSensor s) {
            switch (s) {
                case GPU:     return redGpu;
                case BATTERY: return redBat;
                default:      return redCpu;
            }
        }

        private static int parseInt(String s, int fallback) {
            try { return s == null || s.isEmpty() ? fallback : Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return fallback; }
        }
    }

    // Trip points are a device constant — resolve once per sensor and keep it.
    private final java.util.EnumMap<TempSensor, Thresholds> autoThresholdCache =
        new java.util.EnumMap<>(TempSensor.class);
    private final java.util.EnumSet<TempSensor> autoThresholdResolved =
        java.util.EnumSet.noneOf(TempSensor.class);

    /**
     * Bands for a sensor. Manual mode uses the user's red point; Auto prefers the device's OWN
     * declared thermal trip points (per-device truth — a handheld with active cooling and a phone on
     * the same SoC throttle at very different points) and falls back to {@link #DEFAULT_RED_CPU} etc.
     */
    public Thresholds resolveThresholds(TempSensor sensor, TempDisplay display) {
        if (display != null && !display.auto) {
            int red = display.manualRedFor(sensor);
            return new Thresholds(amberForRed(red), red);
        }
        if (!autoThresholdResolved.contains(sensor)) {
            autoThresholdResolved.add(sensor);
            Thresholds fromDevice = readTripPointThresholds(sensor);
            if (fromDevice != null) autoThresholdCache.put(sensor, fromDevice);
        }
        Thresholds cached = autoThresholdCache.get(sensor);
        if (cached != null) return cached;
        int red = sensor == TempSensor.GPU ? DEFAULT_RED_GPU
                : sensor == TempSensor.BATTERY ? DEFAULT_RED_BAT
                : DEFAULT_RED_CPU;
        return new Thresholds(amberForRed(red), red);
    }

    /**
     * Bands from the kernel's declared trip points for the zone we actually read this sensor from:
     * amber ← the lowest sane {@code passive} trip, red ← the lowest sane {@code hot}/{@code critical}.
     *
     * <p>Returns null unless a usable red point is found. Two real traps this guards against: some
     * zones aren't temperature sensors at all (a Snapdragon's {@code pm8550-bcl-lvl0} battery-current
     * limiter reports {@code trip_point_0_temp = 1} with type {@code passive}), and the GPU is often
     * read from a non-thermal_zone node ({@code /sys/class/kgsl/...}) that has no trip points.
     */
    private Thresholds readTripPointThresholds(TempSensor sensor) {
        String tempPath;
        switch (sensor) {
            case GPU:
                tempPath = resolveTempPath(discoverPrioritizedGpuTempPaths());
                break;
            case BATTERY:
                return null; // battery temp comes from BatteryManager, not a thermal zone
            default:
                tempPath = resolveTempPath(discoverPrioritizedCpuTempPaths());
        }
        if (tempPath == null) return null;
        File zoneDir = new File(tempPath).getParentFile();
        if (zoneDir == null) return null;

        File[] tripTemps = zoneDir.listFiles(
            (d, name) -> name.startsWith("trip_point_") && name.endsWith("_temp"));
        if (tripTemps == null || tripTemps.length == 0) return null;

        Integer amber = null, red = null;
        for (File tripTemp : tripTemps) {
            Integer raw = readIntFromLine(tripTemp.getPath());
            if (raw == null) continue;
            int celsius = raw > 1000 ? (raw + 500) / 1000 : raw;
            // Reject anything that isn't plausibly a thermal trip — this is what filters out the
            // battery-current-limiter zones whose "trip point" is a level index, not a temperature.
            if (celsius < 20 || celsius > 120) continue;

            String typePath = tripTemp.getPath().replaceAll("_temp$", "_type");
            String type = readFirstLine(typePath);
            if (type == null) continue;
            type = type.trim().toLowerCase(Locale.US);

            if (type.contains("critical") || type.contains("hot")) {
                if (red == null || celsius < red) red = celsius;
            } else if (type.contains("passive")) {
                if (amber == null || celsius < amber) amber = celsius;
            }
        }
        if (red == null) return null;
        if (amber == null || amber >= red) amber = amberForRed(red);
        return new Thresholds(amber, red);
    }

    /** Band colour for a reading, or {@code fallbackColor} when banding is off or the value is unusable. */
    public static int tempColor(Float celsius, Thresholds t, TempDisplay display, int fallbackColor) {
        if (celsius == null || t == null || display == null || !display.colorBands) return fallbackColor;
        if (celsius >= t.redC) return TEMP_COLOR_RED;
        if (celsius >= t.amberC) return TEMP_COLOR_AMBER;
        return TEMP_COLOR_OK;
    }

    /** Formats a °C reading in the user's unit. Thresholds stay °C; only display converts. */
    public static String formatTemp(float celsius, TempDisplay display, boolean oneDecimal) {
        boolean f = display != null && display.fahrenheit;
        float value = f ? celsius * 9f / 5f + 32f : celsius;
        String unit = f ? "°F" : "°C";
        return oneDecimal
            ? String.format(Locale.ENGLISH, "%.1f%s", value, unit)
            : String.format(Locale.ENGLISH, "%d%s", Math.round(value), unit);
    }

    // =======================================================================
    // Battery
    // =======================================================================
    public static final class Battery {
        public final float watts;
        public final boolean charging;
        public final Integer percent;      // 0..100 or null
        public final Integer tempC;        // rounded °C or null
        public final String runtimeText;   // "LEFT 2h 5m" / "LEFT CHG" / null

        Battery(float watts, boolean charging) {
            this(watts, charging, null, null, null);
        }
        Battery(float watts, boolean charging, Integer percent, Integer tempC, String runtimeText) {
            this.watts = watts;
            this.charging = charging;
            this.percent = percent;
            this.tempC = tempC;
            this.runtimeText = runtimeText;
        }
    }

    /** power_supply current_now channels (µA) for the dual-battery sum. */
    private static final String[] CURRENT_CHANNELS = {
        "/sys/class/power_supply/battery/current_now",
        "/sys/class/power_supply/bms/current_now",
        "/sys/class/power_supply/main/current_now",
    };

    /** power_supply voltage_now channels (µV) — fallback when {@code EXTRA_VOLTAGE} reads 0 on some
     *  OEM firmwares (e.g. HONOR Magic), which otherwise leaves wattage stuck at 0.0W. */
    private static final String[] VOLTAGE_CHANNELS = {
        "/sys/class/power_supply/battery/voltage_now",
        "/sys/class/power_supply/bms/voltage_now",
        "/sys/class/power_supply/main/voltage_now",
        "/sys/class/power_supply/maxfg/voltage_now",   // Pixel / Tensor fuel gauge (from WinlatorMali)
    };
    /** power_supply power_now channels (µW) — last-resort direct power reading when neither the
     *  BatteryManager voltage nor {@code voltage_now} is available. */
    private static final String[] POWER_CHANNELS = {
        "/sys/class/power_supply/battery/power_now",
        "/sys/class/power_supply/bms/power_now",
        "/sys/class/power_supply/main/power_now",
    };

    /** First readable long across a channel list, or null. */
    private static Long readLongFromChannels(String[] paths) {
        for (String p : paths) {
            Long v = readLongFromLine(p);
            if (v != null) return v;
        }
        return null;
    }

    /**
     * ADDITIVE charging fallback from {@code /sys/class/power_supply/*}, consulted ONLY when the
     * framework gave no usable charge state ({@code EXTRA_STATUS == UNKNOWN} or a null battery intent).
     * A charger/input supply reporting {@code online == 1} — or the battery's own {@code status} reading
     * "Charging"/"Full" — means charging. Returns TRUE when charging is positively detected, FALSE when a
     * status/online node is readable but not charging, or null when nothing usable exists (so the caller
     * keeps its own default). Never reached on devices whose BatteryManager reports a real status
     * (e.g. Adreno phones/handhelds), so it cannot change their reading.
     */
    private static Boolean readChargingFromPowerSupply() {
        File[] supplies = new File("/sys/class/power_supply").listFiles();
        if (supplies == null || supplies.length == 0) return null;
        Boolean sawSignal = null;
        for (File supply : supplies) {
            String name = supply.getName().toLowerCase(Locale.US);
            String type = readFirstLine(new File(supply, "type").getPath());
            type = type == null ? "" : type.trim().toLowerCase(Locale.US);
            boolean isBattery = type.equals("battery") || name.equals("battery") || name.equals("bms");
            if (isBattery) {
                // The battery's own status string is the most direct charge-state signal.
                String st = readFirstLine(new File(supply, "status").getPath());
                if (st == null) continue;
                st = st.trim().toLowerCase(Locale.US);
                sawSignal = Boolean.FALSE;                 // "discharging"/"not charging" both land here
                if (st.equals("charging") || st.equals("full")) return Boolean.TRUE;
            } else {
                // A charger / power-input supply (ac/usb/wireless/mains/dc): online==1 → external power in.
                Long online = readLongFromLine(new File(supply, "online").getPath());
                if (online == null) continue;
                sawSignal = Boolean.FALSE;
                if (online == 1L) return Boolean.TRUE;
            }
        }
        return sawSignal;
    }

    private Double smoothedBatteryRuntimeHours = null;
    private static final double MAX_RUNTIME_HOURS = 72.0;
    private static final double RUNTIME_SMOOTHING_OLD_WEIGHT = 0.65;
    private static final double RUNTIME_SMOOTHING_NEW_WEIGHT = 0.35;

    /**
     * Legacy discharge-only power reading used by the classic overlays + {@code PerfHudView}:
     * watts are reported only while discharging (0 when charging). When {@code dualBattery} is set the
     * per-cell current channels are summed to correct devices that report only one cell's current.
     */
    public Battery getBattery(boolean dualBattery) {
        Intent status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        boolean charging = false;
        int voltageMv = 0;
        if (status != null) {
            charging = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
            voltageMv = status.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);
        } else {
            // ADDITIVE: no battery intent → best-effort power_supply charge probe (default stays false).
            // Never reached on devices that deliver ACTION_BATTERY_CHANGED (e.g. Adreno).
            Boolean sysCharging = readChargingFromPowerSupply();
            if (sysCharging != null) charging = sysCharging;
        }
        long microAmps;
        if (dualBattery) {
            long sum = 0; int n = 0;
            for (String path : CURRENT_CHANNELS) {
                Long v = readLongFromLine(path);
                if (v != null) { sum += Math.abs(v); n++; }
            }
            microAmps = n > 0 ? -sum : readCurrentNowFallback();
        } else {
            microAmps = readCurrentNowFallback();
        }
        // current_now's SIGN convention is device-dependent — many OEMs (Xiaomi/Poco especially)
        // report discharge as POSITIVE, the opposite of the AOSP convention. Gating wattage on the
        // sign therefore reads 0W on battery on those devices (and only shows a value while charging).
        // Use the magnitude for the power figure; the reliable charge DIRECTION is EXTRA_PLUGGED (the
        // `charging` flag), which the HUD already uses for the PWR/CHG label.
        // EXTRA_VOLTAGE reads 0 on some OEM firmwares even though current is fine — fall back to the
        // power_supply voltage_now (µV → mV) so wattage isn't stuck at 0.
        if (voltageMv <= 0) {
            Long uv = readLongFromChannels(VOLTAGE_CHANNELS);
            if (uv != null && uv > 0) voltageMv = (int) (Math.abs(uv) / 1000L);
        }
        // current unit auto-detected (mA vs µA) — see currentRawToAmps.
        float watts = (voltageMv / 1000f) * currentRawToAmps(microAmps);
        return new Battery(watts, charging);
    }

    /**
     * Full GameNative-parity battery snapshot for the GameNative-style HUD: %, power W (magnitude,
     * regardless of charge/discharge), smoothed runtime-left estimate, and temperature.
     */
    public Battery collectBattery() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (bm == null) return new Battery(0f, false);

        Integer percent = null;
        int cap = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        if (cap >= 0 && cap <= 100) percent = cap;

        Intent status = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (status == null) {
            // No battery intent at all → additive power_supply charge probe (default stays false).
            Boolean sysCharging = readChargingFromPowerSupply();
            return new Battery(0f, sysCharging != null && sysCharging, percent, null, null);
        }

        int st = status.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL;
        // ADDITIVE: only when the framework reports no usable charge state do we consult power_supply.
        // A device that reports CHARGING/DISCHARGING/FULL/NOT_CHARGING (e.g. Adreno) never enters this.
        if (st == BatteryManager.BATTERY_STATUS_UNKNOWN) {
            Boolean sysCharging = readChargingFromPowerSupply();
            if (sysCharging != null) charging = sysCharging;
        }
        long currentMicroAmps = Math.abs(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
        long chargeCounter = bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        int voltageMv = status.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0);

        Integer tempC = null;
        int rawTemp = status.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
        if (rawTemp > 0) tempC = Math.round(rawTemp / 10f);

        // EXTRA_VOLTAGE reads 0 on some OEM firmwares (e.g. HONOR Magic) even though capacity, temp
        // and current all work — which left wattage stuck at 0.0W. Fall back to the power_supply
        // voltage_now (µV → mV); if voltage is still unavailable, use power_now (µW) directly.
        if (voltageMv <= 0) {
            Long uv = readLongFromChannels(VOLTAGE_CHANNELS);
            if (uv != null && uv > 0) voltageMv = (int) (Math.abs(uv) / 1000L);
        }
        // Wattage = current × voltage, with current UNIT auto-detected (CURRENT_NOW is nominally µA but
        // many OEMs — e.g. this HONOR — report mA; see batteryCurrentAmps). currentMicroAmps above stays
        // as-is for the runtime estimate (a ratio, so unit-independent). If neither current nor voltage
        // resolves, fall back to power_now (µW) directly.
        float amps = batteryCurrentAmps(bm);
        float watts = 0f;
        if (amps > 0f && voltageMv > 0) {
            watts = (voltageMv / 1000f) * amps;
        } else {
            Long uw = readLongFromChannels(POWER_CHANNELS);   // µW
            if (uw != null && uw != 0L) watts = (float) (Math.abs(uw) / 1_000_000.0);
        }

        String runtimeText;
        if (charging) {
            smoothedBatteryRuntimeHours = null;
            runtimeText = "LEFT CHG";
        } else if (currentMicroAmps <= 0L || chargeCounter <= 0L) {
            runtimeText = null;
        } else {
            double rawHours = (double) chargeCounter / (double) currentMicroAmps;
            if (!Double.isFinite(rawHours) || rawHours <= 0.0 || rawHours > MAX_RUNTIME_HOURS) {
                runtimeText = null;
            } else {
                double smoothed = smoothedBatteryRuntimeHours == null ? rawHours
                    : (smoothedBatteryRuntimeHours * RUNTIME_SMOOTHING_OLD_WEIGHT)
                        + (rawHours * RUNTIME_SMOOTHING_NEW_WEIGHT);
                smoothedBatteryRuntimeHours = smoothed;
                runtimeText = "LEFT " + formatRuntimeHours(smoothed);
            }
        }
        return new Battery(watts, charging, percent, tempC, runtimeText);
    }

    private static String formatRuntimeHours(double hours) {
        int totalMinutes = Math.max(1, (int) Math.round(hours * 60.0));
        int wholeHours = totalMinutes / 60;
        int minutes = totalMinutes % 60;
        if (wholeHours > 0 && minutes > 0) return wholeHours + "h " + minutes + "m";
        if (wholeHours > 0) return wholeHours + "h";
        return minutes + "m";
    }

    private long readCurrentNowFallback() {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        return bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
    }

    /** Raw battery current reading → AMPS, with unit AUTO-DETECT. {@code BATTERY_PROPERTY_CURRENT_NOW}
     *  (and {@code current_now}) are nominally µA, but many OEMs report mA — a small number, whereas a
     *  real µA reading is always huge. So a magnitude &lt; 20000 means the device gave mA. Without this,
     *  wattage on mA-reporting devices came out ~1000× too small → 0.0W even though current & voltage are
     *  fine. Sentinel/zero → 0. (Heuristic mirrors Ludashi-plus WinlatorHUD.getBatteryCurrentAmps().) */
    private static float currentRawToAmps(long raw) {
        if (raw == 0L || raw == Long.MIN_VALUE) return 0f;
        long a = Math.abs(raw);
        return a < 20000L ? a / 1000f : a / 1_000_000f;   // mA → A, else µA → A
    }

    /** Battery current in AMPS for the wattage calc: framework property first, then power_supply
     *  {@code current_now} sysfs (battery/bms/main) when it's unsupported ({@code Long.MIN_VALUE}) or 0. */
    private float batteryCurrentAmps(BatteryManager bm) {
        long raw = (bm != null) ? bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) : 0L;
        if (raw == 0L || raw == Long.MIN_VALUE) {
            Long sys = readLongFromChannels(CURRENT_CHANNELS);
            raw = (sys != null) ? sys : 0L;
        }
        return currentRawToAmps(raw);
    }

    // =======================================================================
    // Clock frequencies (best-effort, nullable)
    // =======================================================================

    /** Peak CPU core clock in MHz (max scaling_cur_freq across cores), or null when unreadable. */
    public Integer getCpuClockMhz() {
        int cores = Runtime.getRuntime().availableProcessors();
        long maxKhz = 0;
        for (int i = 0; i < cores; i++) {
            Long cur = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            if (cur != null && cur > maxKhz) maxKhz = cur;
        }
        if (maxKhz <= 0) return null;
        return (int) (maxKhz / 1000L); // kHz → MHz
    }

    private List<String> gpuClockPathsCache = null;
    private static final String[] GPU_CLOCK_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/gpuclk",             // Adreno, Hz
        "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",   // Adreno devfreq, Hz
        "/sys/class/kgsl/kgsl-3d0/clock_mhz",          // some kernels, already MHz
        "/sys/kernel/gpu/gpu_clock",                   // Mali vendor node, MHz
        "/sys/devices/platform/gpusysfs/gpu_clock",    // Exynos Mali vendor node, MHz
        "/sys/class/misc/mali0/device/clock",          // Mali, kHz/MHz
        "/sys/class/devfreq/gpu/cur_freq",             // generic devfreq gpu, Hz
        // ── Additive cross-vendor fallbacks (appended LAST: only reached when every node above
        //    is unreadable, so Adreno/Mali devices that already resolve are unaffected). ──
        "/sys/kernel/ged/hal/current_freqency",        // MediaTek GED (vendor's spelling), kHz
        "/sys/kernel/ged/hal/gpu_clock",               // MediaTek GED alt, MHz
    };

    private List<String> discoverGpuClockPaths() {
        if (gpuClockPathsCache != null) return gpuClockPathsCache;
        LinkedHashSet<String> found = new LinkedHashSet<>();
        for (String p : GPU_CLOCK_PATHS) if (new File(p).canRead()) found.add(p);
        // Vendor-agnostic devfreq walk: any gpu/mali/panfrost/xclipse/sgpu node's cur_freq (Hz).
        File[] devfreqRoots = {
            new File("/sys/class/devfreq"),
            new File("/sys/devices/virtual/devfreq"),
        };
        for (File root : devfreqRoots) {
            if (!root.isDirectory()) continue;
            File[] nodeDirs = root.listFiles(File::isDirectory);
            if (nodeDirs == null) continue;
            for (File node : nodeDirs) {
                String nodePath = node.getPath().toLowerCase(Locale.US);
                boolean looksLikeGpu = false;
                for (String t : GPU_NODE_TOKENS) {
                    if (nodePath.contains(t)) { looksLikeGpu = true; break; }
                }
                if (!looksLikeGpu) continue;
                File f = new File(node, "cur_freq");
                if (f.canRead()) found.add(f.getPath());
            }
        }
        gpuClockPathsCache = new ArrayList<>(found);
        return gpuClockPathsCache;
    }

    /** GPU clock in MHz (Adreno KGSL / devfreq / Mali nodes), or null when no source reports it. */
    public Integer getGpuClockMhz() {
        for (String p : discoverGpuClockPaths()) {
            Long raw = readLongFromLine(p);
            if (raw == null || raw <= 0) continue;
            long mhz;
            if (raw >= 1_000_000L) mhz = raw / 1_000_000L;      // Hz
            else if (raw >= 10_000L) mhz = raw / 1_000L;        // kHz (defensive)
            else mhz = raw;                                      // already MHz
            if (mhz >= 1 && mhz <= 3000) return (int) mhz;
        }
        // No readable clock node: on a CSF-generation Mali, the hardware counters' cycle counter.
        return MaliGpuCounters.clockMhz();
    }

    // =======================================================================
    // VRAM (best-effort, Java-only sysfs — no native rebuild)
    // =======================================================================
    // Covered here in pure Java: Adreno KGSL (bytes mapped/allocated) and amdgpu-style drm nodes
    // (Samsung Xclipse / "sgpu", AMD-on-Exynos), which expose mem_info_vram_used/_total directly.
    // TODO(native): VK_EXT_memory_budget as a vendor-agnostic fallback for Mali/PowerVR, which expose
    // no world-readable VRAM node. FEASIBILITY (checked 2026-07): the compositor's Vulkan layer
    // (app/src/main/cpp/winlator/vulkan.c) creates an EPHEMERAL VkInstance/VkPhysicalDevice inside
    // getRenderer() and destroys it every call — it holds no reusable device and loads only the
    // properties/extension entrypoints, not vkGetPhysicalDeviceMemoryProperties2. Wiring the budget
    // query therefore means a new JNI method + enabling VK_KHR_get_physical_device_properties2 /
    // VK_EXT_memory_budget at instance create + an NDK rebuild — out of scope for a source-only pass.
    // CAVEAT to document if/when added: this HUD is an app-side overlay, NOT in the game's Vulkan
    // device, so an app-created instance reports the DEVICE-WIDE heap budget (a GPU-memory-pressure
    // proxy), not the game's per-process VRAM. Devices with no readable node return null → HUD hides VRAM.
    private List<String> vramUsedPathsCache = null;
    private static final String[] VRAM_USED_PATHS = {
        "/sys/class/kgsl/kgsl-3d0/gpumem_mapped",        // bytes currently mapped to the GPU
        "/sys/class/kgsl/kgsl-3d0/page_alloc",           // bytes allocated to the KGSL page pool
        "/sys/class/kgsl/kgsl-3d0/mapped",               // alt name on some kernels
        "/sys/class/drm/card0/device/mem_info_vram_used",// amdgpu / Xclipse (sgpu), bytes
        "/sys/class/kgsl/kgsl-3d0/page_alloc_max",       // high-water fallback
    };
    // Total-VRAM companions (amdgpu exposes one; used only by the diagnostics report).
    private static final String[] VRAM_TOTAL_PATHS = {
        "/sys/class/drm/card0/device/mem_info_vram_total",
    };

    /** Used GPU memory in bytes from Adreno KGSL sysfs, or null when this device exposes nothing readable. */
    public Long getVramUsedBytes() {
        if (vramUsedPathsCache == null) {
            ArrayList<String> found = new ArrayList<>();
            for (String p : VRAM_USED_PATHS) if (new File(p).canRead()) found.add(p);
            vramUsedPathsCache = found;
        }
        for (String p : vramUsedPathsCache) {
            Long b = sanitizeVramBytes(readLongFromLine(p));
            if (b != null) return b;
        }
        return null;
    }

    private static Long sanitizeVramBytes(Long v) {
        if (v == null) return null;
        if (v < (1L << 20)) return null;             // < 1 MiB → idle-zero / not a byte count
        if (v > 64L * (1L << 30)) return null;       // > 64 GiB → not bytes
        return v;
    }

    /** Used GPU memory as "x.xGiB" (or "yMiB"), or null when no VRAM source is readable. */
    public String getVramUsedText() {
        Long b = getVramUsedBytes();
        return b == null ? null : formatGiB(b);
    }

    private static String formatGiB(long bytes) {
        double gib = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gib >= 0.1) return String.format(Locale.US, "%.1fGiB", gib);
        return (bytes / (1024L * 1024L)) + "MiB";
    }

    // =======================================================================
    // Single cached snapshot — read EVERYTHING once per HUD refresh
    // =======================================================================

    /**
     * Immutable point-in-time reading of every metric the Fusion HUD draws. Collected in one pass by
     * {@link #snapshot()} so the view never touches sysfs / BatteryManager during draw (the user's
     * explicit ask: one collection pass, cached, so gameplay isn't strained). All numeric fields are
     * raw (nullable when unreadable); the view formats + colours them.
     */
    public static final class Snapshot {
        public final Integer cpuPercent;   // 0..100 or null
        public final Integer gpuPercent;   // 0..100 or null
        public final Integer cpuClockMhz;  // MHz or null
        public final Integer gpuClockMhz;  // MHz or null
        public final Integer cpuTempC;     // °C or null
        public final Integer gpuTempC;     // °C or null
        public final long ramUsedBytes;
        public final long ramTotalBytes;
        public final float ramPercent;     // 0..100
        public final Long vramUsedBytes;   // bytes or null
        public final Battery battery;      // never null
        // ---- Mega-only extras ----
        public final int[] perCorePercent;  // per-core 0..100, -1 unknown (never null; may be empty)
        public final int[] perCoreClockMhz; // per-core MHz, 0 unknown (never null; may be empty)
        public final Long swapUsedBytes;    // bytes or null (no swap)
        public final Long swapTotalBytes;   // bytes or null
        public final Long netDownBps;       // bytes/sec down, or null (first sample / unreadable)
        public final Long netUpBps;         // bytes/sec up, or null

        Snapshot(Integer cpuPercent, Integer gpuPercent, Integer cpuClockMhz, Integer gpuClockMhz,
                 Integer cpuTempC, Integer gpuTempC, long ramUsedBytes, long ramTotalBytes,
                 float ramPercent, Long vramUsedBytes, Battery battery,
                 int[] perCorePercent, int[] perCoreClockMhz, Long swapUsedBytes, Long swapTotalBytes,
                 Long netDownBps, Long netUpBps) {
            this.cpuPercent = cpuPercent;
            this.gpuPercent = gpuPercent;
            this.cpuClockMhz = cpuClockMhz;
            this.gpuClockMhz = gpuClockMhz;
            this.cpuTempC = cpuTempC;
            this.gpuTempC = gpuTempC;
            this.ramUsedBytes = ramUsedBytes;
            this.ramTotalBytes = ramTotalBytes;
            this.ramPercent = ramPercent;
            this.vramUsedBytes = vramUsedBytes;
            this.battery = battery;
            this.perCorePercent = perCorePercent != null ? perCorePercent : new int[0];
            this.perCoreClockMhz = perCoreClockMhz != null ? perCoreClockMhz : new int[0];
            this.swapUsedBytes = swapUsedBytes;
            this.swapTotalBytes = swapTotalBytes;
            this.netDownBps = netDownBps;
            this.netUpBps = netUpBps;
        }

        public String ramUsedText() { return formatBytesGb(ramUsedBytes); }
        public String ramTotalText() { return formatBytesGb(ramTotalBytes); }
        public String vramText() { return vramUsedBytes == null ? null : formatGiB(vramUsedBytes); }
        public String swapUsedText() { return swapUsedBytes == null ? null : formatBytesGb(swapUsedBytes); }
        public String swapTotalText() { return swapTotalBytes == null ? null : formatBytesGb(swapTotalBytes); }

        private static String formatBytesGb(long bytes) {
            double gb = bytes / (1024.0 * 1024.0 * 1024.0);
            if (gb >= 1.0) return String.format(Locale.US, "%.1fGiB", gb);
            return (bytes / (1024L * 1024L)) + "MiB";
        }
    }

    /** One collection pass over every metric. Call from the HUD's own ~1 s refresh thread, never draw. */
    public Snapshot snapshot() {
        Integer cpu = getCpuUsagePercent();
        Integer gpu = getGpuUsagePercent();
        Integer cpuClk = getCpuClockMhz();
        Integer gpuClk = getGpuClockMhz();
        Integer cpuTemp = getCpuTempC();
        Integer gpuTemp = getGpuTempC();

        long usedBytes = 0, totalBytes = 0;
        float ramPct = 0f;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            totalBytes = Math.max(0L, mi.totalMem);
            usedBytes = Math.max(0L, mi.totalMem - mi.availMem);
            ramPct = totalBytes > 0 ? usedBytes * 100f / totalBytes : 0f;
        }

        Long vram = getVramUsedBytes();
        Battery battery = collectBattery();

        int[] perCorePct = readPerCorePercent();
        int[] perCoreClk = getPerCoreClockMhz();
        long[] swap = getSwapBytes();
        long[] net = readNetRateBps();

        return new Snapshot(cpu, gpu, cpuClk, gpuClk, cpuTemp, gpuTemp,
            usedBytes, totalBytes, ramPct, vram, battery,
            perCorePct, perCoreClk,
            swap == null ? null : swap[0], swap == null ? null : swap[1],
            net == null ? null : net[0], net == null ? null : net[1]);
    }

    // =======================================================================
    // Mega-view extras: per-core CPU, swap, network (best-effort, nullable)
    // =======================================================================

    /** Per-core clock in MHz (each core's {@code scaling_cur_freq}); 0 for a core that can't be read. */
    public int[] getPerCoreClockMhz() {
        int cores = Runtime.getRuntime().availableProcessors();
        int[] out = new int[Math.max(0, cores)];
        for (int i = 0; i < out.length; i++) {
            Long cur = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            out[i] = (cur != null && cur > 0) ? (int) (cur / 1000L) : 0;
        }
        return out;
    }

    // Per-core /proc/stat deltas — one prev sample per core, seeded on the first read.
    private long[] lastCoreTotal = null;
    private long[] lastCoreIdle = null;

    /** Per-core CPU usage 0..100 from the {@code cpuN} /proc/stat deltas; -1 for a core not yet sampled. */
    public int[] readPerCorePercent() {
        int cores = Runtime.getRuntime().availableProcessors();
        int[] out = new int[Math.max(0, cores)];
        java.util.Arrays.fill(out, -1);
        if (lastCoreTotal == null || lastCoreTotal.length != cores) {
            lastCoreTotal = new long[cores];
            lastCoreIdle = new long[cores];
            java.util.Arrays.fill(lastCoreTotal, -1L);
        }
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/stat"))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.startsWith("cpu")) break;      // cpu lines are first; stop at the block end
                if (line.length() < 4 || !Character.isDigit(line.charAt(3))) continue; // skip aggregate "cpu "
                String[] parts = line.trim().split("\\s+");
                int core;
                try { core = Integer.parseInt(parts[0].substring(3)); } catch (Exception e) { continue; }
                if (core < 0 || core >= cores) continue;
                long total = 0, idle = 0;
                boolean ok = true;
                for (int i = 1; i < parts.length; i++) {
                    try {
                        long v = Long.parseLong(parts[i]);
                        total += v;
                        if (i == 4) idle = v;          // idle
                        else if (i == 5) idle += v;    // + iowait
                    } catch (NumberFormatException e) { ok = false; break; }
                }
                if (!ok) continue;
                long prevTotal = lastCoreTotal[core], prevIdle = lastCoreIdle[core];
                lastCoreTotal[core] = total;
                lastCoreIdle[core] = idle;
                if (prevTotal >= 0) {
                    long dTotal = total - prevTotal, dIdle = idle - prevIdle;
                    if (dTotal > 0) out[core] = clampPercent((int) ((Math.max(0, dTotal - dIdle) * 100L) / dTotal));
                }
            }
        } catch (Exception ignored) {}
        // Frequency fallback for any core /proc/stat couldn't give us — restricted on some firmwares
        // (e.g. HONOR / Android 16), which is also why the aggregate getCpuUsagePercent() falls back.
        // scaling_cur_freq / cpuinfo_max_freq is a load proxy (per-core clocks ARE readable there), and
        // it also seeds the very first sample. Cores with a real /proc/stat delta keep it (no regression).
        for (int i = 0; i < out.length; i++) {
            if (out[i] >= 0) continue;
            Long cur = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/scaling_cur_freq");
            Long max = readLongFromLine("/sys/devices/system/cpu/cpu" + i + "/cpufreq/cpuinfo_max_freq");
            if (cur != null && max != null && max > 0L)
                out[i] = clampPercent((int) ((Math.max(0L, Math.min(cur, max)) * 100L) / max));
        }
        return out;
    }

    /** Swap {used, total} in bytes from /proc/meminfo, or null when there is no swap. */
    public long[] getSwapBytes() {
        long totalKb = -1, freeKb = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null && (totalKb < 0 || freeKb < 0)) {
                if (line.startsWith("SwapTotal:")) totalKb = parseMeminfoKb(line);
                else if (line.startsWith("SwapFree:")) freeKb = parseMeminfoKb(line);
            }
        } catch (Exception e) { return null; }
        if (totalKb <= 0) return null;
        long used = Math.max(0L, (totalKb - Math.max(0L, freeKb))) * 1024L;
        return new long[]{used, totalKb * 1024L};
    }

    private static long parseMeminfoKb(String line) {
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) return -1;
        Long v = parseLong(parts[1]);
        return v == null ? -1 : v;
    }

    // Net rate: cumulative rx/tx bytes + the wall-clock of the previous read → bytes/sec.
    private long lastNetRx = -1, lastNetTx = -1, lastNetWallMs = 0;

    /** Network {down, up} in bytes/sec (all interfaces except loopback), or null on the first/failed read. */
    public long[] readNetRateBps() {
        long rx = 0, tx = 0;
        boolean any = false;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/net/dev"))) {
            String line;
            while ((line = r.readLine()) != null) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String iface = line.substring(0, colon).trim();
                if (iface.equals("lo") || iface.isEmpty()) continue;
                String[] f = line.substring(colon + 1).trim().split("\\s+");
                if (f.length < 9) continue;
                Long rxB = parseLong(f[0]);
                Long txB = parseLong(f[8]);
                if (rxB == null || txB == null) continue;
                rx += rxB; tx += txB; any = true;
            }
        } catch (Exception e) { return null; }
        if (!any) return null;
        long now = SystemClock.elapsedRealtime();
        long prevRx = lastNetRx, prevTx = lastNetTx, prevWall = lastNetWallMs;
        lastNetRx = rx; lastNetTx = tx; lastNetWallMs = now;
        if (prevRx < 0 || prevWall <= 0) return null;    // seed
        long dt = now - prevWall;
        if (dt <= 0) return null;
        long down = Math.max(0L, (rx - prevRx) * 1000L / dt);
        long up = Math.max(0L, (tx - prevTx) * 1000L / dt);
        return new long[]{down, up};
    }

    // =======================================================================
    // Diagnostics report (invoked ONLY on user tap — never on the HUD refresh path)
    // =======================================================================

    /**
     * One-tap plain-text device report for the "Export HUD diagnostics" action: for every metric it
     * lists the resolved source path/API + current value, or MISS + the exact candidates it tried,
     * followed by an "exists on this device" dump (thermal zone types, devfreq/kgsl/mali nodes) so a
     * device owner can send back the real path for anything that missed. Does a short prime+sample so
     * delta-based readers (CPU%/GPU%/net) report a live value. Run OFF the main thread.
     */
    public String buildDiagnosticsReport(Context ctxIn) {
        final Context ctx = ctxIn != null ? ctxIn : this.context;
        // Prime the delta-based readers, wait, then take a live sample.
        snapshot();
        try { Thread.sleep(350L); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        Snapshot s = snapshot();

        StringBuilder sb = new StringBuilder();
        sb.append("Bannerlator HUD diagnostics\n");
        sb.append("Generated: ")
          .append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date())).append('\n');
        sb.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("Board/HW: ").append(Build.BOARD).append(" / ").append(Build.HARDWARE).append('\n');
        sb.append("SoC: ").append(socString()).append('\n');
        sb.append("Android: ").append(Build.VERSION.RELEASE)
          .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("CPU cores: ").append(Runtime.getRuntime().availableProcessors()).append('\n');
        sb.append("GPU renderer: ").append(safeGpu(() -> GPUInformation.getRenderer(null, ctx))).append('\n');
        sb.append("Vulkan: ").append(safeGpu(() -> GPUInformation.getVulkanVersion(null, ctx))).append('\n');

        sb.append("\n== Metrics ==\n");

        // ---- CPU ----
        boolean procStat = new File("/proc/stat").canRead();
        if (s.cpuPercent != null) {
            metricHit(sb, "CPU %", s.cpuPercent + "%",
                procStat ? "/proc/stat (cpu aggregate delta)"
                         : "/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq (freq estimate)");
        } else {
            metricMiss(sb, "CPU %", "/proc/stat",
                "/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq");
        }

        // Per-core %
        StringBuilder cores = new StringBuilder();
        for (int i = 0; i < s.perCorePercent.length; i++) {
            if (i > 0) cores.append(' ');
            int v = s.perCorePercent[i];
            cores.append('c').append(i).append('=').append(v < 0 ? "?" : (v + "%"));
        }
        if (s.perCorePercent.length > 0)
            metricHit(sb, "Per-core %", cores.toString(), "/proc/stat (per-cpu lines)");
        else
            metricMiss(sb, "Per-core %", "/proc/stat");

        // Per-core clock
        StringBuilder coreClk = new StringBuilder();
        for (int i = 0; i < s.perCoreClockMhz.length; i++) {
            if (i > 0) coreClk.append(' ');
            int v = s.perCoreClockMhz[i];
            coreClk.append('c').append(i).append('=').append(v <= 0 ? "?" : (v + "MHz"));
        }
        if (s.cpuClockMhz != null)
            metricHit(sb, "CPU clock", s.cpuClockMhz + "MHz (peak); " + coreClk,
                "/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq");
        else
            metricMiss(sb, "CPU clock", "/sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq");

        // CPU temp
        String cpuTempPath = resolveTempPath(discoverPrioritizedCpuTempPaths());
        if (s.cpuTempC != null && cpuTempPath != null)
            metricHit(sb, "CPU temp", s.cpuTempC + "°C", cpuTempPath);
        else
            metricMiss(sb, "CPU temp",
                "/sys/class/thermal/thermal_zone*/temp (types: cpu*/cpuss/tsens/mtktscpu/s5p-tmu/soc)");

        // ---- GPU ----
        String gpuUsagePath = firstReadableGpuUsagePath();
        if (s.gpuPercent != null && gpuUsagePath != null)
            metricHit(sb, "GPU %", s.gpuPercent + "%", gpuUsagePath);
        else
            metricMissList(sb, "GPU %", discoverGpuUsagePaths(), GPU_USAGE_STATIC_PATHS);

        String gpuClockPath = firstReadableGpuClockPath();
        if (s.gpuClockMhz != null && gpuClockPath != null)
            metricHit(sb, "GPU clock", s.gpuClockMhz + "MHz", gpuClockPath);
        else
            metricMissList(sb, "GPU clock", discoverGpuClockPaths(), GPU_CLOCK_PATHS);

        String gpuTempPath = resolveTempPath(discoverPrioritizedGpuTempPaths());
        if (s.gpuTempC != null)
            metricHit(sb, "GPU temp", s.gpuTempC + "°C",
                gpuTempPath != null ? gpuTempPath : "kgsl/mali direct temp node");
        else
            metricMiss(sb, "GPU temp",
                "/sys/class/kgsl/kgsl-3d0/temp", "/sys/class/misc/mali0/device/temp",
                "/sys/class/thermal/thermal_zone*/temp (types: gpu*/gpuss/g3d/kgsl/mali/mtktsgpu)");

        // ---- Memory ----
        metricHit(sb, "RAM", s.ramUsedText() + " / " + s.ramTotalText()
                + " (" + Math.round(s.ramPercent) + "%)", "ActivityManager.MemoryInfo");
        if (s.swapUsedBytes != null)
            metricHit(sb, "SWAP", s.swapUsedText() + " / " + s.swapTotalText(), "/proc/meminfo");
        else
            metricMiss(sb, "SWAP", "/proc/meminfo (SwapTotal=0 or unreadable)");

        // VRAM
        String vramPath = firstReadableVramUsedPath();
        if (s.vramUsedBytes != null && vramPath != null) {
            Long total = readFirstReadableLong(VRAM_TOTAL_PATHS);
            String v = s.vramText() + (total != null ? " / " + formatGiB(total) : "");
            metricHit(sb, "VRAM", v, vramPath);
        } else {
            metricMissList(sb, "VRAM (Java sysfs; Vulkan budget = native TODO)",
                new ArrayList<>(), VRAM_USED_PATHS);
        }

        // ---- Net ----
        if (s.netDownBps != null)
            metricHit(sb, "NET", formatRate(s.netDownBps) + " down / " + formatRate(s.netUpBps) + " up",
                "/proc/net/dev");
        else
            metricMiss(sb, "NET", "/proc/net/dev (first sample or unreadable)");

        // ---- Battery ----
        Battery b = s.battery;
        StringBuilder bat = new StringBuilder();
        bat.append(b.percent != null ? b.percent + "%" : "?%")
           .append(", ").append(String.format(Locale.US, "%.1fW", b.watts))
           .append(b.charging ? " (charging)" : "")
           .append(", temp ").append(b.tempC != null ? b.tempC + "°C" : "?")
           .append(b.runtimeText != null ? ", " + b.runtimeText : "");
        metricHit(sb, "Battery", bat.toString(), "BatteryManager + /sys/class/power_supply");

        // ---- Exists-on-device dump ----
        appendExistsDump(sb);
        return sb.toString();
    }

    private static String socString() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Build.SOC_MANUFACTURER + " " + Build.SOC_MODEL;
        }
        return "(SoC id needs Android 12+; HW=" + Build.HARDWARE + ")";
    }

    private interface GpuStr { String get(); }
    private static String safeGpu(GpuStr fn) {
        try { String v = fn.get(); return v == null ? "(null)" : v; }
        catch (Throwable t) { return "(unavailable: " + t.getClass().getSimpleName() + ")"; }
    }

    private String firstReadableGpuUsagePath() {
        for (String p : discoverGpuUsagePaths()) if (readGpuUsageSample(p) != null) return p;
        return null;
    }

    private String firstReadableGpuClockPath() {
        for (String p : discoverGpuClockPaths()) {
            Long raw = readLongFromLine(p);
            if (raw != null && raw > 0) return p;
        }
        return null;
    }

    private String firstReadableVramUsedPath() {
        if (vramUsedPathsCache == null) getVramUsedBytes();   // builds the cache
        if (vramUsedPathsCache == null) return null;
        for (String p : vramUsedPathsCache) if (sanitizeVramBytes(readLongFromLine(p)) != null) return p;
        return null;
    }

    private static Long readFirstReadableLong(String[] paths) {
        for (String p : paths) {
            Long v = readLongFromLine(p);
            if (v != null && v > 0) return v;
        }
        return null;
    }

    private static String formatRate(Long bps) {
        if (bps == null) return "?";
        double kb = bps / 1024.0;
        if (kb >= 1024.0) return String.format(Locale.US, "%.1fMB/s", kb / 1024.0);
        return String.format(Locale.US, "%.0fKB/s", kb);
    }

    private static void metricHit(StringBuilder sb, String name, String value, String source) {
        sb.append(name).append(": ").append(value).append("\n    source: ").append(source).append('\n');
    }

    private static void metricMiss(StringBuilder sb, String name, String... candidates) {
        sb.append(name).append(": MISS\n    tried: ");
        for (int i = 0; i < candidates.length; i++) {
            if (i > 0) sb.append("\n           ");
            sb.append(candidates[i]);
        }
        sb.append('\n');
    }

    private static void metricMissList(StringBuilder sb, String name, List<String> discovered, String[] statics) {
        LinkedHashSet<String> all = new LinkedHashSet<>();
        if (discovered != null) all.addAll(discovered);
        for (String p : statics) all.add(p);
        sb.append(name).append(": MISS\n    tried: ");
        boolean first = true;
        for (String p : all) {
            if (!first) sb.append("\n           ");
            sb.append(p);
            first = false;
        }
        if (first) sb.append("(no candidates)");
        sb.append('\n');
    }

    private void appendExistsDump(StringBuilder sb) {
        sb.append("\n== Exists on this device (send these back for any MISS above) ==\n");
        sb.append("-- /sys/class/thermal/thermal_zone*/type --\n");
        List<String[]> zones = discoverAllThermalZones();
        if (zones.isEmpty()) sb.append("  (none readable)\n");
        for (String[] z : zones) sb.append("  ").append(z[1]).append("  type=").append(z[0]).append('\n');
        appendDevfreqListing(sb, "/sys/class/devfreq");
        appendDevfreqListing(sb, "/sys/devices/virtual/devfreq");
        appendReadableFiles(sb, "/sys/class/kgsl/kgsl-3d0");
        appendReadableFiles(sb, "/sys/class/misc/mali0/device");
        appendReadableFiles(sb, "/sys/class/drm/card0/device");
        appendPowerSupplyDump(sb);
        appendGpuNoRootProbes(sb);
    }

    /** Probes for a ROOT-FREE GPU-load path on locked-sysfs Adreno devices (SM8750/HONOR etc.):
     *  (1) can the app open the KGSL device node? The sysfs mirror (/sys/class/kgsl) is SELinux-blocked
     *      (vendor_sysfs_kgsl), but /dev/kgsl-3d0 is labeled gpu_device and is normally app-openable —
     *      that's the door for reading GPU busy via ioctl perf-counters instead of sysfs.
     *  (2) is there an app-readable devfreq node under /sys/devices/platform that bypasses the blocked
     *      /sys/class/kgsl symlink (a free win for at least the clock). */
    private void appendGpuNoRootProbes(StringBuilder sb) {
        sb.append("-- GPU no-root reachability probes --\n");
        sb.append("  /dev/kgsl-3d0 open O_RDWR:   ")
          .append(probeOpen("/dev/kgsl-3d0", android.system.OsConstants.O_RDWR)).append('\n');
        sb.append("  /dev/kgsl-3d0 open O_RDONLY: ")
          .append(probeOpen("/dev/kgsl-3d0", android.system.OsConstants.O_RDONLY)).append('\n');
        sb.append("  /dev/dri/renderD128 O_RDWR:  ")
          .append(probeOpen("/dev/dri/renderD128", android.system.OsConstants.O_RDWR)).append('\n');
        boolean found = false;
        File[] roots = { new File("/sys/devices/platform/soc"), new File("/sys/devices/platform") };
        for (File root : roots) {
            File[] nodes = root.listFiles();
            if (nodes == null) continue;
            for (File soc : nodes) {
                if (!soc.getName().contains("kgsl-3d0")) continue;
                File[] inner = new File(soc, "devfreq").listFiles();
                if (inner == null) continue;
                for (File node : inner) {
                    found = true;
                    Long cur = readLongFromLine(node + "/cur_freq");
                    Long load = readLongFromLine(node + "/gpu_load");
                    if (load == null) load = readLongFromLine(node + "/load");
                    sb.append("  devfreq ").append(node.getAbsolutePath())
                      .append(": cur_freq=").append(cur == null ? "—" : cur.toString())
                      .append(" load=").append(load == null ? "—" : load.toString()).append('\n');
                }
            }
        }
        if (!found) sb.append("  devfreq (platform kgsl): (no app-readable node found)\n");
    }

    /** open()+close() a path with the given flags, reporting OK or the errno class — used to test
     *  whether the app's sandbox can reach a device node without root. */
    private static String probeOpen(String path, int flags) {
        java.io.FileDescriptor fd = null;
        try {
            fd = android.system.Os.open(path, flags, 0);
            return "OK";
        } catch (Throwable t) {
            return t.getClass().getSimpleName() + ": " + t.getMessage();
        } finally {
            if (fd != null) try { android.system.Os.close(fd); } catch (Throwable ignored) {}
        }
    }

    /** Battery-wattage inputs: which power_supply nodes this app can actually READ, and their raw
     *  values. Confirms the voltage_now / power_now fallback for devices where EXTRA_VOLTAGE is 0. */
    private void appendPowerSupplyDump(StringBuilder sb) {
        sb.append("-- /sys/class/power_supply (battery watts inputs) --\n");
        File[] nodes = new File("/sys/class/power_supply").listFiles();
        if (nodes == null || nodes.length == 0) { sb.append("  (absent / unreadable)\n"); return; }
        boolean any = false;
        for (File node : nodes) {
            Long v = readLongFromLine(node + "/voltage_now");
            Long c = readLongFromLine(node + "/current_now");
            Long p = readLongFromLine(node + "/power_now");
            if (v == null && c == null && p == null) continue;
            any = true;
            sb.append("  ").append(node.getName())
              .append(": voltage_now=").append(v == null ? "—" : v + "µV")
              .append(" current_now=").append(c == null ? "—" : c + "µA")
              .append(" power_now=").append(p == null ? "—" : p + "µW")
              .append('\n');
        }
        if (!any) sb.append("  (nodes present but voltage_now/current_now/power_now unreadable)\n");
    }

    private static void appendDevfreqListing(StringBuilder sb, String rootPath) {
        File root = new File(rootPath);
        File[] nodes = root.listFiles(File::isDirectory);
        sb.append("-- ").append(rootPath).append(" --\n");
        if (nodes == null || nodes.length == 0) { sb.append("  (absent / empty)\n"); return; }
        for (File node : nodes) {
            sb.append("  ").append(node.getName()).append("/: ").append(readableFileNames(node)).append('\n');
        }
    }

    private static void appendReadableFiles(StringBuilder sb, String dirPath) {
        File dir = new File(dirPath);
        sb.append("-- ").append(dirPath).append(" --\n");
        if (!dir.isDirectory()) { sb.append("  (absent / unreadable)\n"); return; }
        sb.append("  ").append(readableFileNames(dir)).append('\n');
    }

    private static String readableFileNames(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return "(unreadable)";
        StringBuilder names = new StringBuilder();
        for (File f : files) {
            if (f.isFile() && f.canRead()) {
                if (names.length() > 0) names.append(", ");
                names.append(f.getName());
            }
        }
        return names.length() == 0 ? "(no readable files)" : names.toString();
    }

    // =======================================================================
    // Low-level helpers
    // =======================================================================
    private static int clampPercent(int v) { return v < 0 ? 0 : (v > 100 ? 100 : v); }

    private static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private static String readNthLine(String path, int lineIndex) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            String line = null;
            for (int i = 0; i <= lineIndex; i++) {
                line = r.readLine();
                if (line == null) return null;
            }
            return line;
        } catch (Exception e) {
            return null;
        }
    }

    private static Long readLongFromLine(String path) {
        String line = readFirstLine(path);
        return line == null ? null : parseLong(line.trim());
    }

    private static Integer readIntFromLine(String path) {
        String line = readFirstLine(path);
        return line == null ? null : parseInt(line.trim());
    }

    private static Long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return null; }
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return null; }
    }
}
