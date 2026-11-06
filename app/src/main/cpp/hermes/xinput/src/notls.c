/*
 * Linked into both views of the ARM64X image only (build.sh link_arm64x), ahead of the mingw CRT.
 *
 * The CRT's DLL start-up (dllcrt2.o) references __dyn_tls_init_callback, which pulls tlssup.o and
 * with it _tls_used, the TLS directory. An ARM64X image has one TLS directory for both views (the
 * linker takes the native view's and emits no ARM64X relocation for it), so its callbacks are the
 * native ARM64 ones. In an x64 process (the EC view) Wine treats non-EC code as x64 and the emulator
 * runs those ARM64 bytes as x86 on every thread start: a stray write to the image header (base+0x42)
 * that the loader catches, but a game's own first-chance exception handler sees first (Cyberpunk
 * 2077's crash reporter exits on it).
 *
 * Defining the symbol here keeps tlssup.o out, so the image has no TLS directory. The CRT checks the
 * pointer for NULL before calling it; this DLL has no thread-local variables.
 */
#include <windows.h>

const PIMAGE_TLS_CALLBACK __dyn_tls_init_callback = NULL;
