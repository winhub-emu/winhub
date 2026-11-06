/*
 * xinput_test.exe - end-to-end test of HERMES-XInput under Wine (CI, see run_wine.sh).
 *
 * The feeder (feeder/, the app's real writer engine from hermes-core) owns the slot files and
 * performs one scripted step each time this program writes a new step number into the control
 * file (argv[1], a DOS path). This program loads every shipped DLL from its own folder, checks
 * their export tables, and then checks through the DLLs what the feeder published: connection,
 * values and buttons, Guide only in GetStateEx, dwPacketNumber, how fast a publish is visible
 * (the read-only view must be a live MAP_SHARED mapping), missed taps, file retirement,
 * replacement and loss, unplug, rumble (the feeder checks the datagrams), capabilities,
 * battery, audio ids, keystrokes and the Guide-button wait. Exit code 0 = every check passed.
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

typedef struct { WORD b; BYTE lt, rt; SHORT lx, ly, rx, ry; } GP;
typedef struct { DWORD packet; GP g; } ST;
typedef struct { WORD l, r; } VIB;
typedef struct { BYTE type, subtype; WORD flags; GP g; VIB v; } CAPS;
typedef struct { CAPS c; WORD vid, pid, ver, unk1; DWORD unk2; } CAPSEX;
typedef struct { BYTE type, level; } BATT;
typedef struct { WORD vk; WCHAR uni; WORD flags; BYTE user, hid; } KS;

typedef DWORD(WINAPI *get_state_fn)(DWORD, ST *);
typedef DWORD(WINAPI *set_state_fn)(DWORD, VIB *);
typedef DWORD(WINAPI *get_caps_fn)(DWORD, DWORD, CAPS *);
typedef void(WINAPI *enable_fn)(BOOL);
typedef DWORD(WINAPI *battery_fn)(DWORD, BYTE, BATT *);
typedef DWORD(WINAPI *keystroke_fn)(DWORD, DWORD, KS *);
typedef DWORD(WINAPI *dsound_fn)(DWORD, GUID *, GUID *);
typedef DWORD(WINAPI *audio_ids_fn)(DWORD, LPWSTR, UINT *, LPWSTR, UINT *);
typedef DWORD(WINAPI *wait_guide_fn)(DWORD, DWORD, LPVOID);
typedef DWORD(WINAPI *idx_fn)(DWORD);
typedef DWORD(WINAPI *caps_ex_fn)(DWORD, DWORD, DWORD, CAPSEX *);

#define XI_DPAD_UP 0x0001
#define XI_DPAD_DOWN 0x0002
#define XI_DPAD_LEFT 0x0004
#define XI_DPAD_RIGHT 0x0008
#define XI_START 0x0010
#define XI_GUIDE 0x0400
#define XI_A 0x1000
#define XI_B 0x2000
#define XI_X 0x4000
#define XI_Y 0x8000

static int g_fail, g_checks;
#define CHECK(c, ...)                                                   \
    do {                                                                \
        g_checks++;                                                     \
        if (!(c)) {                                                     \
            g_fail++;                                                   \
            printf("FAIL %s:%d: ", __FILE__, __LINE__);                 \
            printf(__VA_ARGS__);                                        \
            printf("\n");                                               \
            fflush(stdout);                                             \
        }                                                               \
    } while (0)

static void (*fp(HMODULE h, const char *name))(void) {
    return (void (*)(void))GetProcAddress(h, name);
}
static void (*fpo(HMODULE h, unsigned ord))(void) {
    return (void (*)(void))GetProcAddress(h, (LPCSTR)(ULONG_PTR)ord);
}

static LARGE_INTEGER g_freq;
static double now_ms(void) {
    LARGE_INTEGER t;
    QueryPerformanceCounter(&t);
    return (double)t.QuadPart * 1000.0 / (double)g_freq.QuadPart;
}

/* ---- control file ---------------------------------------------------------------------------- */

static HANDLE g_ctl = INVALID_HANDLE_VALUE;

static void step(unsigned n) {
    char buf[32];
    int len = snprintf(buf, sizeof buf, "%08u %08u\n", n, 99999999u - n);
    OVERLAPPED ov;
    memset(&ov, 0, sizeof ov);
    DWORD w = 0;
    if (!WriteFile(g_ctl, buf, (DWORD)len, &w, &ov) || w != (DWORD)len) {
        printf("FATAL: cannot write the control file (%lu)\n", GetLastError());
        exit(3);
    }
}

