/*
 * HERMES-XInput: an XInput DLL that reads the pad straight from the HERMES slot files.
 *
 * WHY
 *   Wine's own xinput reads HID reports that winebus queues per handle (32 by default, up to 512)
 *   and drops the oldest: a game that polls slower than the pad reports plays back stale stick
 *   positions. XInput is a "current state" API, so this DLL answers XInputGetState from the live
 *   slot file the app publishes (one 4 KiB file per pad slot, see ../include/hermes.h and
 *   ../core/src/layout.rs): no queue, no report rate, nothing to fall behind.
 *
 * STATE
 *   On first use the DLL parses FAKE_EVDEV_MEMFD_PATHS ("<slot>=<unix path>;...", Wine imports it
 *   from the unix environment), turns each unix path into a DOS path (kernel32's
 *   wine_get_dos_file_name, then "\\?\unix\...", then "Z:\..."), opens it read-only with full
 *   sharing and maps a read-only view. Wine maps read-only file views MAP_SHARED, so every app
 *   publish is visible in the view at once (the CI test measures it). XInput user N = slot N.
 *   A read follows core/src/proto.rs exactly (hxi_core.h). A slot is connected while its file is
 *   valid, not retired, and the app marks the slot connected. A retired, replaced or missing file
 *   is re-opened lazily, at most once a second per slot; meanwhile the slot reports
 *   ERROR_DEVICE_NOT_CONNECTED. Nothing here blocks (except XInputWaitForGuideButton, which is
 *   asked to) or allocates per call; a bad argument is refused, never dereferenced.
 *
 * RUMBLE
 *   XInputSetState sends a 12-byte UDP datagram to the app's WinHandler on 127.0.0.1:7947
 *   (HERMES_XINPUT_RUMBLE_PORT overrides): code 14, "HXR1", slot, low-frequency motor, high-
 *   frequency motor (u16 LE each, the raw 0..65535 XInput values), duration ms. WinHandler hands it
 *   to the same per-slot vibration path the evdev force-feedback rumble uses. Only changes are
 *   sent (a stop at once, other changes at most every 8 ms; the next XInput call flushes a held
 *   one), plus a refresh every 1.5 s while a motor runs, because the app's vibration is a one-shot
 *   of 3 s: if the game dies mid-rumble, the motor stops by itself. Winsock is loaded on the first
 *   rumble only; a game that never rumbles never touches it.
 *
 * MODES (live, from the slot files' control block; format minor 5, see hxi_core.h)
 *   The app writes xinput_mode / xinput_flags into the control block of every slot file; this DLL
 *   reads slot 0's. DIRECT (unset or 1): the above. FORWARD (2, only with FORWARD_AVAILABLE): every
 *   call goes to Wine's own xinput, a renamed copy of its builtin xinput1_4.dll the app installs as
 *   system32\hermes_wine_xinput.dll (loaded on the first forward request, never in DllMain;
 *   GetAudioDeviceIds / DSound GUIDs stay local, 101-103 are built on its GetStateEx). XI_HIDDEN (any
 *   mode): every user is not connected and rumble is dropped. Every call checks ctl_gen (one acquire
 *   load); a change is applied under an exclusive lock: the motors stop on the old path, the copy is
 *   told the game's XInputEnable (entering forward) or XInputEnable(FALSE) (leaving it), per-slot state
 *   resets, dwPacketNumber stays monotonic, one log line, and a 21-byte status datagram (code 16,
 *   "HXS1") tells the app the change is applied. GetCapabilitiesEx reports the slot's identity from
 *   its control block when the app marked it valid. Without a usable block: exactly as before.
 *
 * DIAGNOSTICS
 *   One line when a process loads the DLL (DllMain: process image, DLL name, which build, version),
 *   one per slot attach / loss. Under Wine they go through ntdll's __wine_dbg_output, which writes to
 *   the process's unix stderr whatever WINEDEBUG says and whether or not the process has Windows std
 *   handles (a GUI game started by explorer has none), so they land in the app's Wine log. Elsewhere,
 *   or when asked: the Windows stderr handle, or OutputDebugStringA (Wine: WINEDEBUG=+debugstr).
 *
 * BUILDS
 *   x86_64 and i386 for x86_64 Wine (system32 / syswow64). In an arm64ec Wine, system32 holds an
 *   ARM64X image: the same source compiled for ARM64 and for ARM64EC and linked into one file, so
 *   Wine's own native ARM64 programs (control.exe/joy.cpl, ...) load its ARM64 half and x64 games
 *   its ARM64EC half; an ARM64EC-only image fails to load in an ARM64 process (c000007b).
 *
 * ENVIRONMENT
 *   HERMES_XINPUT_LOG=0         no diagnostics (default: one line per load, attach and loss)
 *   HERMES_XINPUT_LOGVIA        auto (default) | wine | stderr | ods: where diagnostics go
 *   HERMES_XINPUT_PATHMODE      auto (default) | wine | unix | z: which DOS path form to use
 *   HERMES_XINPUT_RUMBLE_PORT   UDP port of the rumble listener (default 7947)
 */
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

#include "hxi_core.h"

#ifndef HXI_VERSION
#define HXI_VERSION "1.3"
#endif

#if defined(__arm64ec__) || defined(_M_ARM64EC)
#define HXI_ARCH "arm64ec"
#elif defined(__x86_64__)
#define HXI_ARCH "x86_64"
#elif defined(__i386__)
#define HXI_ARCH "i386"
#elif defined(__aarch64__)
#define HXI_ARCH "aarch64"
#else
#define HXI_ARCH "unknown"
#endif
/* -DHXI_HYBRID=1: this object is one half of the ARM64X image (build.sh). */
#if defined(HXI_HYBRID) && HXI_HYBRID
#define HXI_BUILD HXI_ARCH " half of ARM64X"
#else
#define HXI_BUILD HXI_ARCH
#endif

/* The public XInput types, laid out as the API documents them. */
typedef struct {
    WORD wButtons;
    BYTE bLeftTrigger;
    BYTE bRightTrigger;
    SHORT sThumbLX;
    SHORT sThumbLY;
    SHORT sThumbRX;
    SHORT sThumbRY;
} HXI_GAMEPAD;

typedef struct {
    DWORD dwPacketNumber;
    HXI_GAMEPAD Gamepad;
} HXI_STATE;

typedef struct {
    WORD wLeftMotorSpeed;
    WORD wRightMotorSpeed;
} HXI_VIBRATION;

typedef struct {
    BYTE Type;
    BYTE SubType;
    WORD Flags;
    HXI_GAMEPAD Gamepad;
    HXI_VIBRATION Vibration;
} HXI_CAPABILITIES;

typedef struct {
    HXI_CAPABILITIES Capabilities;
    WORD VendorId;
    WORD ProductId;
    WORD VersionNumber;
    WORD unk1;
    DWORD unk2;
} HXI_CAPABILITIES_EX;

typedef struct {
    BYTE BatteryType;
    BYTE BatteryLevel;
} HXI_BATTERY_INFORMATION;

typedef struct {
    WORD VirtualKey;
    WCHAR Unicode;
    WORD Flags;
    BYTE UserIndex;
    BYTE HidCode;
} HXI_KEYSTROKE;

_Static_assert(sizeof(HXI_GAMEPAD) == 12, "XINPUT_GAMEPAD");
_Static_assert(sizeof(HXI_STATE) == 16, "XINPUT_STATE");
_Static_assert(sizeof(HXI_VIBRATION) == 4, "XINPUT_VIBRATION");
_Static_assert(sizeof(HXI_CAPABILITIES) == 20, "XINPUT_CAPABILITIES");
_Static_assert(sizeof(HXI_CAPABILITIES_EX) == 32, "XINPUT_CAPABILITIES_EX");
_Static_assert(sizeof(HXI_BATTERY_INFORMATION) == 2, "XINPUT_BATTERY_INFORMATION");
_Static_assert(sizeof(HXI_KEYSTROKE) == 8, "XINPUT_KEYSTROKE");

#define HXI_SLOTS 4u
#define HXI_USER_ANY 0xffu
#define HXI_FLAG_GAMEPAD 0x1u
#define HXI_DEVTYPE_GAMEPAD 0x01
#define HXI_DEVSUBTYPE_GAMEPAD 0x01
#define HXI_BATTERY_DEVTYPE_GAMEPAD 0x00
#define HXI_BATTERY_DEVTYPE_HEADSET 0x01
#define HXI_BATTERY_TYPE_DISCONNECTED 0x00
#define HXI_BATTERY_TYPE_WIRED 0x01
#define HXI_BATTERY_LEVEL_EMPTY 0x00
#define HXI_BATTERY_LEVEL_FULL 0x03
#define HXI_KEYSTROKE_KEYDOWN 0x0001
#define HXI_KEYSTROKE_KEYUP 0x0002
#define HXI_TRIGGER_THRESHOLD 30
#define HXI_THUMB_KEY_THRESHOLD 20000
/* What XInputGetCapabilitiesEx reports: the Xbox 360 wired pad the evdev side presents too
 * (FAKE_EVDEV_IDENTITY=xbox360). */
#define HXI_VID 0x045e
#define HXI_PID 0x028e
#define HXI_PVER 0x0114

#define HXI_REOPEN_MS 1000u
#define HXI_SNAP_ATTEMPTS 64u
#define HXI_RUMBLE_CODE 14
#define HXI_RUMBLE_PORT 7947
#define HXI_RUMBLE_DURATION_MS 3000u
#define HXI_RUMBLE_REFRESH_MS 1500u
#define HXI_RUMBLE_MIN_GAP_MS 8u

