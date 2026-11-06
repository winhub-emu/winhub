/*
 * hxi_core.h - the HERMES slot-file reader used by HERMES-XInput.
 *
 * Portable C99 plus the GCC/Clang __atomic builtins, no OS calls: the Windows DLL (src/xinput.c)
 * and the Linux host test (test/host_test.c) include the same code, so the protocol and the
 * XInput mapping the CI checks on Linux x86_64 and aarch64 are the ones the DLL ships.
 *
 * FILE (format 1, see core/src/layout.rs; every offset below is pinned there by const asserts)
 *   0x000 header: "HERMESIO" tag (u64), major/minor, sizes, slot, instance id (u64), retired flag,
 *         writer pid, creation time; 0x40 bell, 0x44 published index
 *   0x100 4 state copies x 128 bytes: a sequence word, then 31 data words (W_* below)
 *   the file is 4096 bytes and never grows or shrinks while in use
 *
 * PROTOCOL (core/src/proto.rs): single writer, lock-free readers. Publish n goes into copy n % 4,
 * whose sequence word is odd while the copy is written; then `published` becomes n. A reader loads
 * `published`, then that copy's sequence word (must be even), the data words, and the sequence
 * word again; it accepts the copy only if the sequence word did not change AND data word 0 (the
 * publish number) equals the `published` value it started from. The retry count is bounded.
 *
 * ORDERING: every shared word is read with an acquire load, and two explicit full fences bracket
 * the data words: one after the first sequence load, one before the second. On arm64/arm64ec they
 * are `dmb ish`; on x86 `mfence` (i386 is built with -msse2 so it is never the `lock or` form).
 * Natively x86 needs neither (TSO), but this DLL also runs as x86 code under box64/FEX, which do
 * not always emulate TSO for plain loads and do turn a fence into a real barrier. 64-bit header
 * fields are read as two 32-bit loads: a 64-bit atomic load on i386 may be a locked cmpxchg8b,
 * which writes and would fault on the read-only mapping.
 */
#ifndef HXI_CORE_H
#define HXI_CORE_H

#include <stddef.h>
#include <stdint.h>

/* layout (format 1) */
#define HXI_MAGIC_LO 0x4d524548u /* "HERM" */
#define HXI_MAGIC_HI 0x4f495345u /* "ESIO" */
#define HXI_FORMAT_MAJOR 1u
#define HXI_FILE_SIZE 4096u
#define HXI_HEADER_SIZE 256u
#define HXI_COPY_SIZE 128u
#define HXI_COPY_COUNT 4u
#define HXI_COPY_WORDS 31u
#define HXI_JOURNAL_LEN 256u

#define HXI_H_MAGIC 0u
#define HXI_H_MAJOR 8u
#define HXI_H_MINOR 12u
#define HXI_H_FILE_SIZE 16u
#define HXI_H_HEADER_SIZE 20u
#define HXI_H_COPY_SIZE 24u
#define HXI_H_COPY_COUNT 28u
#define HXI_H_JOURNAL_LEN 32u
#define HXI_H_SLOT 36u
#define HXI_H_INSTANCE 40u
#define HXI_H_RETIRED 48u
#define HXI_H_WRITER_PID 52u
#define HXI_H_PUBLISHED 68u
#define HXI_COPIES_OFF 256u

/* data words of one state copy */
#define HXI_W_PUB 0u
#define HXI_W_FLAGS 1u
#define HXI_W_UNPLUG 2u
#define HXI_W_EDGES 3u
#define HXI_W_COUNTS 4u
#define HXI_BUTTONS 11u
#define HXI_W_AXES 15u
#define HXI_AXES 8u
#define HXI_FLAG_CONNECTED 1u

/* canonical button index (core/src/abi.rs: ascending evdev code order) */
enum { HXI_B_A, HXI_B_B, HXI_B_X, HXI_B_Y, HXI_B_TL, HXI_B_TR, HXI_B_SELECT, HXI_B_START, HXI_B_MODE,
       HXI_B_THUMBL, HXI_B_THUMBR };
