/*
 * host_test - the HERMES-XInput reader (src/hxi_core.h, the exact code the DLL ships) on Linux.
 *
 *   host_test unit                      the XInput mapping and the path grammar
 *   host_test stress <pad file> <secs>  read a slot file while the feeder ("feeder stress") publishes
 *                                       as fast as it can, checking that no snapshot is torn or goes
 *                                       backwards and that the mapping of each one is exact
 *
 * CI runs it on x86_64 and on aarch64 runners: on aarch64 the acquire loads and the two fences are
 * real ldar/dmb instructions, the same ones the arm64ec DLL executes.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>

#include "../src/hxi_core.h"

static int fails, checks;
#define CHECK(c, ...)                                         \
    do {                                                      \
        checks++;                                             \
        if (!(c)) {                                           \
            fails++;                                          \
            printf("FAIL %s:%d: ", __FILE__, __LINE__);       \
            printf(__VA_ARGS__);                              \
            printf("\n");                                     \
        }                                                     \
    } while (0)

static void words(uint32_t w[HXI_COPY_WORDS], uint16_t pressed, const int32_t axes[HXI_AXES]) {
    memset(w, 0, sizeof(uint32_t) * HXI_COPY_WORDS);
    w[HXI_W_FLAGS] = HXI_FLAG_CONNECTED;
    for (uint32_t b = 0; b < HXI_BUTTONS; b++) w[HXI_W_COUNTS + b] = (pressed >> b) & 1u;
    for (uint32_t a = 0; a < HXI_AXES; a++) w[HXI_W_AXES + a] = (uint32_t)axes[a];
}

struct got { uint32_t mask; char path[4][64]; };
static void take(void *ctx, uint32_t slot, const char *p, size_t n) {
    struct got *g = ctx;
    if (n > 63) n = 63;
    memcpy(g->path[slot], p, n);
    g->path[slot][n] = 0;
}
HXI_DEFINE_PARSE_PATHS(parse_paths, char)

static int unit(void) {
    static const int32_t zero[HXI_AXES] = {0};
    uint32_t w[HXI_COPY_WORDS];
    hxi_pad p;

    /* every canonical button reaches its XInput bit; Guide only with guide=1 */
    static const uint16_t want[HXI_BUTTONS] = {0x1000, 0x2000, 0x4000, 0x8000, 0x0100, 0x0200, 0x0020, 0x0010, 0x0400, 0x0040, 0x0080};
    for (uint32_t b = 0; b < HXI_BUTTONS; b++) {
        words(w, (uint16_t)(1u << b), zero);
        hxi_map(w, hxi_pressed(w), 1, &p);
        CHECK(p.buttons == want[b], "button %u -> %04x, want %04x", b, p.buttons, want[b]);
        hxi_map(w, hxi_pressed(w), 0, &p);
        CHECK(p.buttons == (b == HXI_B_MODE ? 0 : want[b]), "button %u without guide -> %04x", b, p.buttons);
    }
    /* parity: 3 edges = pressed, 4 = released */
    words(w, 0, zero);
    w[HXI_W_COUNTS + HXI_B_A] = 3;
    w[HXI_W_COUNTS + HXI_B_B] = 4;
    CHECK(hxi_pressed(w) == 1u, "parity");

    /* sticks: evdev +Y down -> XInput +Y up, with the -(-32768) clamp */
    int32_t ax[HXI_AXES] = {-32768, -32768, 32767, 32767, 255, 0, 0, 0};
    words(w, 0, ax);
    hxi_map(w, 0, 0, &p);
    CHECK(p.lx == -32768 && p.ly == 32767 && p.rx == 32767 && p.ry == -32767, "sticks %d %d %d %d", p.lx, p.ly, p.rx, p.ry);
    CHECK(p.lt == 255 && p.rt == 0, "triggers %u %u", p.lt, p.rt);
    int32_t ax2[HXI_AXES] = {1, 1, -1, -1, 300, -5, 0, 0};
    words(w, 0, ax2);
    hxi_map(w, 0, 0, &p);
    CHECK(p.lx == 1 && p.ly == -1 && p.rx == -1 && p.ry == 1 && p.lt == 255 && p.rt == 0, "small values / trigger clamp");

    /* hats: HAT0X -1 left, +1 right; HAT0Y -1 up, +1 down */
    int32_t h1[HXI_AXES] = {0, 0, 0, 0, 0, 0, -1, -1};
    words(w, 0, h1);
    hxi_map(w, 0, 0, &p);
    CHECK(p.buttons == (HXI_XI_DPAD_LEFT | HXI_XI_DPAD_UP), "hat up-left -> %04x", p.buttons);
    int32_t h2[HXI_AXES] = {0, 0, 0, 0, 0, 0, 1, 1};
    words(w, 0, h2);
    hxi_map(w, 0, 0, &p);
    CHECK(p.buttons == (HXI_XI_DPAD_RIGHT | HXI_XI_DPAD_DOWN), "hat down-right -> %04x", p.buttons);

    /* missed taps: released then and now with >= 2 edges between */
    uint32_t seen[HXI_BUTTONS] = {0};
    words(w, 0, zero);
    w[HXI_W_COUNTS + HXI_B_X] = 2;  /* tapped once */
    w[HXI_W_COUNTS + HXI_B_Y] = 1;  /* pressed now: not a missed tap */
    w[HXI_W_COUNTS + HXI_B_A] = 4;  /* tapped twice */
    CHECK(hxi_missed_taps(w, seen) == ((1u << HXI_B_X) | (1u << HXI_B_A)), "missed taps %04x", hxi_missed_taps(w, seen));
    seen[HXI_B_X] = 2;
    seen[HXI_B_A] = 4;
    CHECK(hxi_missed_taps(w, seen) == 0, "taps already seen are not reported again");
    seen[HXI_B_B] = 1; /* was pressed */
    w[HXI_W_COUNTS + HXI_B_B] = 4;
    CHECK(hxi_missed_taps(w, seen) == 0, "a release (and re-tap) of a held button is not a missed tap");

    /* FAKE_EVDEV_MEMFD_PATHS grammar (core/src/guest.rs parse_paths) */
    struct got g;
    memset(&g, 0, sizeof g);
    uint32_t m = parse_paths(" 0 = /a/pad0 ;1=/b;9=/c;0=/dup;2=;x=/y;; 3=/d/pad3", take, &g);
    CHECK(m == 0xbu, "mask %x", m);
    CHECK(!strcmp(g.path[0], "/a/pad0") && !strcmp(g.path[1], "/b") && !strcmp(g.path[3], "/d/pad3") && !g.path[2][0],
          "paths '%s' '%s' '%s' '%s'", g.path[0], g.path[1], g.path[2], g.path[3]);
    CHECK(parse_paths("", take, &g) == 0 && parse_paths(NULL, take, &g) == 0, "empty");

    /* magic */
    CHECK(!memcmp("HERMESIO", (const char[]){'H', 'E', 'R', 'M', 'E', 'S', 'I', 'O'}, 8), "tag");
    uint32_t lo = HXI_MAGIC_LO, hi = HXI_MAGIC_HI;
    char tag[9] = {0};
    memcpy(tag, &lo, 4);
    memcpy(tag + 4, &hi, 4);
    CHECK(!strcmp(tag, "HERMESIO"), "magic words spell %s", tag);

    printf("unit: %d checks, %d failed\n", checks, fails);
    return fails ? 1 : 0;
}