enum { PATH_AUTO, PATH_WINE, PATH_UNIX, PATH_Z };

typedef struct hxi_slot {
    SRWLOCK lock;            /* held (exclusive) for every read: the view is never unmapped under a reader */
    uint32_t index;
    int configured;
    char *unix_path;         /* UTF-8 */
    const uint8_t *view;     /* read-only MAP_SHARED view of the slot file, or NULL */
    uint64_t instance;
    ULONGLONG next_open;     /* earliest next open attempt (GetTickCount64), 0 = now */
    int missing_logged;
    uint32_t words[HXI_COPY_WORDS];
    int have_words;
    /* XInputGetState / GetStateEx */
    uint32_t seen_counts[HXI_BUTTONS];
    uint64_t seen_instance;
    int seen_valid;
    uint32_t key_pub;
    uint64_t key_instance;
    uint16_t key_taps;
    int key_valid;
    DWORD packet;
    /* XInputGetKeystroke */
    uint32_t ks_mask;
    WORD ks_ldir, ks_rdir;
    int ks_valid;
    /* rumble */
    WORD want_l, want_r, sent_l, sent_r;
    ULONGLONG sent_at;
    int sent_valid;
    /* rumble diagnostics (rate-limited log lines) */
    int set_logged, logged_on;
    DWORD motor_log_tick, nc_log_tick;
    unsigned motor_suppressed, nc_suppressed;
    /* forward mode: the copy's last packet number (ours stays monotonic across modes) */
    DWORD fwd_packet;
    int fwd_valid;
    volatile LONG guide_cancel;
} hxi_slot;

static hxi_slot g_slots[HXI_SLOTS];
static INIT_ONCE g_init = INIT_ONCE_STATIC_INIT;
static HMODULE g_module;
static volatile LONG g_enabled = 1;
static char g_dllname[64] = "xinput";
static int g_pathmode = PATH_AUTO;
static unsigned g_rumble_port = HXI_RUMBLE_PORT;
static uint32_t g_mask;
typedef WCHAR *(CDECL *wine_dos_fn)(const char *);
static wine_dos_fn g_wine_dos;

/* ---------------------------------------------------------------------------------------------- */
/* diagnostics */

enum { LOG_OFF, LOG_WINE, LOG_STDERR, LOG_ODS };
static const char *const LOG_NAMES[] = {"off", "wine_dbg", "stderr", "ods"};
static INIT_ONCE g_log_init = INIT_ONCE_STATIC_INIT;
static int g_logvia = LOG_OFF;
typedef int(CDECL *wine_dbg_output_fn)(const char *);
static wine_dbg_output_fn g_wine_dbg_output;

static int env_is(const WCHAR *name, const WCHAR *value);

/* Safe in DllMain: environment reads, GetModuleHandle and GetProcAddress of an already loaded DLL. */
static BOOL CALLBACK log_once(PINIT_ONCE once, PVOID param, PVOID *ctx) {
    (void)once; (void)param; (void)ctx;
    HMODULE nt = GetModuleHandleW(L"ntdll.dll");
    if (nt) g_wine_dbg_output = (wine_dbg_output_fn)(void (*)(void))GetProcAddress(nt, "__wine_dbg_output");
    if (env_is(L"HERMES_XINPUT_LOG", L"0")) g_logvia = LOG_OFF;
    else if (env_is(L"HERMES_XINPUT_LOGVIA", L"stderr")) g_logvia = LOG_STDERR;
    else if (env_is(L"HERMES_XINPUT_LOGVIA", L"ods")) g_logvia = LOG_ODS;
    else if (g_wine_dbg_output) g_logvia = LOG_WINE;
    else g_logvia = LOG_STDERR;
    return TRUE;
}

static void hxi_log(const char *fmt, ...) {
    InitOnceExecuteOnce(&g_log_init, log_once, NULL, NULL);
    if (g_logvia == LOG_OFF) return;
    char buf[1400];
    int n = snprintf(buf, sizeof buf, "HERMES-XInput: ");
    va_list ap;
    va_start(ap, fmt);
    int m = vsnprintf(buf + n, sizeof buf - (size_t)n - 2, fmt, ap);
    va_end(ap);
    if (m < 0) return;
    n += m;
    if (n > (int)sizeof buf - 2) n = (int)sizeof buf - 2;
    buf[n++] = '\n';
    buf[n] = 0;
    switch (g_logvia) {
    case LOG_WINE:
        g_wine_dbg_output(buf);
        break;
    case LOG_ODS:
        OutputDebugStringA(buf);
        break;
    default: {
        HANDLE h = GetStdHandle(STD_ERROR_HANDLE);
        if (h && h != INVALID_HANDLE_VALUE) {
            DWORD w;
            WriteFile(h, buf, (DWORD)n, &w, NULL);
        }
    }
    }
}

/* The line every process that loads the DLL writes once (DllMain, PROCESS_ATTACH). */
static void log_attach(HMODULE self) {
    InitOnceExecuteOnce(&g_log_init, log_once, NULL, NULL); /* the channel name below needs it */
    if (g_logvia == LOG_OFF) return;
    WCHAR w[MAX_PATH];
    char image[MAX_PATH * 3] = "?", dll[MAX_PATH] = "?";
    DWORD n = GetModuleFileNameW(NULL, w, MAX_PATH);
    if (n && n < MAX_PATH) WideCharToMultiByte(CP_UTF8, 0, w, -1, image, (int)sizeof image, NULL, NULL);
    n = GetModuleFileNameW(self, w, MAX_PATH);
    if (n && n < MAX_PATH) {
        const WCHAR *b = w + n;
        while (b > w && b[-1] != '\\' && b[-1] != '/') b--;
        WideCharToMultiByte(CP_UTF8, 0, b, -1, dll, (int)sizeof dll, NULL, NULL);
    }
    image[sizeof image - 1] = 0;
    dll[sizeof dll - 1] = 0;
    lstrcpynA(g_dllname, dll, (int)sizeof g_dllname);
    hxi_log("%s %s (%s) loaded into %s (pid %lu) [log via %s]", HXI_VERSION, dll, HXI_BUILD, image,
            (unsigned long)GetCurrentProcessId(), LOG_NAMES[g_logvia]);
}

/* ---------------------------------------------------------------------------------------------- */
/* configuration */

static WCHAR *env_dup(const WCHAR *name) {
    DWORD n = GetEnvironmentVariableW(name, NULL, 0);
    if (!n) return NULL;
    WCHAR *v = HeapAlloc(GetProcessHeap(), 0, (SIZE_T)(n + 1) * sizeof(WCHAR));
    if (!v) return NULL;
    DWORD got = GetEnvironmentVariableW(name, v, n + 1);
    if (!got || got > n) {
        HeapFree(GetProcessHeap(), 0, v);
        return NULL;
    }
    v[got] = 0;
    return v;
}

static int env_is(const WCHAR *name, const WCHAR *value) {
    WCHAR *v = env_dup(name);
    int r = v && lstrcmpiW(v, value) == 0;
    if (v) HeapFree(GetProcessHeap(), 0, v);
    return r;
}

HXI_DEFINE_PARSE_PATHS(hxi_parse_paths_w, WCHAR)

static void take_path(void *ctx, uint32_t slot, const WCHAR *p, size_t len) {
    (void)ctx;
    int n = WideCharToMultiByte(CP_UTF8, 0, p, (int)len, NULL, 0, NULL, NULL);
    if (n <= 0) return;
    char *u = HeapAlloc(GetProcessHeap(), 0, (SIZE_T)n + 1);
    if (!u) return;
    WideCharToMultiByte(CP_UTF8, 0, p, (int)len, u, n, NULL, NULL);
    u[n] = 0;
    g_slots[slot].unix_path = u;
    g_slots[slot].configured = 1;
    g_mask |= 1u << slot;
}

static BOOL CALLBACK init_once(PINIT_ONCE once, PVOID param, PVOID *ctx) {
    (void)once; (void)param; (void)ctx;
    for (uint32_t i = 0; i < HXI_SLOTS; i++) g_slots[i].index = i;
    if (env_is(L"HERMES_XINPUT_PATHMODE", L"wine")) g_pathmode = PATH_WINE;
    else if (env_is(L"HERMES_XINPUT_PATHMODE", L"unix")) g_pathmode = PATH_UNIX;
    else if (env_is(L"HERMES_XINPUT_PATHMODE", L"z")) g_pathmode = PATH_Z;
    WCHAR *port = env_dup(L"HERMES_XINPUT_RUMBLE_PORT");
    if (port) {
        unsigned v = 0;
        const WCHAR *c = port;
        while (*c >= '0' && *c <= '9' && v < 65536u) v = v * 10u + (unsigned)(*c++ - '0');
        if (!*c && v > 0 && v < 65536u) g_rumble_port = v;
        HeapFree(GetProcessHeap(), 0, port);
    }
    HMODULE k32 = GetModuleHandleW(L"kernel32.dll");
    if (k32) g_wine_dos = (wine_dos_fn)(void (*)(void))GetProcAddress(k32, "wine_get_dos_file_name");
    WCHAR *paths = env_dup(L"FAKE_EVDEV_MEMFD_PATHS");
    if (paths) {
        hxi_parse_paths_w(paths, take_path, NULL);
        HeapFree(GetProcessHeap(), 0, paths);
    }
    char dll[MAX_PATH] = "?";
    if (g_module) {
        WCHAR w[MAX_PATH];
        DWORD n = GetModuleFileNameW(g_module, w, MAX_PATH);
        if (n && n < MAX_PATH) {
            const WCHAR *b = w + n;
            while (b > w && b[-1] != '\\' && b[-1] != '/') b--;
            WideCharToMultiByte(CP_UTF8, 0, b, -1, dll, (int)sizeof dll, NULL, NULL);
        }
    }
    static const char *const modes[] = {"auto", "wine", "unix", "z"};
    hxi_log("%s %s (%s) slots 0x%x from FAKE_EVDEV_MEMFD_PATHS, paths %s%s, rumble udp %u",
            HXI_VERSION, dll, HXI_BUILD, (unsigned)g_mask, modes[g_pathmode],
            g_wine_dos ? "" : " (no wine_get_dos_file_name)", g_rumble_port);
    return TRUE;
}