/* canonical axis index: X, Y, RX, RY, BRAKE (left trigger), GAS (right trigger), HAT0X, HAT0Y */
enum { HXI_A_LX, HXI_A_LY, HXI_A_RX, HXI_A_RY, HXI_A_LT, HXI_A_RT, HXI_A_HATX, HXI_A_HATY };

/* XINPUT_GAMEPAD wButtons bits (public XInput API values) */
#define HXI_XI_DPAD_UP 0x0001u
#define HXI_XI_DPAD_DOWN 0x0002u
#define HXI_XI_DPAD_LEFT 0x0004u
#define HXI_XI_DPAD_RIGHT 0x0008u
#define HXI_XI_START 0x0010u
#define HXI_XI_BACK 0x0020u
#define HXI_XI_LEFT_THUMB 0x0040u
#define HXI_XI_RIGHT_THUMB 0x0080u
#define HXI_XI_LEFT_SHOULDER 0x0100u
#define HXI_XI_RIGHT_SHOULDER 0x0200u
#define HXI_XI_GUIDE 0x0400u
#define HXI_XI_A 0x1000u
#define HXI_XI_B 0x2000u
#define HXI_XI_X 0x4000u
#define HXI_XI_Y 0x8000u

/* canonical button index -> XInput bit */
static const uint16_t HXI_BUTTON_BITS[HXI_BUTTONS] = {
    HXI_XI_A, HXI_XI_B, HXI_XI_X, HXI_XI_Y, HXI_XI_LEFT_SHOULDER, HXI_XI_RIGHT_SHOULDER,
    HXI_XI_BACK, HXI_XI_START, HXI_XI_GUIDE, HXI_XI_LEFT_THUMB, HXI_XI_RIGHT_THUMB,
};

/* One XInput gamepad report, in XINPUT_GAMEPAD field order. */
typedef struct hxi_pad {
    uint16_t buttons;
    uint8_t lt, rt;
    int16_t lx, ly, rx, ry;
} hxi_pad;

static inline const uint32_t *hxi_word(const uint8_t *base, uint32_t off) {
    return (const uint32_t *)(const void *)(base + off);
}

static inline uint32_t hxi_ld(const uint8_t *base, uint32_t off) {
    return __atomic_load_n(hxi_word(base, off), __ATOMIC_ACQUIRE);
}

static inline void hxi_fence(void) {
    __atomic_thread_fence(__ATOMIC_SEQ_CST);
}

static inline void hxi_pause(void) {
#if defined(__aarch64__) || defined(__arm64ec__) || defined(_M_ARM64EC) || defined(__arm__)
    __asm__ __volatile__("yield" ::: "memory");
#elif defined(__i386__) || defined(__x86_64__)
    __asm__ __volatile__("pause" ::: "memory");
#else
    __atomic_signal_fence(__ATOMIC_SEQ_CST);
#endif
}

/* 1 if `base` (a mapping of at least HXI_FILE_SIZE bytes) is a format-1 slot file for `slot`. The
 * tag is read first: the writer stores it last, with release, after the rest of the header. */
static inline int hxi_valid(const uint8_t *base, uint32_t slot) {
    if (hxi_ld(base, HXI_H_MAGIC) != HXI_MAGIC_LO || hxi_ld(base, HXI_H_MAGIC + 4) != HXI_MAGIC_HI)
        return 0;
    hxi_fence();
    return hxi_ld(base, HXI_H_MAJOR) == HXI_FORMAT_MAJOR
        && hxi_ld(base, HXI_H_HEADER_SIZE) == HXI_HEADER_SIZE
        && hxi_ld(base, HXI_H_COPY_SIZE) == HXI_COPY_SIZE
        && hxi_ld(base, HXI_H_COPY_COUNT) == HXI_COPY_COUNT
        && hxi_ld(base, HXI_H_JOURNAL_LEN) == HXI_JOURNAL_LEN
        && hxi_ld(base, HXI_H_FILE_SIZE) >= HXI_FILE_SIZE
        && hxi_ld(base, HXI_H_SLOT) == slot;
}

