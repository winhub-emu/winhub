package com.winhub.hernandez.core;

import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;

public abstract class ProcessHelper {
    public static final boolean PRINT_DEBUG = true; // FIXME change to false
    private static final ArrayList<Callback<String>> debugCallbacks = new ArrayList<>();
    private static final byte SIGCONT = 18;
    private static final byte SIGSTOP = 19;
    private static final byte SIGTERM = 15;
    private static final byte SIGKILL = 9;

    public static void suspendProcess(int pid) {
        Process.sendSignal(pid, SIGSTOP);
        Log.d("ProcessHelper", "Process suspended with pid: " + pid);
    }

    public static void resumeProcess(int pid) {
        Process.sendSignal(pid, SIGCONT);
        Log.d("ProcessHelper", "Process resumed with pid: " + pid);
    }

    public static void terminateProcess(int pid) {
        Process.sendSignal(pid, SIGTERM);
        Log.d("ProcessHelper", "Process terminated with pid: " + pid);
    }

    public static void killProcess(int pid) {
        Process.sendSignal(pid, SIGKILL);
        Log.d("ProcessHelper", "Process killed with pid: " + pid);
    }

    public static void terminateAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            terminateProcess(Integer.parseInt(process));
        }
    }

    /**
     * Number of live threads in the given process, i.e. the entry count of {@code /proc/<pid>/task}
     * (one sub-dir per tid). Returns {@code -1} when the process no longer exists (dir gone). The
     * winhandler pid is the Linux pid — the same one we already read {@code /proc/<pid>/cmdline} with
     * and that Ludashi reads {@code /proc/<pid>/status} with — so this resolves correctly.
     *
     * <p>Used as the affinity-drift trigger: a CPU affinity set via SetProcessAffinityMask only pins
     * the threads that exist at call time, and under wow64/FEX newly spawned game threads don't
     * inherit it. A jump in this count is the cheap, reliable signal that new (unpinned) threads
     * appeared and the mask must be re-applied — letting us re-pin ONLY on real change instead of on a
     * blind timer.
     */
    public static int getThreadCount(int pid) {
        String[] tids = new File("/proc/" + pid + "/task").list();
        return tids != null ? tids.length : -1;
    }

    /**
     * The process's current Linux affinity, read from {@code Cpus_allowed:} in
     * {@code /proc/<pid>/status} (main-thread/tgid view). This is the ground truth after a
     * SetProcessAffinityMask, whose Wine-reported mask is unreliable under wow64/FEX (approach
     * borrowed from Ludashi). Returns {@code 0} when unreadable. Note: this reflects the leader
     * thread only, so it is NOT sufficient on its own to detect per-worker-thread drift — thread
     * count is the trigger; this is available for verification/logging.
     */
    public static int getProcessAffinityMask(int pid) {
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream("/proc/" + pid + "/status")))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("Cpus_allowed:")) {
                    return (int) Long.parseLong(line.substring("Cpus_allowed:".length()).trim(), 16);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    /**
     * Resolve the game's real LINUX pid by matching {@code exeBasename} inside {@code /proc/<pid>/cmdline}.
     * The affinity/winhandler pids are Wine "Windows" pids that DON'T exist under {@code /proc} (device-
     * proven: winhandler pid 236/324 vs the real Linux pid 5062), so the drift checker can't read thread
     * info by them — this bridges to the real Linux pid. Wine sets argv[0] to the Windows exe path, so the
     * game's cmdline contains the exe name. Returns the match with the MOST threads (the engine, not a
     * helper/stub or a single-thread launcher), or {@code -1} if none. Only scans same-uid-readable
     * cmdlines, so most system procs are skipped cheaply.
     */
    public static int findLinuxPidByExe(String exeBasename) {
        if (exeBasename == null || exeBasename.isEmpty()) return -1;
        String needle = exeBasename.toLowerCase();
        File[] entries = new File("/proc").listFiles();
        if (entries == null) return -1;
        int best = -1, bestThreads = -1;
        for (File e : entries) {
            String name = e.getName();
            if (name.isEmpty() || !Character.isDigit(name.charAt(0))) continue;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(new FileInputStream("/proc/" + name + "/cmdline")))) {
                StringBuilder sb = new StringBuilder();
                int c;
                while ((c = r.read()) != -1 && sb.length() < 512) sb.append(c == 0 ? ' ' : (char) c);
                if (!sb.toString().toLowerCase().contains(needle)) continue;
                String[] tids = new File("/proc/" + name + "/task").list();
                int threads = tids != null ? tids.length : 0;
                if (threads > bestThreads) { bestThreads = threads; best = Integer.parseInt(name); }
            } catch (Exception ignored) {}
        }
        return best;
    }

    /**
     * Host-side affinity: pin ALL threads of a Linux process to {@code mask} via {@code taskset -a -p}
     * (toybox wants BARE hex — device-proven). The guest runs under the app's own uid, so this is same-uid
     * and needs no root. Crucially this reaches the native FEX/driver threads that the Windows-side
     * SetProcessAffinityMask cannot touch under wow64/FEX. Returns true when taskset exits 0.
     */
    public static boolean setLinuxAffinity(int linuxPid, int mask) {
        if (linuxPid <= 0 || mask == 0) return false;
        try {
            java.lang.Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/taskset", "-a", "-p", Integer.toHexString(mask & 0xff), Integer.toString(linuxPid)});
            return p.waitFor() == 0;
        } catch (Exception e) {
            Log.w("ProcessHelper", "setLinuxAffinity failed pid=" + linuxPid, e);
            return false;
        }
    }

    /**
     * Run {@code command} exactly like {@link #exec(String, String[], File, Callback)} (same env, same
     * debug-log capture) and block until it exits, or until {@code timeoutMs} has passed, in which case
     * the process is killed. Returns the exit status, or -1 when it could not be started or timed out.
     * Only for short, headless helpers on a worker thread (never the UI thread).
     */
    public static int execAndWait(String command, String[] envp, File workingDir, long timeoutMs) {
        return execAndWait(command, envp, workingDir, timeoutMs, null);
    }

    /** As above; [onStarted] gets the pid as soon as the process exists, so another thread can kill it. */
    public static int execAndWait(String command, String[] envp, File workingDir, long timeoutMs,
                                  java.util.function.IntConsumer onStarted) {
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        final int[] status = {-1};
        int pid = exec(command, envp, workingDir, (s) -> { status[0] = s; done.countDown(); });
        if (pid <= 0) return -1;
        if (onStarted != null) onStarted.accept(pid);
        try {
            if (!done.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                Log.w("ProcessHelper", "execAndWait: pid " + pid + " still running after " + timeoutMs + " ms, killing it: " + command);
                killProcess(pid);
                return -1;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
        return status[0];
    }

    /**
     * Gracefully terminate every wine process, resuming SIGSTOP'd ones first so a suspended guest can
     * answer the SIGTERM, waiting up to {@code graceMs} for a clean exit, then force-killing any
     * survivor when {@code forceKill} is set. Mirrors the teardown WinNative performs on task
     * removal. Idempotent: no-ops when no wine processes are running.
     */
    public static void terminateAllWineProcessesAndWait(int graceMs, boolean forceKill) {
        resumeAllWineProcesses();
        terminateAllWineProcesses();
        long start = System.currentTimeMillis();
        while (true) {
            // One scan per poll (the old loop scanned /proc with no sleep at all, so a stubborn
            // process pegged a core and hammered the kernel for the whole grace period).
            ArrayList<String> running = listRunningWineProcesses();
            if (running.isEmpty()) return;
            if (System.currentTimeMillis() - start >= graceMs) {
                if (forceKill) {
                    for (String process : running) {
                        killProcess(Integer.parseInt(process));
                    }
                }
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public static void pauseAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            suspendProcess(Integer.parseInt(process));
        }
    }

    public static void resumeAllWineProcesses() {
        for (String process : listRunningWineProcesses()) {
            resumeProcess(Integer.parseInt(process));
        }
    }

    public static int exec(String command) {
        return exec(command, null);
    }

    public static int exec(String command, String[] envp) {
        return exec(command, envp, null);
    }

    public static int exec(String command, String[] envp, File workingDir) {
        return exec(command, envp, workingDir, null);
    }

    public static int exec(String command, String[] envp, File workingDir, Callback<Integer> terminationCallback) {
        // Redact secrets (Steam refresh token / steamID / email) before this env dump hits raw logcat.
        Log.d("ProcessHelper", com.winhub.hernandez.store.SteamLogRedactor.redact("env: " + Arrays.toString(envp) + "\ncmd: " + command));

        // Store env vars for future use
        EnvironmentManager.setEnvVars(envp);

        int pid = -1;
        try {
            Log.d("ProcessHelper", "Splitting command: " + command);
            String[] splitCommand = splitCommand(command);
            Log.d("ProcessHelper", "Split command result: " + Arrays.toString(splitCommand));
            Log.d("ProcessHelper", "Starting process...");
            ProcessBuilder pb = new ProcessBuilder(splitCommand);
            pb.directory(workingDir);
            pb.environment().putAll(EnvironmentManager.getEnvVars());
            if (debugCallbacks.isEmpty()) {
                File null_file = new File("/dev/null");
                pb.redirectError(null_file);
                pb.redirectOutput(null_file);
            }
            java.lang.Process process = pb.start();

            // Accessing hidden field
            Log.d("ProcessHelper", "Accessing hidden field to get PID");
            Field pidField = process.getClass().getDeclaredField("pid");
            pidField.setAccessible(true);
            pid = pidField.getInt(process);
            pidField.setAccessible(false);
            Log.d("ProcessHelper", "Process started with pid: " + pid);

            if (!debugCallbacks.isEmpty()) {
                createDebugThread(process.getInputStream());
                createDebugThread(process.getErrorStream());
            }

            if (terminationCallback != null) createWaitForThread(process, terminationCallback);

        }
        catch (Exception e) {
            Log.e("ProcessHelper", "Error executing command: " + command, e);
        }
        return pid;
    }

    private static void createDebugThread(final InputStream inputStream) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (PRINT_DEBUG) System.out.println(line);
                    synchronized (debugCallbacks) {
                        if (!debugCallbacks.isEmpty()) {
                            for (Callback<String> callback : debugCallbacks) callback.call(line);
                        }
                    }
                }
            }
            catch (IOException e) {
                Log.e("ProcessHelper", "Error in debug thread", e);
            }
        });
    }

    private static void createWaitForThread(java.lang.Process process, final Callback<Integer> terminationCallback) {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int status = process.waitFor();
                    terminationCallback.call(status);
                }
                catch (InterruptedException e) {
                    Log.e("ProcessHelper", "Error waiting for process termination", e);
                }
            }
        });
    }

    public static void removeAllDebugCallbacks() {
        synchronized (debugCallbacks) {
            debugCallbacks.clear();
            Log.d("ProcessHelper", "All debug callbacks removed");
        }
    }

    public static void addDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            if (!debugCallbacks.contains(callback)) debugCallbacks.add(callback);
            Log.d("ProcessHelper", "Added debug callback: " + callback.toString());
        }
    }

    public static void removeDebugCallback(Callback<String> callback) {
        synchronized (debugCallbacks) {
            debugCallbacks.remove(callback);
            Log.d("ProcessHelper", "Removed debug callback: " + callback.toString());
        }
    }

    public static String[] splitCommand(String command) {
        ArrayList<String> result = new ArrayList<>();
        boolean startedQuotes = false;
        String value = "";
        char currChar, nextChar;
        for (int i = 0, count = command.length(); i < count; i++) {
            currChar = command.charAt(i);

            if (startedQuotes) {
                if (currChar == '"') {
                    startedQuotes = false;
                    if (!value.isEmpty()) {
                        value += '"';
                        result.add(value);
                        value = "";
                    }
                }
                else value += currChar;
            }
            else if (currChar == '"') {
                startedQuotes = true;
                value += '"';
            }
            else {
                nextChar = i < count-1 ? command.charAt(i+1) : '\0';
                if (currChar == ' ' || (currChar == '\\' && nextChar == ' ')) {
                    if (currChar == '\\') {
                        value += ' ';
                        i++;
                    }
                    else if (!value.isEmpty()) {
                        result.add(value);
                        value = "";
                    }
                }
                else {
                    value += currChar;
                    if (i == count-1) {
                        result.add(value);
                        value = "";
                    }
                }
            }
        }

        return result.toArray(new String[0]);
    }

    public static String getAffinityMaskAsHexString(String cpuList) {
        String[] values = cpuList.split(",");
        int affinityMask = 0;
        for (String value : values) {
            byte index = Byte.parseByte(value);
            affinityMask |= (int)Math.pow(2, index);
        }
        return Integer.toHexString(affinityMask);
    }

    public static int getAffinityMask(String cpuList) {
        if (cpuList == null || cpuList.isEmpty()) return 0;
        String[] values = cpuList.split(",");
        int affinityMask = 0;
        for (String value : values) {
            byte index = Byte.parseByte(value);
            affinityMask |= (int)Math.pow(2, index);
        }
        return affinityMask;
    }

    public static int getAffinityMask(boolean[] cpuList) {
        int affinityMask = 0;
        for (int i = 0; i < cpuList.length; i++) {
            if (cpuList[i]) affinityMask |= (int)Math.pow(2, i);
        }
        return affinityMask;
    }

    public static int getAffinityMask(int from, int to) {
        int affinityMask = 0;
        for (int i = from; i < to; i++) affinityMask |= (int)Math.pow(2, i);
        return affinityMask;
    }

    public static ArrayList<String> listRunningWineProcesses(){
        File proc = new File("/proc");
        String[] filters = {"wine", "exe"};
        String[] allPids;
        ArrayList<String> filteredPids = new ArrayList<String>();
        List<String> filterList = Arrays.asList(filters);
        allPids = proc.list(new FilenameFilter(){
            public boolean accept(File proc, String filename){
                return new File(proc, filename).isDirectory() && filename.matches("[0-9]+");
            }
        });

        if (allPids == null) return filteredPids;

        for (int index = 0; index < allPids.length; index++){
            String data = "";
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    new FileInputStream(proc + "/" + allPids[index] + "/stat")))) {
                String line = br.readLine();
                if (line != null) data = line;
            }
            catch (IOException e) {}
            // The comm field in /proc/<pid>/stat is truncated to 15 chars (TASK_COMM_LEN), so a game whose
            // exe name pushes ".exe" past char 15 (e.g. "NINJA GAIDEN SIGMA.exe") never matched the filter
            // and so was never paused/suspended — it kept running (and playing audio) while backgrounded or
            // manually paused. Also match the FULL, untruncated argv from /proc/<pid>/cmdline (same source
            // findLinuxPidByExe already uses) so the game engine is caught too. Additive: a pid the stat
            // check already matched is still matched — we only ever ADD the previously-missed process.
            // Read cmdline only when stat did not already match, so the common system process costs one
            // small read instead of two.
            boolean matched = false;
            for (String filter : filterList) {
                if (data.contains(filter)) { matched = true; break; }
            }
            if (!matched) {
                String cmdline = readCmdline(proc, allPids[index]);
                data = data + " " + cmdline;
                for (String filter : filterList) {
                    if (data.contains(filter)) { matched = true; break; }
                }
            }
            if (matched) filteredPids.add(allPids[index]);
        }
        return filteredPids;
    }

    // Read /proc/<pid>/cmdline (NUL-separated argv) as a space-joined string. Returns "" if unreadable
    // (other-uid or gone). Same-uid guest processes launched by wine carry their full exe path here,
    // untruncated — unlike the 15-char comm in /proc/<pid>/stat.
    private static String readCmdline(File proc, String pid) {
        try (FileInputStream fr = new FileInputStream(proc + "/" + pid + "/cmdline")) {
            byte[] buf = new byte[512];
            int n = fr.read(buf);
            if (n <= 0) return "";
            StringBuilder sb = new StringBuilder(n);
            for (int i = 0; i < n; i++) sb.append(buf[i] == 0 ? ' ' : (char) buf[i]);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