static void hxi_init(void) {
    InitOnceExecuteOnce(&g_init, init_once, NULL, NULL);
}

/* ---------------------------------------------------------------------------------------------- */
/* slot files */

/* `prefix` + the unix path with '/' turned into '\'. */
static WCHAR *dos_prefixed(const char *u, const WCHAR *prefix) {
    int n = MultiByteToWideChar(CP_UTF8, 0, u, -1, NULL, 0);
    if (n <= 0) return NULL;
    size_t pl = (size_t)lstrlenW(prefix);
    WCHAR *r = HeapAlloc(GetProcessHeap(), 0, (pl + (size_t)n + 1) * sizeof(WCHAR));
    if (!r) return NULL;
    memcpy(r, prefix, pl * sizeof(WCHAR));
    MultiByteToWideChar(CP_UTF8, 0, u, -1, r + pl, n);
    for (WCHAR *c = r + pl; *c; c++)
        if (*c == '/') *c = '\\';
    return r;
}

static WCHAR *dos_path(const char *u, int mode) {
    switch (mode) {
    case PATH_WINE: {
        if (!g_wine_dos) return NULL;
        WCHAR *w = g_wine_dos(u); /* process-heap memory; freed by the caller */
        return w;
    }
    case PATH_UNIX:
        return dos_prefixed(u, L"\\\\?\\unix");
    case PATH_Z:
        return dos_prefixed(u, L"Z:");
    }
    return NULL;
}

/* Map `path` read-only as slot `slot`'s file: 1 = *view is a valid live slot file, 0 = could not
 * open/map it, -1 = there but not a live slot file. */
static int map_path(const WCHAR *path, uint32_t slot, const uint8_t **view) {
    HANDLE f = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL,
                           OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return 0;
    LARGE_INTEGER size;
    if (!GetFileSizeEx(f, &size) || size.QuadPart < (LONGLONG)HXI_FILE_SIZE) {
        CloseHandle(f);
        return -1;
    }
    HANDLE m = CreateFileMappingW(f, NULL, PAGE_READONLY, 0, HXI_FILE_SIZE, NULL);
    CloseHandle(f);
    if (!m) return 0;
    const uint8_t *v = MapViewOfFile(m, FILE_MAP_READ, 0, 0, HXI_FILE_SIZE);
    CloseHandle(m); /* the view keeps the section alive */
    if (!v) return 0;
    if (!hxi_valid(v, slot) || hxi_retired(v)) {
        UnmapViewOfFile(v);
        return -1;
    }
    *view = v;
    return 1;
}

/* 1 = mapped and valid, 0 = could not open/map it, -1 = there but not a live slot file. */
static int try_open(hxi_slot *s, const WCHAR *path) {
    const uint8_t *v = NULL;
    int r = map_path(path, s->index, &v);
    if (r != 1) return r;
    s->view = v;
    s->instance = hxi_instance(v);
    s->have_words = 0;
    return 1;
}

/* A separate read-only view of slot `s`'s current file (the control block's anchor), or NULL. */
static const uint8_t *map_slot_view(const hxi_slot *s) {
    static const int order[] = {PATH_WINE, PATH_UNIX, PATH_Z};
    for (int i = 0; i < 3; i++) {
        if (g_pathmode != PATH_AUTO && order[i] != g_pathmode) continue;
        WCHAR *p = dos_path(s->unix_path, order[i]);
        if (!p) continue;
        const uint8_t *v = NULL;
        int r = map_path(p, s->index, &v);
        HeapFree(GetProcessHeap(), 0, p);
        if (r == 1) return v;
        if (r < 0) break;
    }
    return NULL;
}

static int slot_open(hxi_slot *s) {
    static const int order[] = {PATH_WINE, PATH_UNIX, PATH_Z};
    int last = 0;
    for (int i = 0; i < 3; i++) {
        int mode = order[i];
        if (g_pathmode != PATH_AUTO && mode != g_pathmode) continue;
        WCHAR *p = dos_path(s->unix_path, mode);
        if (!p) continue;
        last = try_open(s, p);
        if (last == 1) {
            char shown[1024];
            WideCharToMultiByte(CP_UTF8, 0, p, -1, shown, (int)sizeof shown, NULL, NULL);
            shown[sizeof shown - 1] = 0;
            hxi_log("slot %u attached: %s (instance %08x%08x, writer pid %u)", s->index, shown,
                    (unsigned)(s->instance >> 32), (unsigned)s->instance, (unsigned)hxi_ld(s->view, HXI_H_WRITER_PID));
            s->missing_logged = 0;
            HeapFree(GetProcessHeap(), 0, p);
            return 1;
        }
        HeapFree(GetProcessHeap(), 0, p);
        if (last < 0) break; /* the file is there but not live: another path form names the same file */
    }
    if (!s->missing_logged) {
        hxi_log("slot %u: %s %s; not connected, retrying once a second", s->index, s->unix_path,
                last < 0 ? "is not a live slot file (retired or foreign)" : "cannot be opened yet");
        s->missing_logged = 1;
    }
    return 0;
}

static void slot_close(hxi_slot *s) {
    if (s->view) UnmapViewOfFile(s->view);
    s->view = NULL;
    s->have_words = 0;
}

/* Under s->lock. The current published state into w; returns 1 if the slot is connected. */
static int slot_read(hxi_slot *s, uint32_t w[HXI_COPY_WORDS]) {
    if (!s->configured) return 0;
    if (s->view && hxi_retired(s->view)) {
        hxi_log("slot %u: the app retired its file (session end or a new file), re-opening", s->index);
        slot_close(s);
    }
    if (!s->view) {
        ULONGLONG now = GetTickCount64();
        if (s->next_open && now < s->next_open) return 0;
        s->next_open = now + HXI_REOPEN_MS;
        if (!slot_open(s)) return 0;
    }
    if (hxi_snapshot(s->view, w, HXI_SNAP_ATTEMPTS)) {
        memcpy(s->words, w, sizeof s->words);
        s->have_words = 1;
    } else if (s->have_words) {
        memcpy(w, s->words, sizeof s->words); /* the writer kept lapping this read: last good state */
    } else {
        return 0;
    }
    return (w[HXI_W_FLAGS] & HXI_FLAG_CONNECTED) != 0;
}

/* ---------------------------------------------------------------------------------------------- */
/* rumble */

typedef int(WSAAPI *wsastartup_fn)(WORD, LPWSADATA);
typedef SOCKET(WSAAPI *socket_fn)(int, int, int);
typedef int(WSAAPI *ioctlsocket_fn)(SOCKET, long, u_long *);
typedef int(WSAAPI *sendto_fn)(SOCKET, const char *, int, int, const struct sockaddr *, int);
typedef int(WSAAPI *wsaerror_fn)(void);

static INIT_ONCE g_net_init = INIT_ONCE_STATIC_INIT;
static SOCKET g_sock = INVALID_SOCKET;
static sendto_fn g_sendto;
static wsaerror_fn g_wsaerror;
static struct sockaddr_in g_dst;
static volatile LONG g_send_failures;
static volatile LONG g_send_fail_tick;

static BOOL CALLBACK net_once(PINIT_ONCE once, PVOID param, PVOID *ctx) {
    (void)once; (void)param; (void)ctx;
    HMODULE ws = LoadLibraryW(L"ws2_32.dll");
    if (!ws) {
        hxi_log("rumble: ws2_32.dll not available, rumble off");
        return TRUE;
    }
    wsastartup_fn startup = (wsastartup_fn)(void (*)(void))GetProcAddress(ws, "WSAStartup");
    socket_fn mksock = (socket_fn)(void (*)(void))GetProcAddress(ws, "socket");
    ioctlsocket_fn ioctl_p = (ioctlsocket_fn)(void (*)(void))GetProcAddress(ws, "ioctlsocket");
    sendto_fn sendto_p = (sendto_fn)(void (*)(void))GetProcAddress(ws, "sendto");
    g_wsaerror = (wsaerror_fn)(void (*)(void))GetProcAddress(ws, "WSAGetLastError");
    WSADATA wd;
    if (!startup || !mksock || !ioctl_p || !sendto_p || startup(MAKEWORD(2, 2), &wd) != 0) {
        hxi_log("rumble: winsock unusable, rumble off");
        return TRUE;
    }
    SOCKET s = mksock(AF_INET, SOCK_DGRAM, IPPROTO_UDP);
    if (s == INVALID_SOCKET) {
        hxi_log("rumble: no UDP socket, rumble off");
        return TRUE;
    }
    u_long nb = 1;
    ioctl_p(s, (long)FIONBIO, &nb);
    memset(&g_dst, 0, sizeof g_dst);
    g_dst.sin_family = AF_INET;
    g_dst.sin_port = (u_short)(((g_rumble_port & 0xffu) << 8) | ((g_rumble_port >> 8) & 0xffu));
    g_dst.sin_addr.s_addr = 0x0100007fu; /* 127.0.0.1, network order */
    g_sendto = sendto_p;
    g_sock = s;
    hxi_log("winsock loaded for the first rumble or mode status (%s, %s), datagrams to 127.0.0.1:%u", g_dllname,
            HXI_BUILD, g_rumble_port);
    return TRUE;
}