/* ---- DLLs ------------------------------------------------------------------------------------ */

struct spec {
    const char *name;
    const char *named[16];
    unsigned ords[8];
    const char *absent[6];
};

/* Every DLL also exports the extended functions by name (Wine's own programs, e.g. joy.cpl, import
 * XInputGetStateEx by name) at their Windows ordinals 100-103 (and 108 in xinput1_4). */
#define HXI_EXT "XInputGetStateEx", "XInputWaitForGuideButton", "XInputCancelGuideButtonWait", "XInputPowerOffController"
static const struct spec SPECS[] = {
    {"xinput1_1", {"XInputGetState", "XInputSetState", "XInputGetCapabilities", "XInputEnable", "XInputGetDSoundAudioDeviceGuids", HXI_EXT},
     {100, 101, 102, 103}, {"XInputGetBatteryInformation", "XInputGetKeystroke", "XInputGetAudioDeviceIds"}},
    {"xinput1_2", {"XInputGetState", "XInputSetState", "XInputGetCapabilities", "XInputEnable", "XInputGetDSoundAudioDeviceGuids", HXI_EXT},
     {100, 101, 102, 103}, {"XInputGetBatteryInformation", "XInputGetKeystroke", "XInputGetAudioDeviceIds"}},
    {"xinput1_3", {"XInputGetState", "XInputSetState", "XInputGetCapabilities", "XInputEnable", "XInputGetDSoundAudioDeviceGuids",
                   "XInputGetBatteryInformation", "XInputGetKeystroke", HXI_EXT},
     {100, 101, 102, 103}, {"XInputGetAudioDeviceIds"}},
    {"xinput1_4", {"XInputGetState", "XInputSetState", "XInputGetCapabilities", "XInputEnable", "XInputGetBatteryInformation",
                   "XInputGetKeystroke", "XInputGetAudioDeviceIds", HXI_EXT, "XInputGetCapabilitiesEx"},
     {100, 101, 102, 103, 108}, {"XInputGetDSoundAudioDeviceGuids"}},
    {"xinput9_1_0", {"XInputGetCapabilities", "XInputGetDSoundAudioDeviceGuids", "XInputGetState", "XInputSetState", HXI_EXT},
     {100, 101, 102, 103}, {"XInputEnable", "XInputGetKeystroke", "XInputGetBatteryInformation"}},
    {"xinputuap", {"XInputEnable", "XInputGetAudioDeviceIds", "XInputGetBatteryInformation", "XInputGetCapabilities",
                   "XInputGetKeystroke", "XInputGetState", "XInputSetState", HXI_EXT},
     {100, 101, 102, 103}, {"XInputGetDSoundAudioDeviceGuids"}},
};
#define NSPECS (sizeof SPECS / sizeof SPECS[0])
static HMODULE g_dll[NSPECS];

static void load_dlls(void) {
    WCHAR dir[MAX_PATH];
    DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
    while (n > 0 && dir[n - 1] != '\\') n--;
    dir[n] = 0;
    for (size_t i = 0; i < NSPECS; i++) {
        const struct spec *s = &SPECS[i];
        WCHAR path[MAX_PATH];
        _snwprintf(path, MAX_PATH, L"%ls%hs.dll", dir, s->name);
        path[MAX_PATH - 1] = 0;
        HMODULE h = LoadLibraryW(path);
        CHECK(h != NULL, "%s.dll does not load (%lu)", s->name, GetLastError());
        if (!h) continue;
        WCHAR got[MAX_PATH];
        GetModuleFileNameW(h, got, MAX_PATH);
        CHECK(_wcsicmp(got, path) == 0, "%s.dll loaded from %ls, not from the test folder", s->name, got);
        for (int k = 0; k < 16 && s->named[k]; k++)
            CHECK(fp(h, s->named[k]) != NULL, "%s.dll lacks %s", s->name, s->named[k]);
        for (int k = 0; k < 8 && s->ords[k]; k++)
            CHECK(fpo(h, s->ords[k]) != NULL, "%s.dll lacks ordinal %u", s->name, s->ords[k]);
        /* the named extended exports are the same functions as their ordinals */
        CHECK(fp(h, "XInputGetStateEx") == fpo(h, 100), "%s.dll: XInputGetStateEx is not ordinal 100", s->name);
        CHECK(fp(h, "XInputPowerOffController") == fpo(h, 103), "%s.dll: XInputPowerOffController is not ordinal 103", s->name);
        if (!strcmp(s->name, "xinput1_4"))
            CHECK(fp(h, "XInputGetCapabilitiesEx") == fpo(h, 108), "xinput1_4.dll: XInputGetCapabilitiesEx is not ordinal 108");
        for (int k = 0; k < 6 && s->absent[k]; k++)
            CHECK(fp(h, s->absent[k]) == NULL, "%s.dll exports %s by name (it must not)", s->name, s->absent[k]);
        if (!strcmp(s->name, "xinput1_3") || !strcmp(s->name, "xinput1_4"))
            CHECK(fpo(h, 2) == fp(h, "XInputGetState"), "%s.dll: ordinal 2 is not XInputGetState", s->name);
        g_dll[i] = h;
    }
}

