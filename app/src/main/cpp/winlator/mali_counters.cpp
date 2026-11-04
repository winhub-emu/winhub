// Mali GPU usage and clock from the GPU's own hardware counters, via /dev/mali0.
// From WinlatorMali (GunaCharanTeja, f5a8e92 + 82fada3, MIT); JNI names changed for this package.
// The Java side (widget/MaliGpuCounters) only calls in on a CSF-generation Mali, see there.
//
// Vendors (e.g. MediaTek on ColorOS) block every sysfs path for GPU load/clock from ordinary apps, but
// /dev/mali0 is world-accessible because every app's GPU driver needs it, and the kbase driver's
// hardware-counter reader needs no privilege. "GPU active cycles" divided by the cycles available in
// the interval gives usage; the GPU cycle counter read over a short window gives the current clock.
// Structures and ioctl numbers follow the kbase r44p1 uapi headers.
#include <errno.h>
#include <fcntl.h>
#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <unistd.h>

namespace {

constexpr uint32_t KBASE_IOCTL_TYPE = 0x80;
struct VersionCheck { uint16_t major, minor; };
struct SetFlags { uint32_t create_flags; };
struct GpuProps { uint64_t buffer; uint32_t size; uint32_t flags; };
struct ReaderSetup { uint32_t buffer_count, fe_bm, shader_bm, tiler_bm, mmu_l2_bm; };
union TimeInfo {
    struct { uint32_t request_flags; uint32_t paddings[7]; } in;
    struct { uint64_t sec; uint32_t nsec; uint32_t padding; uint64_t timestamp; uint64_t cycle_counter; } out;
};

#define IOCTL_VERSION_CHECK_CSF _IOWR(KBASE_IOCTL_TYPE, 52, VersionCheck)
#define IOCTL_SET_FLAGS         _IOW(KBASE_IOCTL_TYPE, 1, SetFlags)
#define IOCTL_GET_GPUPROPS      _IOW(KBASE_IOCTL_TYPE, 3, GpuProps)
#define IOCTL_HWCNT_READER      _IOW(KBASE_IOCTL_TYPE, 8, ReaderSetup)
#define IOCTL_TIMEINFO          _IOWR(KBASE_IOCTL_TYPE, 50, TimeInfo)

constexpr uint32_t HWCNT_READER = 0xBE;
struct ReaderCycles { uint64_t top, shader_cores; };
struct ReaderMeta { uint64_t timestamp; uint32_t event_id, buffer_idx; ReaderCycles cycles; };
#define RD_GET_HWVER    _IOR(HWCNT_READER, 0x00, uint32_t)
#define RD_GET_BUF_SIZE _IOR(HWCNT_READER, 0x01, uint32_t)
#define RD_DUMP         _IOW(HWCNT_READER, 0x10, uint32_t)
#define RD_GET_BUFFER   _IOR(HWCNT_READER, 0x20, ReaderMeta)
#define RD_PUT_BUFFER   _IOW(HWCNT_READER, 0x21, ReaderMeta)

constexpr uint32_t TIMEINFO_MONOTONIC = 1u << 0;
constexpr uint32_t TIMEINFO_CYCLES = 1u << 2;
constexpr uint32_t BUFFER_COUNT = 4;

struct Reader {
    int fd = -1;
    int rfd = -1;
    uint8_t *map = nullptr;
    uint32_t bufSize = 0;
    int activeIndex = 4;     // front-end block "GPU active cycles" on CSF GPUs
    double maxMhz = 0;
    uint64_t prevTimestamp = 0;
};

uint64_t readMaxFreqKhz(int fd) {
    GpuProps props = {0, 0, 0};
    int need = ioctl(fd, IOCTL_GET_GPUPROPS, &props);
    if (need <= 0 || need > 65536) return 0;
    uint8_t *buf = static_cast<uint8_t *>(malloc(need));
    if (!buf) return 0;
    props.buffer = reinterpret_cast<uintptr_t>(buf);
    props.size = need;
    int got = ioctl(fd, IOCTL_GET_GPUPROPS, &props);
    uint64_t khz = 0;
    for (int off = 0; off + 4 <= got;) {
        uint32_t key;
        memcpy(&key, buf + off, 4);
        off += 4;
        int size = 1 << (key & 3);
        if (off + size > got) break;
        uint64_t value = 0;
        memcpy(&value, buf + off, size);
        off += size;
        if ((key >> 2) == 6) khz = value;  // KBASE_GPUPROP_GPU_FREQ_KHZ_MAX
    }
    free(buf);
    return khz;
}

// Current clock from the GPU cycle counter. It only runs while the GPU is powered, and reads taken while
// it was idle come back as nonsense, so the caller only trusts values in a sane range.
double readClockMhz(int fd) {
    TimeInfo a{}, b{};
    a.in.request_flags = b.in.request_flags = TIMEINFO_MONOTONIC | TIMEINFO_CYCLES;
    if (ioctl(fd, IOCTL_TIMEINFO, &a) != 0) return -1;
    usleep(2000);
    if (ioctl(fd, IOCTL_TIMEINFO, &b) != 0) return -1;
    double dtNs = static_cast<double>(b.out.sec - a.out.sec) * 1e9 + (static_cast<double>(b.out.nsec) - a.out.nsec);
    if (dtNs <= 0 || b.out.cycle_counter <= a.out.cycle_counter) return -1;
    return static_cast<double>(b.out.cycle_counter - a.out.cycle_counter) / dtNs * 1000.0;
}

void closeReader(Reader *r) {
    if (!r) return;
    if (r->map) munmap(r->map, static_cast<size_t>(r->bufSize) * BUFFER_COUNT);
    if (r->rfd >= 0) close(r->rfd);
    if (r->fd >= 0) close(r->fd);
    delete r;
}

Reader *openReader() {
    Reader *r = new Reader();
    r->fd = open("/dev/mali0", O_RDWR | O_CLOEXEC);
    if (r->fd < 0) { closeReader(r); return nullptr; }

    // CSF GPUs only (Mali-G310/G510/G610/G615/G710/G715/G720 and newer). On a job-manager driver this
    // ioctl is a reserved no-op that fails. Job-manager GPUs (G52/G57/G76/G77/G78...) are skipped: on old
    // MediaTek Bifrost kernels the counter reader or time-info ioctls reboot the phone (Helio G95 report).
    VersionCheck csf = {1, 20};
    if (ioctl(r->fd, IOCTL_VERSION_CHECK_CSF, &csf) != 0) { closeReader(r); return nullptr; }
    r->activeIndex = 4;

    SetFlags flags = {0};
    if (ioctl(r->fd, IOCTL_SET_FLAGS, &flags) != 0) { closeReader(r); return nullptr; }

    r->maxMhz = readMaxFreqKhz(r->fd) / 1000.0;
    if (r->maxMhz <= 0) { closeReader(r); return nullptr; }

    ReaderSetup setup = {BUFFER_COUNT, 0xffffffff, 0, 0, 0};
    r->rfd = ioctl(r->fd, IOCTL_HWCNT_READER, &setup);
    if (r->rfd < 0) { closeReader(r); return nullptr; }

    uint32_t hwver = 0;
    // Counter layout (front-end block first, 4-byte values) is only known for the v5 format.
    if (ioctl(r->rfd, RD_GET_HWVER, &hwver) != 0 || hwver != 5 ||
        ioctl(r->rfd, RD_GET_BUF_SIZE, &r->bufSize) != 0 || r->bufSize < 64 * 4) {
        closeReader(r);
        return nullptr;
    }

    void *map = mmap(nullptr, static_cast<size_t>(r->bufSize) * BUFFER_COUNT, PROT_READ, MAP_PRIVATE, r->rfd, 0);
    if (map == MAP_FAILED) { closeReader(r); return nullptr; }
    r->map = static_cast<uint8_t *>(map);
    return r;
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_winhub_hernandez_widget_MaliGpuCounters_nativeOpen(JNIEnv *, jclass) {
    return reinterpret_cast<jlong>(openReader());
}

// out[0] = usage percent (or -1 until two samples exist), out[1] = clock in MHz (or -1 if not trustworthy).
JNIEXPORT jboolean JNICALL
Java_com_winhub_hernandez_widget_MaliGpuCounters_nativeSample(JNIEnv *env, jclass, jlong handle, jintArray out) {
    Reader *r = reinterpret_cast<Reader *>(handle);
    if (!r || env->GetArrayLength(out) < 2) return JNI_FALSE;

    if (ioctl(r->rfd, RD_DUMP, 0) != 0) return JNI_FALSE;
    ReaderMeta meta{};
    if (ioctl(r->rfd, RD_GET_BUFFER, &meta) != 0) return JNI_FALSE;
    if (meta.buffer_idx >= BUFFER_COUNT) return JNI_FALSE;
    const uint32_t *values = reinterpret_cast<const uint32_t *>(r->map + static_cast<size_t>(meta.buffer_idx) * r->bufSize);
    uint64_t activeCycles = values[r->activeIndex];
    ioctl(r->rfd, RD_PUT_BUFFER, &meta);

    double clock = readClockMhz(r->fd);
    bool clockValid = clock >= 50.0 && clock <= r->maxMhz * 1.05;

    jint result[2] = {-1, clockValid ? static_cast<jint>(clock + 0.5) : -1};
    if (r->prevTimestamp != 0 && meta.timestamp > r->prevTimestamp) {
        double seconds = (meta.timestamp - r->prevTimestamp) / 1e9;
        // Usage against the current clock (what OEM tools report); the max clock when the reading is unusable.
        double mhz = clockValid ? clock : r->maxMhz;
        double usage = activeCycles / (mhz * 1e6 * seconds) * 100.0;
        result[0] = static_cast<jint>(usage < 0 ? 0 : usage > 100 ? 100 : usage + 0.5);
    }
    r->prevTimestamp = meta.timestamp;
    env->SetIntArrayRegion(out, 0, 2, result);
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_winhub_hernandez_widget_MaliGpuCounters_nativeClose(JNIEnv *, jclass, jlong handle) {
    closeReader(reinterpret_cast<Reader *>(handle));
}

}