/* 1 if the datagram went out. A failure is logged (the first one, then at most every 10 s). */
static int rumble_send(uint32_t slot, WORD l, WORD r) {
    InitOnceExecuteOnce(&g_net_init, net_once, NULL, NULL);
    if (g_sock == INVALID_SOCKET) return 0;
    unsigned char p[12] = {
        HXI_RUMBLE_CODE, 'H', 'X', 'R', '1', (unsigned char)slot,
        (unsigned char)(l & 0xff), (unsigned char)(l >> 8), (unsigned char)(r & 0xff), (unsigned char)(r >> 8),
        (unsigned char)(HXI_RUMBLE_DURATION_MS & 0xff), (unsigned char)(HXI_RUMBLE_DURATION_MS >> 8),
    };
    if (g_sendto(g_sock, (const char *)p, (int)sizeof p, 0, (const struct sockaddr *)&g_dst, (int)sizeof g_dst) != SOCKET_ERROR)
        return 1;
    int err = g_wsaerror ? g_wsaerror() : -1;
    LONG n = InterlockedIncrement(&g_send_failures);
    DWORD t = GetTickCount(), last = (DWORD)__atomic_load_n(&g_send_fail_tick, __ATOMIC_RELAXED);
    if (n == 1 || t - last >= 10000u) {
        __atomic_store_n(&g_send_fail_tick, (LONG)t, __ATOMIC_RELAXED);
        hxi_log("rumble: sendto 127.0.0.1:%u failed for slot %u, WSAGetLastError %d (%ld failure(s) so far)", g_rumble_port,
                slot, err, (long)n);
    }
    return 0;
}

/* Under s->lock: whether the motors should be told something now, and what. */
static int rumble_due(hxi_slot *s, WORD *l, WORD *r) {
    int on = __atomic_load_n(&g_enabled, __ATOMIC_ACQUIRE) != 0;
    WORD wl = on ? s->want_l : 0, wr = on ? s->want_r : 0;
    int active = wl || wr;
    ULONGLONG now = GetTickCount64();
    if (!s->sent_valid) {
        if (!active) return 0;
    } else if (wl == s->sent_l && wr == s->sent_r) {
        if (!active || now - s->sent_at < HXI_RUMBLE_REFRESH_MS) return 0;
    } else if (active && now - s->sent_at < HXI_RUMBLE_MIN_GAP_MS) {
        return 0;
    }
    s->sent_l = wl;
    s->sent_r = wr;
    s->sent_at = now;
    s->sent_valid = 1;
    *l = wl;
    *r = wr;
    return 1;
}

/* What a caller does about the motors once it has dropped the slot lock: send, and log a motors
 * on/off change (at most one such line per slot every 500 ms, the skipped ones counted). */
typedef struct {
    int send, note;
    WORD l, r;
    unsigned suppressed;
} rumble_job;

static void rumble_plan(hxi_slot *s, rumble_job *j) { /* under s->lock */
    memset(j, 0, sizeof *j);
    j->send = rumble_due(s, &j->l, &j->r);
    if (!j->send) return;
    int on = j->l || j->r;
    if (on == s->logged_on) return;
    DWORD t = GetTickCount();
    if (s->motor_log_tick && t - s->motor_log_tick < 500u) {
        s->motor_suppressed++;
        return;
    }
    j->note = 1;
    j->suppressed = s->motor_suppressed;
    s->motor_suppressed = 0;
    s->logged_on = on;
    s->motor_log_tick = t ? t : 1;
}

static void rumble_run(uint32_t slot, const rumble_job *j) {
    if (!j->send) return;
    int ok = rumble_send(slot, j->l, j->r);
    if (!j->note) return;
    char more[64] = "";
    if (j->suppressed) snprintf(more, sizeof more, "; %u on/off change(s) before this not logged", j->suppressed);
    hxi_log("slot %u: motors %s (low %u, high %u): datagram %s%s", slot, (j->l || j->r) ? "ON" : "off", (unsigned)j->l,
            (unsigned)j->r, ok ? "sent" : "NOT sent", more);
}

/* ---------------------------------------------------------------------------------------------- */
/* reads */

static void to_gamepad(const hxi_pad *p, HXI_GAMEPAD *g) {
    g->wButtons = p->buttons;
    g->bLeftTrigger = p->lt;
    g->bRightTrigger = p->rt;
    g->sThumbLX = p->lx;
    g->sThumbLY = p->ly;
    g->sThumbRX = p->rx;
    g->sThumbRY = p->ry;
}

static DWORD get_state(DWORD idx, HXI_STATE *st, int guide) {
    if (idx >= HXI_SLOTS || !st) return ERROR_BAD_ARGUMENTS;
    hxi_init();
    hxi_slot *s = &g_slots[idx];
    uint32_t w[HXI_COPY_WORDS];
    DWORD ret;
    AcquireSRWLockExclusive(&s->lock);
    if (slot_read(s, w)) {
        uint16_t taps = 0;
        if (s->seen_valid && s->seen_instance == s->instance) taps = hxi_missed_taps(w, s->seen_counts);
        for (uint32_t b = 0; b < HXI_BUTTONS; b++) s->seen_counts[b] = w[HXI_W_COUNTS + b];
        s->seen_instance = s->instance;
        s->seen_valid = 1;
        /* A new packet number for every new publish, a new file, and for a surfaced tap (and the
         * read after it, which shows the button up again). */
        if (!s->key_valid || s->key_pub != w[HXI_W_PUB] || s->key_instance != s->instance || s->key_taps != taps) {
            s->packet++;
            s->key_pub = w[HXI_W_PUB];
            s->key_instance = s->instance;
            s->key_taps = taps;
            s->key_valid = 1;
        }
        hxi_pad p;
        hxi_map(w, (uint16_t)(hxi_pressed(w) | taps), guide, &p);
        if (!__atomic_load_n(&g_enabled, __ATOMIC_ACQUIRE)) memset(&p, 0, sizeof p);
        st->dwPacketNumber = s->packet;
        to_gamepad(&p, &st->Gamepad);
        ret = ERROR_SUCCESS;
    } else {
        s->seen_valid = 0;
        s->want_l = s->want_r = 0;
        ret = ERROR_DEVICE_NOT_CONNECTED;
    }
    rumble_job job;
    rumble_plan(s, &job);
    ReleaseSRWLockExclusive(&s->lock);
    rumble_run(idx, &job);
    return ret;
}

/* Connected or not, for the APIs that need nothing else (takes the slot lock itself). */
static int connected(DWORD idx) {
    hxi_init();
    hxi_slot *s = &g_slots[idx];
    uint32_t w[HXI_COPY_WORDS];
    AcquireSRWLockExclusive(&s->lock);
    int c = slot_read(s, w);
    ReleaseSRWLockExclusive(&s->lock);
    return c;
}

/* ---------------------------------------------------------------------------------------------- */
/* the direct path (HERMES slot files); the exports below pick it or the forward path */

static DWORD direct_set_state(DWORD dwUserIndex, HXI_VIBRATION *pVibration) {
    hxi_slot *s = &g_slots[dwUserIndex];
    uint32_t w[HXI_COPY_WORDS];
    WORD vl = pVibration->wLeftMotorSpeed, vr = pVibration->wRightMotorSpeed;
    DWORD ret = ERROR_SUCCESS;
    int first = 0, nc = 0;
    unsigned nc_more = 0;
    AcquireSRWLockExclusive(&s->lock);
    int conn = slot_read(s, w);
    if (conn) {
        s->want_l = vl;
        s->want_r = vr;
    } else {
        s->want_l = s->want_r = 0;
        ret = ERROR_DEVICE_NOT_CONNECTED;
    }
    /* diagnostics: the first call per slot; then calls on a slot without a controller, at most one
     * line per slot every 30 s (games poll all four slots every frame) */
    DWORD t = GetTickCount();
    if (!s->set_logged) {
        s->set_logged = 1;
        first = 1;
        s->nc_log_tick = t;
    } else if (!conn) {
        if (t - s->nc_log_tick >= 30000u) {
            nc = 1;
            nc_more = s->nc_suppressed;
            s->nc_suppressed = 0;
            s->nc_log_tick = t;
        } else {
            s->nc_suppressed++;
        }
    }
    rumble_job job;
    rumble_plan(s, &job);
    ReleaseSRWLockExclusive(&s->lock);
    if (first)
        hxi_log("slot %u: first XInputSetState in this process (%s, %s): low %u, high %u -> %s%s", (unsigned)dwUserIndex,
                g_dllname, HXI_BUILD, (unsigned)vl, (unsigned)vr,
                conn ? "ERROR_SUCCESS" : "ERROR_DEVICE_NOT_CONNECTED (no controller on this slot)",
                __atomic_load_n(&g_enabled, __ATOMIC_ACQUIRE) ? "" : " (XInputEnable(FALSE) in force: motors held off)");
    if (nc)
        hxi_log("slot %u: XInputSetState(low %u, high %u) on a slot with no controller -> ERROR_DEVICE_NOT_CONNECTED"
                " (%u more such call(s) in the last 30 s)", (unsigned)dwUserIndex, (unsigned)vl, (unsigned)vr, nc_more);
    rumble_run(dwUserIndex, &job);
    return ret;
}