static double mono(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (double)t.tv_sec + (double)t.tv_nsec * 1e-9;
}

static int stress(const char *path, double secs) {
    int fd = -1;
    for (int i = 0; i < 500 && fd < 0; i++) {
        fd = open(path, O_RDONLY | O_CLOEXEC);
        if (fd < 0) usleep(10000);
    }
    if (fd < 0) {
        perror(path);
        return 2;
    }
    const uint8_t *base = mmap(NULL, HXI_FILE_SIZE, PROT_READ, MAP_SHARED, fd, 0);
    close(fd);
    if (base == MAP_FAILED) {
        perror("mmap");
        return 2;
    }
    CHECK(hxi_valid(base, 0), "not a valid slot-0 file");
    CHECK(!hxi_retired(base), "retired");
    CHECK(hxi_instance(base) != 0, "instance 0");
    uint32_t w[HXI_COPY_WORDS], last = 0;
    unsigned long ok = 0, busy = 0, torn = 0, back = 0, changes = 0, mapped_bad = 0;
    double t0 = mono();
    while (mono() - t0 < secs) {
        for (int k = 0; k < 1000; k++) {
            if (!hxi_snapshot(base, w, 64)) {
                busy++;
                continue;
            }
            ok++;
            if (ok > 1 && (int32_t)(w[HXI_W_PUB] - last) < 0) back++;
            if (w[HXI_W_PUB] != last) changes++;
            last = w[HXI_W_PUB];
            if (!(w[HXI_W_FLAGS] & HXI_FLAG_CONNECTED)) continue; /* before the feeder opened the slot */
            /* the feeder derives every field from the low 16 bits of its counter (feeder/src/main.rs) */
            int32_t lx = hxi_axis(w, HXI_A_LX);
            if (lx == 0 && hxi_axis(w, HXI_A_LY) == 0) continue; /* the neutral first publish */
            uint32_t low = (uint32_t)(lx + 32768);
            int good = hxi_axis(w, HXI_A_LY) == -1 - lx && hxi_axis(w, HXI_A_RX) == lx && hxi_axis(w, HXI_A_RY) == -1 - lx
                && hxi_axis(w, HXI_A_LT) == (int32_t)(low & 0xffu) && hxi_axis(w, HXI_A_RT) == 255 - (int32_t)(low & 0xffu)
                && hxi_axis(w, HXI_A_HATX) == (int32_t)(low % 3u) - 1 && hxi_axis(w, HXI_A_HATY) == (int32_t)((low / 3u) % 3u) - 1
                && hxi_pressed(w) == (uint16_t)(low & 0x7ffu);
            if (!good) {
                if (torn < 5)
                    printf("torn snapshot: pub %u lx %d ly %d lt %d buttons %03x\n", w[HXI_W_PUB], lx,
                           hxi_axis(w, HXI_A_LY), hxi_axis(w, HXI_A_LT), hxi_pressed(w));
                torn++;
                continue;
            }
            hxi_pad p;
            hxi_map(w, hxi_pressed(w), 1, &p);
            int32_t ly = -1 - lx, ny = -ly;
            if (p.lx != lx || p.ly != (ny > 32767 ? 32767 : ny) || p.rx != lx || (uint32_t)p.lt != (low & 0xffu)
                || (uint32_t)p.rt != 255u - (low & 0xffu))
                mapped_bad++;
        }
    }
    printf("stress: %lu snapshots (%lu distinct publishes seen), %lu overtaken, %lu torn, %lu backwards, %lu mis-mapped\n",
           ok, changes, busy, torn, back, mapped_bad);
    CHECK(torn == 0, "%lu torn snapshots", torn);
    CHECK(back == 0, "%lu snapshots went backwards", back);
    CHECK(mapped_bad == 0, "%lu snapshots mapped wrongly", mapped_bad);
    CHECK(ok > 100000, "only %lu snapshots", ok);
    CHECK(changes > 1000, "the reader saw only %lu publishes (is the mapping live?)", changes);
    CHECK(busy * 4 < ok, "overtaken too often (%lu of %lu)", busy, ok + busy);
    printf("stress: %d checks, %d failed\n", checks, fails);
    return fails ? 1 : 0;
}

int main(int argc, char **argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    if (argc >= 2 && !strcmp(argv[1], "unit")) return unit();
    if (argc >= 4 && !strcmp(argv[1], "stress")) return stress(argv[2], atof(argv[3]));
    fprintf(stderr, "usage: host_test unit | stress <pad file> <seconds>\n");
    return 2;
}