static inline int hxi_retired(const uint8_t *base) {
    return hxi_ld(base, HXI_H_RETIRED) != 0;
}

/* The file's instance id (never 0 for a live file; constant for the file's lifetime). */
static inline uint64_t hxi_instance(const uint8_t *base) {
    return (uint64_t)hxi_ld(base, HXI_H_INSTANCE) | ((uint64_t)hxi_ld(base, HXI_H_INSTANCE + 4) << 32);
}

/* A consistent copy of the latest published state into out[]. Returns 1, or 0 if every one of
 * `attempts` reads was overtaken by the writer lapping the ring (the caller keeps its last state). */
static inline int hxi_snapshot(const uint8_t *base, uint32_t out[HXI_COPY_WORDS], unsigned attempts) {
    for (unsigned i = 0; i < attempts; i++) {
        uint32_t n = hxi_ld(base, HXI_H_PUBLISHED);
        uint32_t c = HXI_COPIES_OFF + (n % HXI_COPY_COUNT) * HXI_COPY_SIZE;
        uint32_t s1 = hxi_ld(base, c);
        if (s1 & 1u) {
            hxi_pause();
            continue;
        }
        hxi_fence();
        for (uint32_t k = 0; k < HXI_COPY_WORDS; k++)
            out[k] = hxi_ld(base, c + 4u + 4u * k);
        hxi_fence();
        if (hxi_ld(base, c) == s1 && out[HXI_W_PUB] == n)
            return 1;
        hxi_pause();
    }
    return 0;
}

/* Pressed-button mask (bit = canonical index) from the per-button edge counters: every button
 * starts released, so an odd count means pressed. */
static inline uint16_t hxi_pressed(const uint32_t w[HXI_COPY_WORDS]) {
    uint16_t m = 0;
    for (uint32_t b = 0; b < HXI_BUTTONS; b++)
        m |= (uint16_t)((w[HXI_W_COUNTS + b] & 1u) << b);
    return m;
}

/* Buttons pressed AND released again since `seen` (the counters a previous read saw), i.e. taps a
 * poll would otherwise miss: released then and now, but at least two edges in between. */
static inline uint16_t hxi_missed_taps(const uint32_t w[HXI_COPY_WORDS], const uint32_t seen[HXI_BUTTONS]) {
    uint16_t m = 0;
    for (uint32_t b = 0; b < HXI_BUTTONS; b++) {
        uint32_t now = w[HXI_W_COUNTS + b];
        if ((now & 1u) == 0 && (seen[b] & 1u) == 0 && (uint32_t)(now - seen[b]) >= 2u)
            m |= (uint16_t)(1u << b);
    }
    return m;
}

static inline int32_t hxi_axis(const uint32_t w[HXI_COPY_WORDS], uint32_t a) {
    return (int32_t)w[HXI_W_AXES + a];
}

static inline int16_t hxi_clamp16(int32_t v) {
    return (int16_t)(v < -32768 ? -32768 : v > 32767 ? 32767 : v);
}

static inline uint8_t hxi_clamp8(int32_t v) {
    return (uint8_t)(v < 0 ? 0 : v > 255 ? 255 : v);
}

/* XInput Y: up is positive. evdev Y (what HERMES carries): down is positive. -(-32768) = 32768
 * does not fit a SHORT and clamps to 32767. */
static inline int16_t hxi_stick_y(int32_t v) {
    return hxi_clamp16(v == INT32_MIN ? INT32_MAX : -v);
}

/* The XInput report for a snapshot. `buttons` is the canonical pressed mask to show (pressed, plus
 * any missed taps the caller wants to surface); `guide` = 0 hides the Guide button (XInputGetState),
 * 1 reports it (XInputGetStateEx). */