/* After an XInputEnable change in direct mode: hold the motors off, or resume what the game set. */
static void direct_enable(void) {
    for (uint32_t i = 0; i < HXI_SLOTS; i++) {
        hxi_slot *s = &g_slots[i];
        if (!s->configured) continue;
        rumble_job job;
        AcquireSRWLockExclusive(&s->lock);
        rumble_plan(s, &job);
        ReleaseSRWLockExclusive(&s->lock);
        rumble_run(i, &job);
    }
}

static void fill_caps(HXI_CAPABILITIES *c) {
    memset(c, 0, sizeof *c);
    c->Type = HXI_DEVTYPE_GAMEPAD;
    c->SubType = HXI_DEVSUBTYPE_GAMEPAD;
    c->Flags = 0;
    c->Gamepad.wButtons = 0xf3ff; /* every button but Guide (and the reserved 0x0800) */
    c->Gamepad.bLeftTrigger = 0xff;
    c->Gamepad.bRightTrigger = 0xff;
    c->Gamepad.sThumbLX = (SHORT)0xffc0;
    c->Gamepad.sThumbLY = (SHORT)0xffc0;
    c->Gamepad.sThumbRX = (SHORT)0xffc0;
    c->Gamepad.sThumbRY = (SHORT)0xffc0;
    c->Vibration.wLeftMotorSpeed = 0xffff;
    c->Vibration.wRightMotorSpeed = 0xffff;
}

static DWORD direct_caps(DWORD dwUserIndex, HXI_CAPABILITIES *pCapabilities) {
    if (!connected(dwUserIndex)) return ERROR_DEVICE_NOT_CONNECTED;
    fill_caps(pCapabilities);
    return ERROR_SUCCESS;
}

/* The identity XInputGetCapabilitiesEx reports for slot `idx`: the slot file's control-block identity
 * when the app marked it valid, else the Xbox 360 pad. */
static void slot_identity(DWORD idx, WORD *vid, WORD *pid, WORD *ver) {
    *vid = HXI_VID;
    *pid = HXI_PID;
    *ver = HXI_PVER;
    hxi_slot *s = &g_slots[idx];
    uint32_t w[HXI_COPY_WORDS];
    AcquireSRWLockExclusive(&s->lock);
    if (slot_read(s, w) && s->view && hxi_ctl_present(s->view)) {
        hxi_ctl c;
        if (hxi_ctl_read(s->view, &c, HXI_SNAP_ATTEMPTS) && (c.id_flags & HXI_IDF_VALID)) {
            *vid = (WORD)c.id_vendor;
            *pid = (WORD)c.id_product;
            *ver = (WORD)c.id_version;
        }
    }
    ReleaseSRWLockExclusive(&s->lock);
}

static DWORD direct_caps_ex(DWORD dwUserIndex, HXI_CAPABILITIES_EX *pCapabilities) {
    if (!connected(dwUserIndex)) return ERROR_DEVICE_NOT_CONNECTED;
    memset(pCapabilities, 0, sizeof *pCapabilities);
    fill_caps(&pCapabilities->Capabilities);
    slot_identity(dwUserIndex, &pCapabilities->VendorId, &pCapabilities->ProductId, &pCapabilities->VersionNumber);
    return ERROR_SUCCESS;
}

/* Battery for a pad `conn` says is connected or not (both modes). */
static DWORD battery_for(int conn, BYTE devType, HXI_BATTERY_INFORMATION *pInfo) {
    if (!conn) return ERROR_DEVICE_NOT_CONNECTED;
    if (devType == HXI_BATTERY_DEVTYPE_GAMEPAD) {
        pInfo->BatteryType = HXI_BATTERY_TYPE_WIRED;
        pInfo->BatteryLevel = HXI_BATTERY_LEVEL_FULL;
    } else {
        pInfo->BatteryType = HXI_BATTERY_TYPE_DISCONNECTED; /* no headset */
        pInfo->BatteryLevel = HXI_BATTERY_LEVEL_EMPTY;
    }
    return ERROR_SUCCESS;
}

static DWORD dsound_for(int conn, GUID *pRender, GUID *pCapture) {
    if (!conn) return ERROR_DEVICE_NOT_CONNECTED;
    memset(pRender, 0, sizeof *pRender); /* GUID_NULL: no headset */
    memset(pCapture, 0, sizeof *pCapture);
    return ERROR_SUCCESS;
}

static DWORD audio_ids_for(int conn, LPWSTR pRenderDeviceId, UINT *pRenderCount, LPWSTR pCaptureDeviceId,
                           UINT *pCaptureCount) {
    if (!conn) return ERROR_DEVICE_NOT_CONNECTED;
    if (pRenderCount) {
        if (pRenderDeviceId && *pRenderCount) pRenderDeviceId[0] = 0;
        *pRenderCount = 0;
    }
    if (pCaptureCount) {
        if (pCaptureDeviceId && *pCaptureCount) pCaptureDeviceId[0] = 0;
        *pCaptureCount = 0;
    }
    return ERROR_SUCCESS;
}

/* XInputGetKeystroke: one key event per call, from the difference between the state this slot's
 * keystroke reader last reported and the current one. */
static const WORD KEY_VK[16] = {
    0x5800, 0x5801, 0x5802, 0x5803, /* VK_PAD_A B X Y */
    0x5804, 0x5805, 0x5806, 0x5807, /* RSHOULDER LSHOULDER LTRIGGER RTRIGGER */
    0x5810, 0x5811, 0x5812, 0x5813, /* DPAD_UP DOWN LEFT RIGHT */
    0x5814, 0x5815, 0x5816, 0x5817, /* START BACK LTHUMB_PRESS RTHUMB_PRESS */
};

static uint32_t key_mask(const hxi_pad *p) {
    uint32_t m = 0;
    if (p->buttons & HXI_XI_A) m |= 1u << 0;
    if (p->buttons & HXI_XI_B) m |= 1u << 1;
    if (p->buttons & HXI_XI_X) m |= 1u << 2;
    if (p->buttons & HXI_XI_Y) m |= 1u << 3;
    if (p->buttons & HXI_XI_RIGHT_SHOULDER) m |= 1u << 4;
    if (p->buttons & HXI_XI_LEFT_SHOULDER) m |= 1u << 5;
    if (p->lt > HXI_TRIGGER_THRESHOLD) m |= 1u << 6;
    if (p->rt > HXI_TRIGGER_THRESHOLD) m |= 1u << 7;
    if (p->buttons & HXI_XI_DPAD_UP) m |= 1u << 8;
    if (p->buttons & HXI_XI_DPAD_DOWN) m |= 1u << 9;
    if (p->buttons & HXI_XI_DPAD_LEFT) m |= 1u << 10;
    if (p->buttons & HXI_XI_DPAD_RIGHT) m |= 1u << 11;
    if (p->buttons & HXI_XI_START) m |= 1u << 12;
    if (p->buttons & HXI_XI_BACK) m |= 1u << 13;
    if (p->buttons & HXI_XI_LEFT_THUMB) m |= 1u << 14;
    if (p->buttons & HXI_XI_RIGHT_THUMB) m |= 1u << 15;
    return m;
}

/* VK_PAD_xTHUMB_UP, DOWN, RIGHT, LEFT, UPLEFT, UPRIGHT, DOWNRIGHT, DOWNLEFT = base + 0..7 */
static WORD thumb_key(int16_t x, int16_t y, WORD base) {
    int up = y > HXI_THUMB_KEY_THRESHOLD, down = y < -HXI_THUMB_KEY_THRESHOLD;
    int right = x > HXI_THUMB_KEY_THRESHOLD, left = x < -HXI_THUMB_KEY_THRESHOLD;
    if (up && left) return (WORD)(base + 4);
    if (up && right) return (WORD)(base + 5);
    if (down && right) return (WORD)(base + 6);
    if (down && left) return (WORD)(base + 7);
    if (up) return base;
    if (down) return (WORD)(base + 1);
    if (right) return (WORD)(base + 2);
    if (left) return (WORD)(base + 3);
    return 0;
}

static void key_event(HXI_KEYSTROKE *k, WORD vk, int down, DWORD idx) {
    k->VirtualKey = vk;
    k->Unicode = 0;
    k->Flags = down ? HXI_KEYSTROKE_KEYDOWN : HXI_KEYSTROKE_KEYUP;
    k->UserIndex = (BYTE)idx;
    k->HidCode = 0;
}

