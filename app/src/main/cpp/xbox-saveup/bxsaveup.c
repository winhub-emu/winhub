/*
 * bxsaveup.exe: uploads an Xbox game's pending container saves after the game has exited.
 *
 * Xodus Nexus' xgameruntime uploads each XGameSave container in the background as the game writes it; a game that
 * saves on close and exits at once (BALL x PIT) leaves that last save marked "dirty" in its index. Opening a save
 * provider runs the runtime's start-up sync, which uploads every dirty container of that SCID. This program does
 * exactly that, as the game: it starts the runtime with the game's MicrosoftGame.config, adds the default user
 * silently, opens and closes a provider per SCID, and shuts the runtime down.
 *
 * Bannerlator starts it through winhandler.exe on the shutdown screen (store/xbox/XboxSaveSync), in the game's
 * Wine session, so it has the session's Xodus bridge environment. Input: C:\windows\temp\bxsaveup.txt, the config
 * path (Windows path) on the first line and one SCID per following line. Output: C:\windows\temp\bxsaveup.log.
 *
 * The runtime is reached through its exports (InitializeApiImplEx2, QueryApiImpl, UninitializeApiImpl) and the
 * IXUserImpl / IXGameSaveImpl vtables of Xodus Nexus' dlls/xgameruntime (xuser.idl, xgamesave.idl).
 */
#include <windows.h>
#include <stdarg.h>
#include <stdio.h>
#include <string.h>

typedef struct XAsyncBlock { void *queue; void *context; void *callback; void *internal[4]; } XAsyncBlock;
struct initialize_options { UINT32 unk; BOOL isInline; const char *gameConfig; };

typedef HRESULT (WINAPI *InitializeApiImplEx2_t)( ULONG, ULONG, char, const struct initialize_options * );
typedef HRESULT (WINAPI *QueryApiImpl_t)( const GUID *, const GUID *, void ** );
typedef HRESULT (WINAPI *UninitializeApiImpl_t)( void );

/* coclass and interface share the GUID in xuser.idl / xgamesave.idl */
static const GUID XUSER = { 0x01acd177, 0x91f9, 0x4763, { 0xa3, 0x8e, 0xcc, 0xbb, 0x55, 0xce, 0x32, 0xe0 } };
static const GUID XGAMESAVE = { 0x704c3f58, 0xe629, 0x4cc2, { 0xb1, 0x97, 0x30, 0x51, 0x1b, 0x99, 0x6f, 0xe2 } };

/* vtable slots: IUnknown 0-2, then the order of x_user_vtbl (xuser.c) and x_game_save_vtbl (xgamesave.c):
 * DuplicateHandle 3, CloseHandle 4, Compare 5, GetMaxUsers 6, AddAsync 7, AddResult 8; InitializeProvider 3, CloseProvider 6 */
typedef void (WINAPI *XUserCloseHandle_t)( void *, void * );                       /* IXUserImpl 4 */
typedef HRESULT (WINAPI *XUserAddAsync_t)( void *, UINT32, XAsyncBlock * );         /* IXUserImpl 7 */
typedef HRESULT (WINAPI *XUserAddResult_t)( void *, XAsyncBlock *, void ** );       /* IXUserImpl 8 */
typedef HRESULT (WINAPI *XGameSaveInitializeProvider_t)( void *, void *, const char *, BOOLEAN, void ** ); /* IXGameSaveImpl 3 */
typedef void (WINAPI *XGameSaveCloseProvider_t)( void *, void * );                  /* IXGameSaveImpl 6 */
typedef ULONG (WINAPI *Release_t)( void * );

#define SLOT(iface, n) ((*(void ***)(iface))[n])
#define E_PENDING_ ((HRESULT)0x8000000A)

static FILE *out;

static void say( const char *fmt, ... )
{
    va_list ap;
    va_start( ap, fmt );
    if (out) { vfprintf( out, fmt, ap ); fputc( '\n', out ); fflush( out ); }
    va_end( ap );
}

static void trim( char *s )
{
    size_t n = strlen( s );
    while (n && (s[n - 1] == '\n' || s[n - 1] == '\r' || s[n - 1] == ' ')) s[--n] = 0;
}

int main( void )
{
    char config[MAX_PATH * 2], scids[16][130], line[512];
    struct initialize_options options = { 0 };
    InitializeApiImplEx2_t initialize;
    UninitializeApiImpl_t uninitialize;
    QueryApiImpl_t query;
    void *xuser = NULL, *xsave = NULL, *user = NULL;
    XAsyncBlock async = { 0 };
    int count = 0, failed = 0, i;
    HMODULE runtime;
    HRESULT hr;
    FILE *in;

    out = fopen( "C:\\windows\\temp\\bxsaveup.log", "w" );
    if (!(in = fopen( "C:\\windows\\temp\\bxsaveup.txt", "r" ))) { say( "error no-input" ); return 2; }
    if (!fgets( config, sizeof(config), in )) { fclose( in ); say( "error no-config" ); return 2; }
    trim( config );
    while (count < 16 && fgets( line, sizeof(line), in ))
    {
        trim( line );
        if (!*line || strlen( line ) >= sizeof(scids[0])) continue;
        strcpy( scids[count++], line );
    }
    fclose( in );
    if (!count) { say( "error no-scid" ); return 2; }

    if (!(runtime = LoadLibraryA( "xgameruntime.dll" ))) { say( "error load %lu", GetLastError() ); return 3; }
    initialize = (InitializeApiImplEx2_t)GetProcAddress( runtime, "InitializeApiImplEx2" );
    query = (QueryApiImpl_t)GetProcAddress( runtime, "QueryApiImpl" );
    uninitialize = (UninitializeApiImpl_t)GetProcAddress( runtime, "UninitializeApiImpl" );
    if (!initialize || !query) { say( "error exports" ); return 3; }

    options.gameConfig = config;
    if (FAILED(hr = initialize( 10006, 4429, 2, &options ))) { say( "error initialize %#lx", hr ); return 4; }
    if (FAILED(hr = query( &XUSER, &XUSER, &xuser )) || FAILED(hr = query( &XGAMESAVE, &XGAMESAVE, &xsave )))
    { say( "error query %#lx", hr ); return 4; }

    if (FAILED(hr = ((XUserAddAsync_t)SLOT( xuser, 7 ))( xuser, 1 /* AddDefaultUserSilently */, &async )))
    { say( "error user-add %#lx", hr ); return 5; }
    for (i = 0; i < 300; i++)
    {
        hr = ((XUserAddResult_t)SLOT( xuser, 8 ))( xuser, &async, &user );
        if (hr != E_PENDING_) break;
        Sleep( 100 );
    }
    if (FAILED(hr) || !user) { say( "error user %#lx", hr ); return 5; }

    for (i = 0; i < count; i++)
    {
        void *provider = NULL;
        hr = ((XGameSaveInitializeProvider_t)SLOT( xsave, 3 ))( xsave, user, scids[i], FALSE, &provider );
        if (FAILED(hr)) { say( "scid %s error %#lx", scids[i], hr ); failed++; continue; }
        ((XGameSaveCloseProvider_t)SLOT( xsave, 6 ))( xsave, provider );
        say( "scid %s ok", scids[i] );
    }

    ((XUserCloseHandle_t)SLOT( xuser, 4 ))( xuser, user );
    ((Release_t)SLOT( xsave, 2 ))( xsave );
    ((Release_t)SLOT( xuser, 2 ))( xuser );
    if (uninitialize) uninitialize();
    say( failed ? "done with errors" : "done" );
    if (out) fclose( out );
    return failed ? 6 : 0;
}