static inline void hxi_map(const uint32_t w[HXI_COPY_WORDS], uint16_t buttons, int guide, hxi_pad *out) {
    uint16_t xb = 0;
    for (uint32_t b = 0; b < HXI_BUTTONS; b++)
        if (buttons & (1u << b))
            xb |= HXI_BUTTON_BITS[b];
    int32_t hx = hxi_axis(w, HXI_A_HATX), hy = hxi_axis(w, HXI_A_HATY);
    if (hx < 0) xb |= HXI_XI_DPAD_LEFT;
    if (hx > 0) xb |= HXI_XI_DPAD_RIGHT;
    if (hy < 0) xb |= HXI_XI_DPAD_UP;
    if (hy > 0) xb |= HXI_XI_DPAD_DOWN;
    if (!guide) xb &= (uint16_t)~HXI_XI_GUIDE;
    out->buttons = xb;
    out->lt = hxi_clamp8(hxi_axis(w, HXI_A_LT));
    out->rt = hxi_clamp8(hxi_axis(w, HXI_A_RT));
    out->lx = hxi_clamp16(hxi_axis(w, HXI_A_LX));
    out->ly = hxi_stick_y(hxi_axis(w, HXI_A_LY));
    out->rx = hxi_clamp16(hxi_axis(w, HXI_A_RX));
    out->ry = hxi_stick_y(hxi_axis(w, HXI_A_RY));
}

/* CONTROL BLOCK (format minor 5, 0x0B00..0x0BFF; written by the app, read-only here). All u32 LE.
 * Seqlock: the writer makes ctl_seq odd, writes the fields, bumps ctl_gen, makes ctl_seq even again
 * (release). A reader checks ctl_gen with one acquire load per call and re-reads the block only when
 * it changed: ctl_seq even, fence, the fields, fence, ctl_seq unchanged, at most `attempts` times;
 * when every attempt is overtaken it keeps its last good copy. Used only if the header's minor is at
 * least 5, the magic is "HCTL" and the size at least 256; otherwise the DLL behaves as before it. */
#define HXI_CTL_MIN_MINOR 5u
#define HXI_CTL_OFF 0xb00u
#define HXI_CTL_MAGIC 0x4c544348u /* "HCTL" */
#define HXI_CTL_SIZE_MIN 256u
#define HXI_C_MAGIC 0x00u
#define HXI_C_SIZE 0x04u
#define HXI_C_SEQ 0x08u
#define HXI_C_GEN 0x0cu
#define HXI_C_AXIS_MODE 0x10u
#define HXI_C_AXIS_HZ 0x14u
#define HXI_C_AXIS_AUTO_MAX 0x18u
#define HXI_C_XI_MODE 0x1cu
#define HXI_C_XI_FLAGS 0x20u
#define HXI_C_ID_FLAGS 0x40u
#define HXI_C_ID_BUS 0x44u
#define HXI_C_ID_VENDOR 0x48u
#define HXI_C_ID_PRODUCT 0x4cu
#define HXI_C_ID_VERSION 0x50u

#define HXI_XI_MODE_UNSET 0u   /* = direct */
#define HXI_XI_MODE_DIRECT 1u
#define HXI_XI_MODE_FORWARD 2u
#define HXI_XIF_FORWARD_AVAILABLE 0x1u
#define HXI_XIF_HIDDEN 0x2u
#define HXI_IDF_VALID 0x1u

typedef struct hxi_ctl {
    uint32_t gen;
    uint32_t xi_mode, xi_flags;
    uint32_t id_flags, id_bus, id_vendor, id_product, id_version;
} hxi_ctl;

/* 1 if the file carries a usable control block. */
static inline int hxi_ctl_present(const uint8_t *base) {
    return hxi_ld(base, HXI_H_MINOR) >= HXI_CTL_MIN_MINOR
        && hxi_ld(base, HXI_CTL_OFF + HXI_C_MAGIC) == HXI_CTL_MAGIC
        && hxi_ld(base, HXI_CTL_OFF + HXI_C_SIZE) >= HXI_CTL_SIZE_MIN;
}

/* The change counter: one acquire load, the per-call check. */
static inline uint32_t hxi_ctl_gen(const uint8_t *base) {
    return hxi_ld(base, HXI_CTL_OFF + HXI_C_GEN);
}