static DWORD keystroke_one(DWORD idx, HXI_KEYSTROKE *k) {
    hxi_slot *s = &g_slots[idx];
    uint32_t w[HXI_COPY_WORDS];
    DWORD ret = ERROR_EMPTY;
    AcquireSRWLockExclusive(&s->lock);
    if (!slot_read(s, w)) {
        s->ks_valid = 0;
        ReleaseSRWLockExclusive(&s->lock);
        return ERROR_DEVICE_NOT_CONNECTED;
    }
    hxi_pad p;
    hxi_map(w, hxi_pressed(w), 0, &p);
    if (!__atomic_load_n(&g_enabled, __ATOMIC_ACQUIRE)) memset(&p, 0, sizeof p);
    if (!s->ks_valid) {
        s->ks_mask = 0;
        s->ks_ldir = s->ks_rdir = 0;
        s->ks_valid = 1;
    }
    uint32_t cur = key_mask(&p), diff = cur ^ s->ks_mask;
    WORD ld = thumb_key(p.lx, p.ly, 0x5820), rd = thumb_key(p.rx, p.ry, 0x5830);
    if (diff) {
        uint32_t b = 0;
        while (!(diff & (1u << b))) b++;
        s->ks_mask ^= 1u << b;
        key_event(k, KEY_VK[b], (cur >> b) & 1u, idx);
        ret = ERROR_SUCCESS;
    } else if (ld != s->ks_ldir) {
        if (s->ks_ldir) {
            key_event(k, s->ks_ldir, 0, idx);
            s->ks_ldir = 0;
        } else {
            key_event(k, ld, 1, idx);
            s->ks_ldir = ld;
        }
        ret = ERROR_SUCCESS;
    } else if (rd != s->ks_rdir) {
        if (s->ks_rdir) {
            key_event(k, s->ks_rdir, 0, idx);
            s->ks_rdir = 0;
        } else {
            key_event(k, rd, 1, idx);
            s->ks_rdir = rd;
        }
        ret = ERROR_SUCCESS;
    }
    ReleaseSRWLockExclusive(&s->lock);
    return ret;
}

static DWORD direct_keystroke(DWORD dwUserIndex, HXI_KEYSTROKE *pKeystroke) {
    if (dwUserIndex == HXI_USER_ANY) {
        int any = 0;
        for (DWORD i = 0; i < HXI_SLOTS; i++) {
            DWORD r = keystroke_one(i, pKeystroke);
            if (r == ERROR_SUCCESS) return r;
            if (r == ERROR_EMPTY) any = 1;
        }
        return any ? ERROR_EMPTY : ERROR_DEVICE_NOT_CONNECTED;
    }
    return keystroke_one(dwUserIndex, pKeystroke);
}

/* ---------------------------------------------------------------------------------------------- */
/* live mode: the control block, Wine's own xinput as the forward target, switching */

enum { MODE_DIRECT = 1, MODE_FORWARD = 2 };
#define HXI_STATUS_CODE 16
#define HXI_CTL_ATTEMPTS 64u
#define HXI_CTL_ABSENT_RECHECK_MS 1000u

/* Wine's xinput (a renamed copy of its builtin xinput1_4.dll the app puts next to ours). */
typedef DWORD(WINAPI *fx_state_fn)(DWORD, void *);
typedef DWORD(WINAPI *fx_set_fn)(DWORD, HXI_VIBRATION *);
typedef DWORD(WINAPI *fx_caps_fn)(DWORD, DWORD, HXI_CAPABILITIES *);
typedef DWORD(WINAPI *fx_caps_ex_fn)(DWORD, DWORD, DWORD, HXI_CAPABILITIES_EX *);
typedef void(WINAPI *fx_enable_fn)(BOOL);
typedef DWORD(WINAPI *fx_battery_fn)(DWORD, BYTE, HXI_BATTERY_INFORMATION *);
typedef DWORD(WINAPI *fx_keystroke_fn)(DWORD, DWORD, HXI_KEYSTROKE *);

static struct {
    INIT_ONCE once;    /* zero = INIT_ONCE_STATIC_INIT */
    volatile LONG tried;
    int ok;
    HMODULE h;
    fx_state_fn get_state, get_state_ex;
    fx_set_fn set_state;
    fx_caps_fn caps;
    fx_caps_ex_fn caps_ex;
    fx_enable_fn enable;
    fx_battery_fn battery;
    fx_keystroke_fn keystroke;
} g_fwd;

/* SHARED for every API call, EXCLUSIVE while a control change is applied: a switch never runs while
 * a call is inside either path, and a call never sees a half-switched DLL. Order: g_mode_lock, then
 * a slot lock. */
static SRWLOCK g_mode_lock = SRWLOCK_INIT;
static LONG g_mode = MODE_DIRECT; /* written under the exclusive lock (and with release) */
static LONG g_hidden;

static struct {
    const uint8_t *view;        /* own read-only view of slot 0's file: the control anchor */
    ULONGLONG next_open;        /* earliest (re)open of the anchor */
    ULONGLONG next_absent_check;
    int present;                /* -1 unknown, 0 no usable block (pre-minor-5 file), 1 block */
    uint32_t gen;               /* gen of the last applied block */
    int applied;                /* applied at least once in this process */
    hxi_ctl last;               /* last good copy */
} g_ctl;

/* 1 if the file at `path` is one of our own DLLs (it carries our marker). */
static int file_is_ours(const WCHAR *path) {
    HANDLE f = CreateFileW(path, GENERIC_READ, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL, OPEN_EXISTING,
                           FILE_ATTRIBUTE_NORMAL, NULL);
    if (f == INVALID_HANDLE_VALUE) return 0;
    static const char marker[] = "HERMES-XInput";
    char *buf = HeapAlloc(GetProcessHeap(), 0, 65536 + sizeof marker);
    if (!buf) {
        CloseHandle(f);
        return 0;
    }
    size_t keep = 0;
    int found = 0;
    for (;;) {
        DWORD got = 0;
        if (!ReadFile(f, buf + keep, 65536, &got, NULL) || !got) break;
        size_t n = keep + got;
        for (size_t i = 0; i + sizeof marker - 1 <= n && !found; i++)
            if (buf[i] == 'B' && !memcmp(buf + i, marker, sizeof marker - 1)) found = 1;
        if (found) break;
        keep = sizeof marker - 1 < n ? sizeof marker - 1 : n;
        memmove(buf, buf + n - keep, keep);
    }
    HeapFree(GetProcessHeap(), 0, buf);
    CloseHandle(f);
    return found;
}

/* Load and resolve the forward target (never from DllMain: the first forward-mode request does it,
 * with no lock of ours held). */
static void fwd_load(void) {
    HMODULE h = LoadLibraryExW(L"hermes_wine_xinput.dll", NULL, LOAD_LIBRARY_SEARCH_SYSTEM32);
    if (!h) {
        hxi_log("forward: hermes_wine_xinput.dll does not load from system32 (error %lu); staying direct",
                (unsigned long)GetLastError());
        return;
    }
    WCHAR path[MAX_PATH];
    char shown[MAX_PATH * 3] = "?";
    DWORD n = GetModuleFileNameW(h, path, MAX_PATH);
    if (n && n < MAX_PATH) WideCharToMultiByte(CP_UTF8, 0, path, -1, shown, (int)sizeof shown, NULL, NULL);
    shown[sizeof shown - 1] = 0;
    if (h == g_module || (n && n < MAX_PATH && file_is_ours(path))) {
        hxi_log("forward: %s is one of our own DLLs, not Wine's xinput; staying direct", shown);
        FreeLibrary(h);
        return;
    }
    g_fwd.get_state = (fx_state_fn)(void (*)(void))GetProcAddress(h, "XInputGetState");
    g_fwd.set_state = (fx_set_fn)(void (*)(void))GetProcAddress(h, "XInputSetState");
    g_fwd.caps = (fx_caps_fn)(void (*)(void))GetProcAddress(h, "XInputGetCapabilities");
    g_fwd.enable = (fx_enable_fn)(void (*)(void))GetProcAddress(h, "XInputEnable");
    g_fwd.battery = (fx_battery_fn)(void (*)(void))GetProcAddress(h, "XInputGetBatteryInformation");
    g_fwd.keystroke = (fx_keystroke_fn)(void (*)(void))GetProcAddress(h, "XInputGetKeystroke");
    g_fwd.get_state_ex = (fx_state_fn)(void (*)(void))GetProcAddress(h, (LPCSTR)(ULONG_PTR)100);
    g_fwd.caps_ex = (fx_caps_ex_fn)(void (*)(void))GetProcAddress(h, (LPCSTR)(ULONG_PTR)108);
    if (!g_fwd.get_state || !g_fwd.set_state || !g_fwd.caps || !g_fwd.enable || !g_fwd.get_state_ex) {
        hxi_log("forward: %s lacks XInputGetState/SetState/GetCapabilities/Enable/#100; staying direct", shown);
        FreeLibrary(h);
        return;
    }
    g_fwd.h = h;
    g_fwd.ok = 1;
    hxi_log("forward: Wine's xinput loaded from %s (#108 %s, battery %s, keystroke %s)", shown,
            g_fwd.caps_ex ? "yes" : "no", g_fwd.battery ? "yes" : "no", g_fwd.keystroke ? "yes" : "no");
}

static BOOL CALLBACK fwd_once(PINIT_ONCE once, PVOID param, PVOID *ctx) {
    (void)once; (void)param; (void)ctx;
    fwd_load();
    __atomic_store_n(&g_fwd.tried, 1, __ATOMIC_RELEASE);
    return TRUE;
}

static int fwd_tried(void) {
    return __atomic_load_n(&g_fwd.tried, __ATOMIC_ACQUIRE) != 0;
}