static HMODULE dll(const char *name) {
    for (size_t i = 0; i < NSPECS; i++)
        if (!strcmp(SPECS[i].name, name)) return g_dll[i];
    return NULL;
}

/* ---- state waits ----------------------------------------------------------------------------- */

static get_state_fn GS, GSX;

static int gp_eq(const GP *a, const GP *b) {
    return a->b == b->b && a->lt == b->lt && a->rt == b->rt && a->lx == b->lx && a->ly == b->ly && a->rx == b->rx
        && a->ry == b->ry;
}

static void gp_print(const char *what, DWORD r, const GP *g) {
    printf("    %s: ret %lu buttons %04x lt %u rt %u lx %d ly %d rx %d ry %d\n", what, r, g->b, g->lt, g->rt, g->lx, g->ly,
           g->rx, g->ry);
}

/* Poll until the slot returns `want_ret` (and, for success, exactly `want`). Returns the elapsed
 * time in ms, or -1 on timeout. Spins (the latency measurement needs it), yielding after 20 ms. */
static double wait_for(get_state_fn f, DWORD idx, DWORD want_ret, const GP *want, double timeout_ms, ST *last) {
    double t0 = now_ms();
    ST st;
    DWORD r = 0;
    for (;;) {
        memset(&st, 0, sizeof st);
        r = f(idx, &st);
        if (r == want_ret && (want_ret != ERROR_SUCCESS || gp_eq(&st.g, want))) break;
        double el = now_ms() - t0;
        if (el > timeout_ms) {
            if (last) *last = st;
            gp_print("timed out with", r, &st.g);
            return -1;
        }
        if (el > 20) Sleep(1);
    }
    if (last) *last = st;
    return now_ms() - t0;
}

static GP gp(WORD b, BYTE lt, BYTE rt, SHORT lx, SHORT ly, SHORT rx, SHORT ry) {
    GP g = {b, lt, rt, lx, ly, rx, ry};
    return g;
}

static int cmp_double(const void *a, const void *b) {
    double x = *(const double *)a, y = *(const double *)b;
    return x < y ? -1 : x > y;
}