/* A consistent copy of the block into *out. Returns 1, or 0 if every attempt was overtaken by the
 * writer (the caller keeps its last good copy). Never blocks, never writes. */
static inline int hxi_ctl_read(const uint8_t *base, hxi_ctl *out, unsigned attempts) {
    for (unsigned i = 0; i < attempts; i++) {
        uint32_t s1 = hxi_ld(base, HXI_CTL_OFF + HXI_C_SEQ);
        if (s1 & 1u) {
            hxi_pause();
            continue;
        }
        hxi_fence();
        hxi_ctl c;
        c.gen = hxi_ld(base, HXI_CTL_OFF + HXI_C_GEN);
        c.xi_mode = hxi_ld(base, HXI_CTL_OFF + HXI_C_XI_MODE);
        c.xi_flags = hxi_ld(base, HXI_CTL_OFF + HXI_C_XI_FLAGS);
        c.id_flags = hxi_ld(base, HXI_CTL_OFF + HXI_C_ID_FLAGS);
        c.id_bus = hxi_ld(base, HXI_CTL_OFF + HXI_C_ID_BUS);
        c.id_vendor = hxi_ld(base, HXI_CTL_OFF + HXI_C_ID_VENDOR);
        c.id_product = hxi_ld(base, HXI_CTL_OFF + HXI_C_ID_PRODUCT);
        c.id_version = hxi_ld(base, HXI_CTL_OFF + HXI_C_ID_VERSION);
        hxi_fence();
        if (hxi_ld(base, HXI_CTL_OFF + HXI_C_SEQ) == s1) {
            *out = c;
            return 1;
        }
        hxi_pause();
    }
    return 0;
}

/* "<slot>=<path>[;<slot>=<path>...]" (FAKE_EVDEV_MEMFD_PATHS, the grammar of core/src/guest.rs:
 * blanks around keys and paths are trimmed, malformed entries skipped, the first entry for a slot
 * wins). Calls fn(ctx, slot, path, len) for each accepted entry; returns the mask of slots. Generic
 * over the character type through the macro so the DLL can parse the WCHAR environment. */
#define HXI_DEFINE_PARSE_PATHS(name, CH)                                                         \
    static inline uint32_t name(const CH *s, void (*fn)(void *, uint32_t, const CH *, size_t),     \
                                void *ctx) {                                                       \
        uint32_t mask = 0;                                                                         \
        while (s && *s) {                                                                          \
            const CH *e = s;                                                                       \
            while (*e && *e != ';') e++;                                                           \
            const CH *eq = s;                                                                      \
            while (eq < e && *eq != '=') eq++;                                                     \
            if (eq < e) {                                                                          \
                const CH *k0 = s, *k1 = eq, *p0 = eq + 1, *p1 = e;                                 \
                while (k0 < k1 && (*k0 == ' ' || *k0 == '\t' || *k0 == '\r' || *k0 == '\n')) k0++; \
                while (k1 > k0 && (k1[-1] == ' ' || k1[-1] == '\t' || k1[-1] == '\r' || k1[-1] == '\n')) k1--; \
                while (p0 < p1 && (*p0 == ' ' || *p0 == '\t' || *p0 == '\r' || *p0 == '\n')) p0++; \
                while (p1 > p0 && (p1[-1] == ' ' || p1[-1] == '\t' || p1[-1] == '\r' || p1[-1] == '\n')) p1--; \
                if (k1 - k0 == 1 && *k0 >= '0' && *k0 <= '3' && p1 > p0) {                         \
                    uint32_t slot = (uint32_t)(*k0 - '0');                                         \
                    if (!(mask & (1u << slot))) {                                                  \
                        mask |= 1u << slot;                                                        \
                        fn(ctx, slot, p0, (size_t)(p1 - p0));                                      \
                    }                                                                              \
                }                                                                                  \
            }                                                                                      \
            s = *e ? e + 1 : e;                                                                    \
        }                                                                                          \
        return mask;                                                                               \
    }

#endif /* HXI_CORE_H */