/* What api_enter does once it has dropped the exclusive lock. */
typedef struct {
    int load_fwd;
    int status, log, switched;
    uint32_t req_mode, flags, gen;
    int mode, hidden, ok, old_mode, old_hidden;
    int zero[HXI_SLOTS];
} ctl_job;

/* Under the shared (or exclusive) lock: does a control change wait to be applied? The common case is
 * one acquire load of ctl_gen plus one of the retired flag. */
static int ctl_dirty(void) {
    if (!g_slots[0].configured) return 0;
    const uint8_t *v = g_ctl.view;
    if (!v) return GetTickCount64() >= g_ctl.next_open;
    if (hxi_retired(v)) return 1;
    if (g_ctl.present == 1) return hxi_ctl_gen(v) != g_ctl.gen;
    if (g_ctl.present < 0) return 1;
    return GetTickCount64() >= g_ctl.next_absent_check; /* no block yet: look again once a second */
}

/* Exclusive lock held. Stop the motors on the old path, re-arm the new one, swap. */
static void switch_mode(int new_mode, int new_hidden, ctl_job *job) {
    int old_mode = g_mode;
    for (uint32_t i = 0; i < HXI_SLOTS; i++) {
        hxi_slot *s = &g_slots[i];
        AcquireSRWLockExclusive(&s->lock);
        if (old_mode == MODE_DIRECT && s->sent_valid && (s->sent_l || s->sent_r)) job->zero[i] = 1;
        s->want_l = s->want_r = 0;
        s->sent_l = s->sent_r = 0;
        s->sent_valid = 0;
        s->seen_valid = 0;
        s->key_valid = 0;   /* the next direct read is a new packet */
        s->ks_valid = 0;
        s->fwd_valid = 0;
        s->packet++;        /* never repeats: monotonic across modes */
        ReleaseSRWLockExclusive(&s->lock);
    }
    if (old_mode == MODE_FORWARD && g_fwd.ok) {
        HXI_VIBRATION z = {0, 0};
        for (uint32_t i = 0; i < HXI_SLOTS; i++) g_fwd.set_state(i, &z);
    }
    if (g_fwd.ok) {
        if (new_mode == MODE_FORWARD && old_mode != MODE_FORWARD)
            g_fwd.enable(__atomic_load_n(&g_enabled, __ATOMIC_ACQUIRE) ? TRUE : FALSE); /* the game's last value */
        else if (new_mode == MODE_DIRECT && old_mode == MODE_FORWARD)
            g_fwd.enable(FALSE); /* it stops reading its HID devices */
    }
    __atomic_store_n(&g_mode, new_mode, __ATOMIC_RELEASE);
    __atomic_store_n(&g_hidden, new_hidden, __ATOMIC_RELEASE);
}

/* Exclusive lock held: (re)open the anchor, read the block, decide, switch. */
static void ctl_apply(ctl_job *job) {
    ULONGLONG now = GetTickCount64();
    if (g_ctl.view && hxi_retired(g_ctl.view)) {
        UnmapViewOfFile(g_ctl.view);
        g_ctl.view = NULL;
        g_ctl.next_open = 0;
    }
    if (!g_ctl.view) {
        if (now < g_ctl.next_open) return;
        g_ctl.next_open = now + HXI_REOPEN_MS;
        g_ctl.view = map_slot_view(&g_slots[0]);
        if (!g_ctl.view) return; /* keep the mode we have */
        g_ctl.present = -1;
    }
    int present = hxi_ctl_present(g_ctl.view);
    hxi_ctl c;
    memset(&c, 0, sizeof c);
    if (present) {
        if (g_ctl.present == 1 && hxi_ctl_gen(g_ctl.view) == g_ctl.gen) return;
        if (!hxi_ctl_read(g_ctl.view, &c, HXI_CTL_ATTEMPTS)) return; /* overtaken: last good copy stays */
    } else {
        g_ctl.next_absent_check = now + HXI_CTL_ABSENT_RECHECK_MS;
        if (g_ctl.present == 0) return;
    }
    int want_fwd = present && c.xi_mode == HXI_XI_MODE_FORWARD;
    int fwd_allowed = want_fwd && (c.xi_flags & HXI_XIF_FORWARD_AVAILABLE);
    if (fwd_allowed && !fwd_tried()) {
        job->load_fwd = 1; /* loaded with no lock held, then applied */
        return;
    }
    int new_mode = fwd_allowed && g_fwd.ok ? MODE_FORWARD : MODE_DIRECT;
    int new_hidden = present && (c.xi_flags & HXI_XIF_HIDDEN) ? 1 : 0;
    int first = !g_ctl.applied;
    g_ctl.present = present;
    g_ctl.gen = c.gen;
    g_ctl.last = c;
    g_ctl.applied = 1;
    job->old_mode = g_mode;
    job->old_hidden = g_hidden;
    if (new_mode != g_mode || new_hidden != g_hidden) {
        switch_mode(new_mode, new_hidden, job);
        job->switched = 1;
    }
    job->mode = new_mode;
    job->hidden = new_hidden;
    job->req_mode = c.xi_mode;
    job->flags = c.xi_flags;
    job->gen = c.gen;
    job->ok = !want_fwd || new_mode == MODE_FORWARD;
    job->log = present && (!first || job->switched || c.xi_mode || c.xi_flags || (c.id_flags & HXI_IDF_VALID));
    job->status = !first && present; /* a change during the session: tell the app it is applied */
}

static void send_status(const ctl_job *j) {
    InitOnceExecuteOnce(&g_net_init, net_once, NULL, NULL);
    if (g_sock == INVALID_SOCKET) return;
    DWORD pid = GetCurrentProcessId();
    unsigned char build =
#if defined(HXI_HYBRID) && HXI_HYBRID && (defined(__arm64ec__) || defined(_M_ARM64EC))
        3;
#elif defined(HXI_HYBRID) && HXI_HYBRID
        4;
#elif defined(__x86_64__)
        1;
#else
        2;
#endif
    unsigned char p[21] = {
        HXI_STATUS_CODE, 'H', 'X', 'S', '1', (unsigned char)j->req_mode, (unsigned char)j->mode, (unsigned char)j->ok,
        (unsigned char)j->gen, (unsigned char)(j->gen >> 8), (unsigned char)(j->gen >> 16), (unsigned char)(j->gen >> 24),
        (unsigned char)j->flags, (unsigned char)(j->flags >> 8), (unsigned char)(j->flags >> 16), (unsigned char)(j->flags >> 24),
        (unsigned char)pid, (unsigned char)(pid >> 8), (unsigned char)(pid >> 16), (unsigned char)(pid >> 24), build,
    };
    if (g_sendto(g_sock, (const char *)p, (int)sizeof p, 0, (const struct sockaddr *)&g_dst, (int)sizeof g_dst) == SOCKET_ERROR)
        hxi_log("status: sendto failed, WSAGetLastError %d", g_wsaerror ? g_wsaerror() : -1);
}

static const char *mode_name(int m) {
    return m == MODE_FORWARD ? "forward (Wine's xinput)" : "direct";
}

/* No lock held: the work a control change asks for outside the lock. */
static void ctl_after(const ctl_job *j) {
    if (j->load_fwd) {
        InitOnceExecuteOnce(&g_fwd.once, fwd_once, NULL, NULL);
        return;
    }
    for (uint32_t i = 0; i < HXI_SLOTS; i++)
        if (j->zero[i]) rumble_send(i, 0, 0); /* the motors the direct path had running */
    if (j->log)
        hxi_log("control gen %u: mode %s%s%s (requested %u, flags 0x%x)%s", j->gen, mode_name(j->mode),
                j->hidden ? ", XInput hidden" : "", j->switched ? " [switched]" : "", j->req_mode, j->flags,
                j->ok ? "" : " - forward requested but Wine's xinput is unavailable");
    if (j->status) send_status(j);
}

/* Every exported call: apply a pending control change, then hold the mode lock shared. */
static void api_enter(void) {
    hxi_init();
    for (int round = 0; round < 3; round++) {
        AcquireSRWLockShared(&g_mode_lock);
        if (!ctl_dirty()) return;
        ReleaseSRWLockShared(&g_mode_lock);
        ctl_job job;
        memset(&job, 0, sizeof job);
        AcquireSRWLockExclusive(&g_mode_lock);
        ctl_apply(&job);
        ReleaseSRWLockExclusive(&g_mode_lock);
        ctl_after(&job);
        if (!job.load_fwd) break;
    }
    AcquireSRWLockShared(&g_mode_lock);
}

static void api_leave(void) {
    ReleaseSRWLockShared(&g_mode_lock);
}

/* Forward mode: Wine's state, with our own (monotonic) packet number. */
static DWORD fwd_state(DWORD idx, HXI_STATE *st, int guide) {
    struct { HXI_STATE s; DWORD pad[4]; } t; /* GetStateEx may write XINPUT_STATE_EX */
    memset(&t, 0, sizeof t);
    DWORD r = guide ? g_fwd.get_state_ex(idx, &t) : g_fwd.get_state(idx, &t);
    if (r != ERROR_SUCCESS) return r;
    if (!guide) t.s.Gamepad.wButtons &= (WORD)~HXI_XI_GUIDE;
    hxi_slot *s = &g_slots[idx];
    AcquireSRWLockExclusive(&s->lock);
    if (!s->fwd_valid || s->fwd_packet != t.s.dwPacketNumber) {
        s->packet++;
        s->fwd_packet = t.s.dwPacketNumber;
        s->fwd_valid = 1;
    }
    st->dwPacketNumber = s->packet;
    ReleaseSRWLockExclusive(&s->lock);
    st->Gamepad = t.s.Gamepad;
    return ERROR_SUCCESS;
}

