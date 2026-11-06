/*
 * xinput_byname.exe - imports the extended XInput functions BY NAME, through import libraries made
 * from our .def files: XInputGetStateEx, XInputCancelGuideButtonWait and XInputPowerOffController
 * from xinput1_3.dll (as Wine's joy.cpl does), XInputGetCapabilitiesEx from xinput1_4.dll. If a DLL
 * exported one of them only by ordinal, Wine would bind the import to a stub that raises
 * "unimplemented function" when called, and this program would die instead of exiting 0.
 * xinput_test.exe starts it while the feeder keeps slot 0 connected (built for x86_64, i386 and
 * aarch64: in an arm64ec Wine the x86_64 build uses the ARM64X image's EC view, aarch64 its native one).
 */
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

typedef struct { WORD b; BYTE lt, rt; SHORT lx, ly, rx, ry; } GP;
typedef struct { DWORD packet; GP g; } ST;
typedef struct { BYTE type, subtype; WORD flags; GP g; WORD l, r; } CAPS;
typedef struct { CAPS c; WORD vid, pid, ver, unk1; DWORD unk2; } CAPSEX;

__declspec(dllimport) DWORD WINAPI XInputGetStateEx(DWORD, ST *);
__declspec(dllimport) DWORD WINAPI XInputCancelGuideButtonWait(DWORD);
__declspec(dllimport) DWORD WINAPI XInputPowerOffController(DWORD);
__declspec(dllimport) DWORD WINAPI XInputGetCapabilitiesEx(DWORD, DWORD, DWORD, CAPSEX *);

int main(void) {
    ST st;
    DWORD r = ERROR_DEVICE_NOT_CONNECTED;
    for (int i = 0; i < 300 && r != ERROR_SUCCESS; i++) {
        r = XInputGetStateEx(0, &st);
        if (r != ERROR_SUCCESS) Sleep(10);
    }
    if (r != ERROR_SUCCESS) return 10;
    if (XInputPowerOffController(0) != ERROR_SUCCESS) return 11;
    if (XInputCancelGuideButtonWait(0) != ERROR_SUCCESS) return 12;
    CAPSEX cx;
    if (XInputGetCapabilitiesEx(1, 0, 0, &cx) != ERROR_SUCCESS || cx.vid != 0x045e) return 13;
    return 0;
}
