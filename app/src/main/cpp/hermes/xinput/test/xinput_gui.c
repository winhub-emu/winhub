/*
 * xinput_gui.exe - a GUI-subsystem child for the HERMES-XInput Wine test (see xinput_test.c).
 *
 * xinput_test.exe starts it detached and without inheritable handles, the way a launcher or explorer
 * starts a game: no console, no Windows std handles. It loads the DLL named by HXI_TEST_DLL, reads
 * slot 0 through it (retrying for up to 2 s) and exits with XInputGetState's result, so the test
 * can check that this kind of process loads the DLL, reads the pad, and that the DLL's diagnostics
 * of it (the DllMain line) still reach Wine's stderr (run_wine.sh greps for them).
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

typedef struct { WORD b; BYTE lt, rt; SHORT lx, ly, rx, ry; } GP;
typedef struct { DWORD packet; GP g; } ST;
typedef DWORD(WINAPI *get_state_fn)(DWORD, ST *);

int WINAPI WinMain(HINSTANCE instance, HINSTANCE prev, LPSTR cmdline, int show) {
    (void)instance; (void)prev; (void)cmdline; (void)show;
    WCHAR path[MAX_PATH];
    DWORD n = GetEnvironmentVariableW(L"HXI_TEST_DLL", path, MAX_PATH);
    if (!n || n >= MAX_PATH) return 200;
    HMODULE h = LoadLibraryW(path);
    if (!h) return 201;
    get_state_fn gs = (get_state_fn)(void (*)(void))GetProcAddress(h, "XInputGetState");
    if (!gs) return 202;
    ST st;
    DWORD r = ERROR_DEVICE_NOT_CONNECTED;
    for (int i = 0; i < 200 && r != ERROR_SUCCESS; i++) {
        r = gs(0, &st);
        if (r != ERROR_SUCCESS) Sleep(10);
    }
    return (int)r;
}