static int fwd_connected(DWORD idx) {
    struct { HXI_STATE s; DWORD pad[4]; } t;
    return g_fwd.get_state(idx, &t) == ERROR_SUCCESS;
}

/* Connected in the current mode (mode lock held shared). */
static int mode_connected(DWORD idx) {
    if (g_hidden) return 0;
    return g_mode == MODE_FORWARD ? fwd_connected(idx) : connected(idx);
}

/* ---------------------------------------------------------------------------------------------- */
/* the exports */

static DWORD api_state(DWORD idx, HXI_STATE *st, int guide) {
    if (idx >= HXI_SLOTS || !st) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = g_hidden ? ERROR_DEVICE_NOT_CONNECTED
            : g_mode == MODE_FORWARD ? fwd_state(idx, st, guide)
            : get_state(idx, st, guide);
    api_leave();
    return r;
}

DWORD WINAPI XInputGetState(DWORD dwUserIndex, HXI_STATE *pState) {
    return api_state(dwUserIndex, pState, 0);
}

DWORD WINAPI XInputGetStateEx(DWORD dwUserIndex, HXI_STATE *pState) {
    return api_state(dwUserIndex, pState, 1);
}

DWORD WINAPI XInputSetState(DWORD dwUserIndex, HXI_VIBRATION *pVibration) {
    if (dwUserIndex >= HXI_SLOTS || !pVibration) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = g_hidden ? ERROR_DEVICE_NOT_CONNECTED /* rumble dropped */
            : g_mode == MODE_FORWARD ? g_fwd.set_state(dwUserIndex, pVibration)
            : direct_set_state(dwUserIndex, pVibration);
    api_leave();
    return r;
}

void WINAPI XInputEnable(BOOL enable) {
    api_enter();
    __atomic_store_n(&g_enabled, enable ? 1 : 0, __ATOMIC_RELEASE);
    if (g_mode == MODE_FORWARD) g_fwd.enable(enable ? TRUE : FALSE);
    else if (!g_hidden) direct_enable();
    api_leave();
}

DWORD WINAPI XInputGetCapabilities(DWORD dwUserIndex, DWORD dwFlags, HXI_CAPABILITIES *pCapabilities) {
    if (dwUserIndex >= HXI_SLOTS || !pCapabilities || (dwFlags & ~HXI_FLAG_GAMEPAD)) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = g_hidden ? ERROR_DEVICE_NOT_CONNECTED
            : g_mode == MODE_FORWARD ? g_fwd.caps(dwUserIndex, dwFlags, pCapabilities)
            : direct_caps(dwUserIndex, pCapabilities);
    api_leave();
    return r;
}

DWORD WINAPI XInputGetCapabilitiesEx(DWORD dwReserved, DWORD dwUserIndex, DWORD dwFlags, HXI_CAPABILITIES_EX *pCapabilities) {
    if (dwReserved != 1 || dwUserIndex >= HXI_SLOTS || !pCapabilities || (dwFlags & ~HXI_FLAG_GAMEPAD))
        return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r;
    if (g_hidden) {
        r = ERROR_DEVICE_NOT_CONNECTED;
    } else if (g_mode == MODE_FORWARD) {
        if (g_fwd.caps_ex) {
            r = g_fwd.caps_ex(dwReserved, dwUserIndex, dwFlags, pCapabilities);
        } else { /* a Wine without #108: its capabilities plus the slot's identity */
            memset(pCapabilities, 0, sizeof *pCapabilities);
            r = g_fwd.caps(dwUserIndex, dwFlags, &pCapabilities->Capabilities);
            if (r == ERROR_SUCCESS)
                slot_identity(dwUserIndex, &pCapabilities->VendorId, &pCapabilities->ProductId, &pCapabilities->VersionNumber);
        }
    } else {
        r = direct_caps_ex(dwUserIndex, pCapabilities);
    }
    api_leave();
    return r;
}

DWORD WINAPI XInputGetBatteryInformation(DWORD dwUserIndex, BYTE devType, HXI_BATTERY_INFORMATION *pInfo) {
    if (dwUserIndex >= HXI_SLOTS || !pInfo) return ERROR_BAD_ARGUMENTS;
    if (devType != HXI_BATTERY_DEVTYPE_GAMEPAD && devType != HXI_BATTERY_DEVTYPE_HEADSET) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = !g_hidden && g_mode == MODE_FORWARD && g_fwd.battery ? g_fwd.battery(dwUserIndex, devType, pInfo)
            : battery_for(mode_connected(dwUserIndex), devType, pInfo);
    api_leave();
    return r;
}

/* Never forwarded (Wine's xinput1_4 has no #6, and its #10 is a stub that raises). */
DWORD WINAPI XInputGetDSoundAudioDeviceGuids(DWORD dwUserIndex, GUID *pRender, GUID *pCapture) {
    if (dwUserIndex >= HXI_SLOTS || !pRender || !pCapture) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = dsound_for(mode_connected(dwUserIndex), pRender, pCapture);
    api_leave();
    return r;
}

DWORD WINAPI XInputGetAudioDeviceIds(DWORD dwUserIndex, LPWSTR pRenderDeviceId, UINT *pRenderCount,
                                     LPWSTR pCaptureDeviceId, UINT *pCaptureCount) {
    if (dwUserIndex >= HXI_SLOTS) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = audio_ids_for(mode_connected(dwUserIndex), pRenderDeviceId, pRenderCount, pCaptureDeviceId, pCaptureCount);
    api_leave();
    return r;
}

DWORD WINAPI XInputGetKeystroke(DWORD dwUserIndex, DWORD dwReserved, HXI_KEYSTROKE *pKeystroke) {
    if (!pKeystroke) return ERROR_BAD_ARGUMENTS;
    if (dwUserIndex >= HXI_SLOTS && dwUserIndex != HXI_USER_ANY) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = g_hidden ? ERROR_DEVICE_NOT_CONNECTED
            : g_mode == MODE_FORWARD ? (g_fwd.keystroke ? g_fwd.keystroke(dwUserIndex, dwReserved, pKeystroke) : ERROR_EMPTY)
            : direct_keystroke(dwUserIndex, pKeystroke);
    api_leave();
    return r;
}

/* Ordinal 101. dwFlag 0 waits (polling every 8 ms, in whichever mode is current) until Guide is
 * pressed, the slot disconnects or XInputCancelGuideButtonWait is called; the asynchronous form is
 * not supported. The mode lock is never held across the sleep. */
DWORD WINAPI XInputWaitForGuideButton(DWORD dwUserIndex, DWORD dwFlag, LPVOID pVoid) {
    (void)pVoid;
    if (dwUserIndex >= HXI_SLOTS) return ERROR_BAD_ARGUMENTS;
    if (dwFlag) return ERROR_NOT_SUPPORTED;
    hxi_init();
    hxi_slot *s = &g_slots[dwUserIndex];
    LONG gen = __atomic_load_n(&s->guide_cancel, __ATOMIC_ACQUIRE);
    for (;;) {
        int pressed = 0;
        DWORD r = ERROR_SUCCESS;
        api_enter();
        if (g_hidden) {
            r = ERROR_DEVICE_NOT_CONNECTED;
        } else if (g_mode == MODE_FORWARD) {
            HXI_STATE st;
            r = fwd_state(dwUserIndex, &st, 1);
            pressed = r == ERROR_SUCCESS && (st.Gamepad.wButtons & HXI_XI_GUIDE);
        } else {
            uint32_t w[HXI_COPY_WORDS];
            AcquireSRWLockExclusive(&s->lock);
            int c = slot_read(s, w);
            ReleaseSRWLockExclusive(&s->lock);
            if (!c) r = ERROR_DEVICE_NOT_CONNECTED;
            pressed = c && (hxi_pressed(w) & (1u << HXI_B_MODE));
        }
        api_leave();
        if (r != ERROR_SUCCESS) return r;
        if (pressed) return ERROR_SUCCESS;
        if (__atomic_load_n(&s->guide_cancel, __ATOMIC_ACQUIRE) != gen) return ERROR_SUCCESS;
        Sleep(8);
    }
}

/* Ordinal 102. */
DWORD WINAPI XInputCancelGuideButtonWait(DWORD dwUserIndex) {
    if (dwUserIndex >= HXI_SLOTS) return ERROR_BAD_ARGUMENTS;
    hxi_init();
    __atomic_add_fetch(&g_slots[dwUserIndex].guide_cancel, 1, __ATOMIC_ACQ_REL);
    return ERROR_SUCCESS;
}

/* Ordinal 103. The pad is the device's own (or the app's); there is nothing to power off. */
DWORD WINAPI XInputPowerOffController(DWORD dwUserIndex) {
    if (dwUserIndex >= HXI_SLOTS) return ERROR_BAD_ARGUMENTS;
    api_enter();
    DWORD r = mode_connected(dwUserIndex) ? ERROR_SUCCESS : ERROR_DEVICE_NOT_CONNECTED;
    api_leave();
    return r;
}

BOOL WINAPI DllMain(HINSTANCE instance, DWORD reason, LPVOID reserved) {
    (void)reserved;
    if (reason == DLL_PROCESS_ATTACH) {
        g_module = instance;
        DisableThreadLibraryCalls(instance);
        log_attach(instance);
    }
    return TRUE;
}