static wait_guide_fn g_wait_guide;
static DWORD WINAPI guide_thread(LPVOID p) {
    (void)p;
    return g_wait_guide(0, 0, NULL);
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    QueryPerformanceFrequency(&g_freq);
    if (argc < 2) {
        printf("usage: xinput_test.exe <control file>\n");
        return 2;
    }
    g_ctl = CreateFileA(argv[1], GENERIC_WRITE, FILE_SHARE_READ | FILE_SHARE_WRITE | FILE_SHARE_DELETE, NULL, OPEN_ALWAYS,
                        FILE_ATTRIBUTE_NORMAL, NULL);
    if (g_ctl == INVALID_HANDLE_VALUE) {
        printf("FATAL: cannot open %s (%lu)\n", argv[1], GetLastError());
        return 3;
    }
    printf("== exports\n");
    load_dlls();
    HMODULE x4 = dll("xinput1_4"), x3 = dll("xinput1_3");
    if (!x4 || !x3) {
        printf("FATAL: xinput1_4 / xinput1_3 missing\n");
        return 1;
    }
    GS = (get_state_fn)fp(x4, "XInputGetState");
    GSX = (get_state_fn)fpo(x4, 100);
    get_state_fn gsx3 = (get_state_fn)fpo(x3, 100);
    set_state_fn SS = (set_state_fn)fp(x4, "XInputSetState");
    enable_fn EN = (enable_fn)fp(x4, "XInputEnable");
    get_caps_fn CAP = (get_caps_fn)fp(x4, "XInputGetCapabilities");
    caps_ex_fn CAPX = (caps_ex_fn)fpo(x4, 108);
    battery_fn BAT = (battery_fn)fp(x4, "XInputGetBatteryInformation");
    keystroke_fn KEY = (keystroke_fn)fp(x4, "XInputGetKeystroke");
    audio_ids_fn AID = (audio_ids_fn)fp(x4, "XInputGetAudioDeviceIds");
    dsound_fn DSG = (dsound_fn)fp(x3, "XInputGetDSoundAudioDeviceGuids");
    g_wait_guide = (wait_guide_fn)fpo(x4, 101);
    idx_fn CANCEL = (idx_fn)fpo(x4, 102), POWEROFF = (idx_fn)fpo(x4, 103);
    if (!GS || !GSX || !gsx3 || !SS || !EN || !CAP || !CAPX || !BAT || !KEY || !AID || !DSG || !g_wait_guide || !CANCEL
        || !POWEROFF) {
        printf("FATAL: entry points missing\n");
        return 1;
    }

    ST st;
    GP neutral = gp(0, 0, 0, 0, 0, 0, 0);

    printf("== connection\n");
    double t = wait_for(GS, 0, ERROR_SUCCESS, &neutral, 3000, &st);
    CHECK(t >= 0, "slot 0 never connected with a neutral pad");
    DWORD r = GS(1, &st);
    CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "slot 1 (file, no controller) returned %lu", r);
    r = GS(2, &st);
    CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "slot 2 (configured, no file) returned %lu", r);
    r = GS(3, &st);
    CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "slot 3 (not configured) returned %lu", r);
    r = GS(4, &st);
    CHECK(r == ERROR_BAD_ARGUMENTS, "user index 4 returned %lu", r);
    r = GS(0, NULL);
    CHECK(r == ERROR_BAD_ARGUMENTS, "NULL state returned %lu", r);

    printf("== values\n");
    step(1);
    GP want = gp(XI_A | XI_DPAD_LEFT | XI_DPAD_UP, 255, 10, 1000, 2000, 32767, 32767);
    t = wait_for(GS, 0, ERROR_SUCCESS, &want, 1000, &st);
    CHECK(t >= 0, "phase 1 state not seen (A, sticks, triggers, dpad up-left)");
    step(2);
    want = gp(XI_B | XI_START | XI_DPAD_RIGHT | XI_DPAD_DOWN, 0, 0, -32768, -32767, 0, -1);
    t = wait_for(GS, 0, ERROR_SUCCESS, &want, 1000, &st);
    CHECK(t >= 0, "phase 2 state not seen through XInputGetState (Guide must be hidden)");
    GP wantx = want;
    wantx.b |= XI_GUIDE;
    memset(&st, 0, sizeof st);
    r = GSX(0, &st);
    CHECK(r == ERROR_SUCCESS && gp_eq(&st.g, &wantx), "XInputGetStateEx (1_4 #100) does not report Guide");
    gp_print("GetStateEx", r, &st.g);
    memset(&st, 0, sizeof st);
    r = gsx3(0, &st);
    CHECK(r == ERROR_SUCCESS && gp_eq(&st.g, &wantx), "XInputGetStateEx (1_3 #100) does not report Guide");

    printf("== live updates (200 publishes)\n");
    double lat[200];
    DWORD prev_packet = st.packet;
    int packet_ok = 1;
    for (unsigned k = 0; k < 200; k++) {
        double t0 = now_ms();
        step(1000 + k);
        want = gp(0, 0, 0, (SHORT)(k * 100 + 1), 0, 0, 0);
        double w = wait_for(GS, 0, ERROR_SUCCESS, &want, 1000, &st);
        lat[k] = w < 0 ? 1e9 : now_ms() - t0;
        if (w < 0) CHECK(0, "publish %u not seen within 1 s", k);
        if (st.packet == prev_packet) packet_ok = 0;
        prev_packet = st.packet;
    }
    CHECK(packet_ok, "dwPacketNumber did not change with every publish");
    qsort(lat, 200, sizeof lat[0], cmp_double);
    printf("    step -> visible: median %.3f ms, p90 %.3f ms, p99 %.3f ms, max %.3f ms\n", lat[100], lat[180], lat[198],
           lat[199]);
    CHECK(lat[100] < 2.0, "median latency %.3f ms (want < 2 ms: the view must be live)", lat[100]);
    if (lat[198] > 10.0) printf("    note: p99 above 10 ms (runner scheduling)\n");
    memset(&st, 0, sizeof st);
    GS(0, &st);
    DWORD p1 = st.packet;
    GS(0, &st);
    DWORD p2 = st.packet;
    CHECK(p1 == p2, "dwPacketNumber changed without a publish (%lu -> %lu)", p1, p2);

    printf("== a tap between two polls\n");
    step(300);
    {
        double t0 = now_ms();
        int saw_x = 0, done = 0;
        while (now_ms() - t0 < 1000) {
            memset(&st, 0, sizeof st);
            if (GS(0, &st) != ERROR_SUCCESS) continue;
            if (st.g.b & XI_X) saw_x = 1;
            if (st.g.lx == 777) {
                done = 1;
                break;
            }
        }
        CHECK(done, "phase 300 end state not seen");
        CHECK(saw_x, "the tap of X (pressed and released between two polls) was never reported");
        memset(&st, 0, sizeof st);
        GS(0, &st);
        CHECK(!(st.g.b & XI_X), "X still reported after the tap was surfaced");
    }

    printf("== file replaced by a new writer\n");
    step(400);
    want = gp(XI_Y, 0, 0, 0, 0, 0, 0);
    t = wait_for(GS, 0, ERROR_SUCCESS, &want, 2500, &st);
    CHECK(t >= 0, "the replacement file was not picked up within 2.5 s");
    printf("    re-attached after %.1f ms\n", t);

    printf("== file released (session end), then created again\n");
    step(500);
    t = wait_for(GS, 0, ERROR_DEVICE_NOT_CONNECTED, NULL, 200, &st);
    CHECK(t >= 0, "a released (retired, unlinked) file still reads as connected");
    step(501);
    t = wait_for(GS, 0, ERROR_SUCCESS, &want, 2500, &st);
    CHECK(t >= 0, "the recreated file was not picked up within 2.5 s");
    printf("    re-attached after %.1f ms\n", t);

    printf("== unplug, then a new controller\n");
    step(600);
    t = wait_for(GS, 0, ERROR_DEVICE_NOT_CONNECTED, NULL, 200, &st);
    CHECK(t >= 0, "an unplugged slot still reads as connected");
    step(601);
    want = gp(XI_A, 0, 0, 0, 0, 0, 0);
    t = wait_for(GS, 0, ERROR_SUCCESS, &want, 2500, &st);
    CHECK(t >= 0, "the new controller was not picked up within 2.5 s");

    printf("== rumble (the feeder checks the datagrams)\n");
    VIB v = {40000, 20000};
    r = SS(0, &v);
    CHECK(r == ERROR_SUCCESS, "XInputSetState returned %lu", r);
    for (int i = 0; i < 100; i++) SS(0, &v);
    Sleep(20);
    v.l = v.r = 0;
    SS(0, &v);
    Sleep(20);
    EN(FALSE);
    memset(&st, 0xcc, sizeof st);
    r = GS(0, &st);
    CHECK(r == ERROR_SUCCESS && gp_eq(&st.g, &neutral), "XInputEnable(FALSE) does not neutralise the state");
    v.l = 1000;
    v.r = 2000;
    SS(0, &v);
    Sleep(20);
    EN(TRUE);
    r = GS(0, &st);
    want = gp(XI_A, 0, 0, 0, 0, 0, 0);
    CHECK(r == ERROR_SUCCESS && gp_eq(&st.g, &want), "XInputEnable(TRUE) does not bring the state back");
    Sleep(20);
    v.l = v.r = 0;
    SS(0, &v);
    v.l = 5;
    r = SS(1, &v);
    CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "XInputSetState on a slot without a controller returned %lu", r);
    r = SS(4, &v);
    CHECK(r == ERROR_BAD_ARGUMENTS, "XInputSetState on user 4 returned %lu", r);

    printf("== capabilities, battery, audio\n");
    CAPS caps;
    memset(&caps, 0, sizeof caps);
    r = CAP(0, 1, &caps);
    CHECK(r == ERROR_SUCCESS && caps.type == 1 && caps.subtype == 1 && caps.g.b == 0xf3ff,
          "XInputGetCapabilities: %lu type %u subtype %u buttons %04x", r, caps.type, caps.subtype, caps.g.b);
    r = CAP(1, 0, &caps);
    CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "XInputGetCapabilities slot 1 returned %lu", r);
    CAPSEX cx;
    memset(&cx, 0, sizeof cx);
    r = CAPX(1, 0, 0, &cx);
    CHECK(r == ERROR_SUCCESS && cx.vid == 0x045e && cx.pid == 0x028e && cx.c.type == 1,
          "XInputGetCapabilitiesEx: %lu vid %04x pid %04x", r, cx.vid, cx.pid);
    r = CAPX(0, 0, 0, &cx);
    CHECK(r == ERROR_BAD_ARGUMENTS, "XInputGetCapabilitiesEx with reserved 0 returned %lu", r);
    BATT bt = {9, 9};
    r = BAT(0, 0, &bt);
    CHECK(r == ERROR_SUCCESS && bt.type == 1 && bt.level == 3, "battery (pad): %lu %u %u", r, bt.type, bt.level);
    r = BAT(0, 1, &bt);
    CHECK(r == ERROR_SUCCESS && bt.type == 0, "battery (headset): %lu %u", r, bt.type);
    WCHAR rid[64] = L"x", cid[64] = L"x";
    UINT rc = 64, cc = 64;
    r = AID(0, rid, &rc, cid, &cc);
    CHECK(r == ERROR_SUCCESS && rc == 0 && cc == 0 && rid[0] == 0, "XInputGetAudioDeviceIds: %lu %u %u", r, rc, cc);
    GUID g1, g2;
    memset(&g1, 0xab, sizeof g1);
    memset(&g2, 0xab, sizeof g2);
    r = DSG(0, &g1, &g2);
    GUID z;
    memset(&z, 0, sizeof z);
    CHECK(r == ERROR_SUCCESS && !memcmp(&g1, &z, sizeof z) && !memcmp(&g2, &z, sizeof z),
          "XInputGetDSoundAudioDeviceGuids: %lu", r);
    r = POWEROFF(0);
    CHECK(r == ERROR_SUCCESS, "XInputPowerOffController returned %lu", r);

    printf("== keystrokes\n");
    KS ks;
    for (int i = 0; i < 64 && KEY(0, 0, &ks) == ERROR_SUCCESS; i++) {}
    r = KEY(0, 0, &ks);
    CHECK(r == ERROR_EMPTY, "keystroke queue not empty after draining: %lu", r);
    step(801); /* A up, B down */
    {
        KS ev[4];
        int n = 0;
        double t0 = now_ms();
        while (n < 2 && now_ms() - t0 < 1000)
            if (KEY(0, 0, &ev[n]) == ERROR_SUCCESS) n++;
        CHECK(n == 2 && ev[0].vk == 0x5800 && ev[0].flags == 2 && ev[1].vk == 0x5801 && ev[1].flags == 1,
              "A up then B down expected, got %d events (%04x/%u %04x/%u)", n, n > 0 ? ev[0].vk : 0, n > 0 ? ev[0].flags : 0,
              n > 1 ? ev[1].vk : 0, n > 1 ? ev[1].flags : 0);
        step(802); /* B up, through XUSER_INDEX_ANY */
        n = 0;
        t0 = now_ms();
        while (n < 1 && now_ms() - t0 < 1000)
            if (KEY(0xff, 0, &ev[0]) == ERROR_SUCCESS) n++;
        CHECK(n == 1 && ev[0].vk == 0x5801 && ev[0].flags == 2 && ev[0].user == 0, "B up (any user) expected");
        step(803); /* left stick up-right */
        n = 0;
        t0 = now_ms();
        while (n < 1 && now_ms() - t0 < 1000)
            if (KEY(0, 0, &ev[0]) == ERROR_SUCCESS) n++;
        CHECK(n == 1 && ev[0].vk == 0x5825 && ev[0].flags == 1, "VK_PAD_LTHUMB_UPRIGHT down expected, got %04x", ev[0].vk);
        step(804);
        n = 0;
        t0 = now_ms();
        while (n < 1 && now_ms() - t0 < 1000)
            if (KEY(0, 0, &ev[0]) == ERROR_SUCCESS) n++;
        CHECK(n == 1 && ev[0].vk == 0x5825 && ev[0].flags == 2, "VK_PAD_LTHUMB_UPRIGHT up expected");
    }

    printf("== Guide button wait\n");
    step(900);
    t = wait_for(GS, 0, ERROR_SUCCESS, &neutral, 1000, &st);
    CHECK(t >= 0, "phase 900 neutral state not seen");
    {
        HANDLE th = CreateThread(NULL, 0, guide_thread, NULL, 0, NULL);
        Sleep(50);
        CHECK(WaitForSingleObject(th, 0) == WAIT_TIMEOUT, "XInputWaitForGuideButton returned before Guide was pressed");
        step(901);
        CHECK(WaitForSingleObject(th, 2000) == WAIT_OBJECT_0, "XInputWaitForGuideButton did not return on Guide");
        DWORD code = 99;
        GetExitCodeThread(th, &code);
        CHECK(code == ERROR_SUCCESS, "XInputWaitForGuideButton returned %lu", code);
        CloseHandle(th);
        step(902);
        t = wait_for(GS, 0, ERROR_SUCCESS, &neutral, 1000, &st);
        th = CreateThread(NULL, 0, guide_thread, NULL, 0, NULL);
        Sleep(50);
        CANCEL(0);
        CHECK(WaitForSingleObject(th, 2000) == WAIT_OBJECT_0, "XInputCancelGuideButtonWait did not end the wait");
        CloseHandle(th);
    }

    printf("== live mode: control block (identity, direct -> forward -> direct, hidden)\n");
    {
        CAPSEX lx;
        ST ls;
        VIB lv;
        step(1300); /* block appears: direct, forward available, identity 054c:09cc */
        double t0 = now_ms();
        int vid_ok = 0;
        while (!vid_ok && now_ms() - t0 < 3000) {
            memset(&lx, 0, sizeof lx);
            vid_ok = CAPX(1, 0, 0, &lx) == ERROR_SUCCESS && lx.vid == 0x054c;
            if (!vid_ok) Sleep(5);
        }
        CHECK(vid_ok && lx.pid == 0x09cc, "GetCapabilitiesEx does not follow the control block identity (%04x:%04x)", lx.vid, lx.pid);
        memset(&ls, 0, sizeof ls);
        r = GS(0, &ls);
        CHECK(r == ERROR_SUCCESS, "direct mode with a control block: slot 0 returned %lu", r);
        DWORD before = ls.packet;
        lv.l = lv.r = 30000;
        r = SS(0, &lv);
        CHECK(r == ERROR_SUCCESS, "direct SetState returned %lu", r);
        Sleep(30);

        step(1301); /* forward: Wine's xinput, which sees no HID pad on this runner */
        t = wait_for(GS, 0, ERROR_DEVICE_NOT_CONNECTED, NULL, 4000, &ls);
        CHECK(t >= 0, "forward mode never took over (Wine's xinput would report slot 0 not connected)");
        printf("    forward after %.1f ms\n", t);
        r = GSX(0, &ls);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "forward GetStateEx returned %lu", r);
        r = CAPX(1, 0, 0, &lx);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "forward GetCapabilitiesEx returned %lu", r);
        lv.l = lv.r = 1234;
        r = SS(0, &lv);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "forward SetState returned %lu (Wine's xinput has no pad)", r);
        Sleep(30);

        step(1302); /* direct again */
        t = wait_for(GS, 0, ERROR_SUCCESS, &neutral, 4000, &ls);
        CHECK(t >= 0, "back in direct mode, slot 0 is not connected again");
        CHECK((LONG)(ls.packet - before) > 0, "dwPacketNumber went backwards across modes (%lu -> %lu)", before, ls.packet);
        lv.l = 20000;
        lv.r = 10000;
        r = SS(0, &lv);
        CHECK(r == ERROR_SUCCESS, "direct SetState after forward returned %lu", r);
        Sleep(30);

        step(1303); /* XInput hidden */
        t = wait_for(GS, 0, ERROR_DEVICE_NOT_CONNECTED, NULL, 4000, &ls);
        CHECK(t >= 0, "XI_HIDDEN does not hide slot 0");
        lv.l = lv.r = 5000;
        r = SS(0, &lv);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "hidden SetState returned %lu", r);
        r = CAPX(1, 0, 0, &lx);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "hidden GetCapabilitiesEx returned %lu", r);
        KS lk;
        r = KEY(0xff, 0, &lk);
        CHECK(r == ERROR_DEVICE_NOT_CONNECTED, "hidden GetKeystroke(any) returned %lu", r);
        Sleep(30);

        step(1304); /* visible again, identity no longer valid */
        t = wait_for(GS, 0, ERROR_SUCCESS, &neutral, 4000, &ls);
        CHECK(t >= 0, "slot 0 does not come back after XI_HIDDEN is cleared");
        memset(&lx, 0, sizeof lx);
        r = CAPX(1, 0, 0, &lx);
        CHECK(r == ERROR_SUCCESS && lx.vid == 0x045e && lx.pid == 0x028e, "identity not back to 045e:028e (%lu %04x:%04x)", r, lx.vid, lx.pid);
    }

    printf("== every DLL reads the slot\n");
    for (size_t i = 0; i < NSPECS; i++) {
        if (!g_dll[i]) continue;
        get_state_fn f = (get_state_fn)fp(g_dll[i], "XInputGetState");
        t = wait_for(f, 0, ERROR_SUCCESS, &neutral, 1500, &st);
        CHECK(t >= 0, "%s.dll does not read slot 0", SPECS[i].name);
    }

    printf("== GUI child processes (detached, no std handles), one per log channel\n");
    {
        WCHAR dir[MAX_PATH], gui[MAX_PATH], dllp[MAX_PATH];
        DWORD n = GetModuleFileNameW(NULL, dir, MAX_PATH);
        while (n > 0 && dir[n - 1] != '\\') n--;
        dir[n] = 0;
        _snwprintf(gui, MAX_PATH, L"%lsxinput_gui.exe", dir);
        gui[MAX_PATH - 1] = 0;
        _snwprintf(dllp, MAX_PATH, L"%lsxinput1_4.dll", dir);
        dllp[MAX_PATH - 1] = 0;
        SetEnvironmentVariableW(L"HXI_TEST_DLL", dllp);
        static const WCHAR *const vias[] = {L"auto", L"stderr", L"ods"};
        for (int i = 0; i < 3; i++) {
            SetEnvironmentVariableW(L"HERMES_XINPUT_LOGVIA", vias[i]);
            STARTUPINFOW si;
            PROCESS_INFORMATION pi;
            memset(&si, 0, sizeof si);
            si.cb = sizeof si;
            BOOL ok = CreateProcessW(gui, NULL, NULL, NULL, FALSE, DETACHED_PROCESS, NULL, NULL, &si, &pi);
            CHECK(ok, "cannot start xinput_gui.exe (%lu)", GetLastError());
            if (!ok) continue;
            DWORD w = WaitForSingleObject(pi.hProcess, 15000), code = 99;
            GetExitCodeProcess(pi.hProcess, &code);
            CHECK(w == WAIT_OBJECT_0 && code == 0, "xinput_gui.exe (log via %ls): wait %lu, exit %lu", vias[i], w, code);
            printf("    xinput_gui.exe, log via %ls: exit %lu (0 = it read slot 0)\n", vias[i], code);
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
        }
        SetEnvironmentVariableW(L"HERMES_XINPUT_LOGVIA", NULL);

        /* xinput_byname.exe imports XInputGetStateEx & co BY NAME (as Wine's joy.cpl does) */
        WCHAR byname[MAX_PATH];
        _snwprintf(byname, MAX_PATH, L"%lsxinput_byname.exe", dir);
        byname[MAX_PATH - 1] = 0;
        STARTUPINFOW si;
        PROCESS_INFORMATION pi;
        memset(&si, 0, sizeof si);
        si.cb = sizeof si;
        BOOL ok = CreateProcessW(byname, NULL, NULL, NULL, FALSE, DETACHED_PROCESS, NULL, NULL, &si, &pi);
        CHECK(ok, "cannot start xinput_byname.exe (%lu)", GetLastError());
        if (ok) {
            DWORD w = WaitForSingleObject(pi.hProcess, 15000), code = 99;
            GetExitCodeProcess(pi.hProcess, &code);
            CHECK(w == WAIT_OBJECT_0 && code == 0, "xinput_byname.exe: wait %lu, exit %lu (10+ = a by-name import failed)", w, code);
            printf("    xinput_byname.exe (imports XInputGetStateEx, ..., XInputGetCapabilitiesEx by name): exit %lu\n", code);
            CloseHandle(pi.hThread);
            CloseHandle(pi.hProcess);
        }
    }

    step(999);
    printf("== %d checks, %d failed\n", g_checks, g_fail);
    return g_fail ? 1 : 0;
}
