/*
 * The fake evdev layer. Taken from WinNative (maxjivi05, main 0cfc070b + feature/wayland-gamescope
 * through f1f34cd7, 2026-09-14..19), GPL-3.0: the locking, the fork handlers, the shared device
 * table, the Steam virtual-gamepad identity and the Z/RZ trigger placement are that author's.
 * Added here: opt-in identities (FAKE_EVDEV_IDENTITY, e.g. xbox360), which is what gets SDL's
 * built-in mapping applied with no configuration when the pad is read by the Steam client itself.
 */
#include <algorithm>
#include <cerrno>
#include <climits>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <iostream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>

#include <dirent.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <linux/input.h>
#include <linux/joystick.h>
#include <poll.h>
#include <pthread.h>
#include <signal.h>
#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <sys/inotify.h>
#include <sys/ioctl.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/sysmacros.h>
#include <sys/time.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <sys/un.h>
#include <unistd.h>

#include "../hermes/include/hermes.h"
#define EXPORT __attribute__((visibility("default"))) extern "C"

// glibc declares the request as unsigned long; bionic as int. The same source also serves the
// Linux runtime's preload (tools/linuxfs), where Steam and SDL run against glibc.
#ifdef __GLIBC__
typedef unsigned long ioctl_request_t;
#else
typedef int ioctl_request_t;
#endif

static constexpr uint16_t GAMEPAD_VENDOR_ID_BASE = 0x1234;
static constexpr uint16_t GAMEPAD_PRODUCT_ID_BASE = 0x5678;
static constexpr uint16_t GAMEPAD_VERSION = 0x0110;
static constexpr const char *GAMEPAD_NAME_TEMPLATE = "Generic HID Gamepad %d";
// The gamepad Steam Input presents to a game, and the name SDL reads its slot from.
static constexpr uint16_t STEAM_VIRTUAL_VENDOR_ID = 0x28de;
static constexpr uint16_t STEAM_VIRTUAL_PRODUCT_ID = 0x11ff;
static constexpr const char *STEAM_VIRTUAL_NAME_TEMPLATE = "Microsoft X-Box 360 pad %d";
// Opt-in identity for the process that reads the pad directly (the Steam client): SDL keys its
// mapping database on bus+vendor+product. FAKE_EVDEV_IDENTITY picks an entry of this table; unset,
// empty or unknown keeps the generic identity. An identity changes only what the pad says it is
// (EVIOCGID vendor/product, EVIOCGNAME, JSIOCGNAME); buttons, axes and force feedback are the same.
// Not offset per slot: a real pad reports the same ids on every port. The same table, with the
// same values, is in fakeinput.cpp (the Wine copy) and core/ControllerIdentity.java (the app).
struct PadIdentity {
  const char *value;
  uint16_t vendor;
  uint16_t product;
  const char *name_template;
};
static constexpr PadIdentity PAD_IDENTITIES[] = {
    {"xbox360", 0x045E, 0x028E, "Xbox 360 Controller (%d)"},
    {"xboxone", 0x045E, 0x02EA, "Xbox One S Controller (%d)"},
    {"xboxseries", 0x045E, 0x0B12, "Xbox Series X Controller (%d)"},
    {"steamvirtual", STEAM_VIRTUAL_VENDOR_ID, STEAM_VIRTUAL_PRODUCT_ID, STEAM_VIRTUAL_NAME_TEMPLATE},
    {"dinputpad", 0x1209, 0x0001, "USB Gamepad (%d)"},
    {"ds4", 0x054C, 0x09CC, "Wireless Controller (%d)"},
    {"dualsense", 0x054C, 0x0CE6, "DualSense Wireless Controller (%d)"},
    {"switchpro", 0x057E, 0x2009, "Pro Controller (%d)"},
    {"joyconpair", 0x057E, 0x2008, "Nintendo Switch Combined Joy-Cons (%d)"},
    {"steamdeck", 0x28DE, 0x1205, "Steam Deck Controller (%d)"},
};

static const PadIdentity *find_pad_identity(const char *value) {
  if (!value || !*value)
    return nullptr;
  for (const PadIdentity &entry : PAD_IDENTITIES)
    if (!strcmp(entry.value, value))
      return &entry;
  return nullptr;
}

// The slot's identity from HERMES's control block. The app sets it for every slot and can change
// it in-game; the pad then reconnects and the new attach takes the new identity. False when the slot
// file carries none (-ENODATA, an older app): FAKE_EVDEV_IDENTITY / the generic identity apply.
static bool live_pad_identity(hermes_handle handle, struct hermes_identity *out) {
  return handle && hermes_identity(handle, out, sizeof(*out)) == 0;
}

// The live identity's name into an EVIOCGNAME / JSIOCGNAME buffer (copied as text, never as a format).
static void copy_live_name(int op, void *argp, const struct hermes_identity &live) {
  size_t size = _IOC_SIZE(op);
  if (!argp || size == 0)
    return;
  snprintf(static_cast<char *>(argp), size, "%.*s", static_cast<int>(sizeof(live.name)), live.name);
}
static constexpr const char *GAMEPAD_PHYS_TEMPLATE = "usb-fakeinput/input%d";
static constexpr const char *GAMEPAD_UNIQ_TEMPLATE = "0000000000%02d";
static constexpr uint8_t GAMEPAD_AXIS_COUNT = 8;
static constexpr uint8_t GAMEPAD_BUTTON_COUNT = 11;
static constexpr unsigned int FAKE_INPUT_MAJOR = 13;
static constexpr unsigned int FAKE_INPUT_EVENT_MINOR_BASE = 64;
static constexpr unsigned int FAKE_INPUT_JS_MINOR_BASE = 0;

static_assert(sizeof(struct input_event) == HERMES_EVENT_SIZE,
              "hermes writes the platform's struct input_event");

// One open pad fd. The fd itself belongs to hermes (a real kernel fd whose readiness hermes
// keeps current), so poll/select/epoll on it need no help from this library.
struct FakeController {
  char *event = nullptr;
  int slot = -1;
  hermes_handle handle = 0;

  ~FakeController() { free(event); }
};

// hermes's canonical button and axis order (index -> evdev code). Index 8 is BTN_MODE, the
// Steam button: the client's in-game menu is bound to it, and SDL reports it as button 8.
static const uint16_t kPadButtonCodes[HERMES_BUTTONS] = HERMES_BUTTON_CODES;
static const uint16_t kPadAxisCodes[HERMES_AXES] = HERMES_AXIS_CODES;

// An LD_PRELOAD interposer is called outside its own lifetime. The loader initialises libraries
// in dependency order, and the hooks below belong to whichever library gets there first: on the
// Linux runtime, libpython's initialiser runs before ours and calls close() and read(), so a hook
// fires before this library has been initialised at all. A file-scope std::unordered_map or
// std::recursive_mutex has no object yet at that moment - touching one is undefined behaviour,
// and in practice the process dies before main(). The same window reopens at exit, after static
// destructors have run. (Proton is a Python script, so this killed every game launched from the
// native Steam client, and the compatibility-tool registrar with it.)
//
// So everything with a constructor lives behind an accessor that builds it on first use and
// never destroys it. The leak is deliberate and bounded: one map or mutex for the process.
namespace Logger {
extern int log_enabled;
void init();
}

// hermes's diagnostics, under FAKE_EVDEV_LOG. Written with one raw write(2): hermes may call
// this from its own helper thread, which must never wait on stdio's lock or re-enter a hook.
static void hermes_log_line(int level, const char *line) {
  char out[512];
  int n = snprintf(out, sizeof(out), "fakeinput%s: %s\n", level ? " (warning)" : "",
                   line ? line : "");
  if (n <= 0)
    return;
  if (n >= static_cast<int>(sizeof(out)))
    n = sizeof(out) - 1;
  syscall(SYS_write, STDERR_FILENO, out, static_cast<size_t>(n));
}

static void controller_fork_prepare();
static void controller_fork_parent();
static void controller_fork_child();

struct FakeConfig {
  const char *hook_dir;
  const char *udev_data_dir;
  bool vibration_enabled;
  const PadIdentity *identity; // null = the generic identity
};

static FakeConfig &config() {
  static FakeConfig *cfg = [] {
    auto *c = new FakeConfig();
    const char *dir = getenv("FAKE_EVDEV_DIR");
    c->hook_dir = dir ? dir : "/data/data/com.termux/files/home/fake-input";
    c->udev_data_dir = getenv("FAKE_UDEV_DATA_DIR");
    c->vibration_enabled =
        getenv("FAKE_EVDEV_VIBRATION") && atoi(getenv("FAKE_EVDEV_VIBRATION"));
    c->identity = find_pad_identity(getenv("FAKE_EVDEV_IDENTITY"));
    pthread_atfork(controller_fork_prepare, controller_fork_parent,
                   controller_fork_child);
    Logger::init();
    if (Logger::log_enabled)
      hermes_set_logger(hermes_log_line);
    hermes_init();
    return c;
  }();
  return *cfg;
}

static std::recursive_mutex &controller_mutex() {
  static auto *mutex = new std::recursive_mutex();
  return *mutex;
}

static std::unordered_map<int, std::shared_ptr<FakeController>> &controller_map() {
  static auto *map = new std::unordered_map<int, std::shared_ptr<FakeController>>();
  return *map;
}

static std::unordered_map<int, struct ff_effect> &ff_effects() {
  static auto *map = new std::unordered_map<int, struct ff_effect>();
  return *map;
}

static const char *fake_hook_dir() { return config().hook_dir; }
static const char *fake_udev_data_dir() { return config().udev_data_dir; }
static bool fake_vibration_enabled() { return config().vibration_enabled; }
static const PadIdentity *fake_identity() { return config().identity; }

static int next_ff_id = 0;

// fork() carries over only the calling thread, so a lock another thread was holding at that
// instant stays held in the child by a thread that is not there to release it. Every hook below
// takes controller_mutex, and the Steam client forks while its other threads are inside them, so
// the child blocked on its first open() and never reached exec: the spawn never completed, the
// client's main loop stalled past its own watchdog and it tore the session down.
//
// Taking the lock before the fork is what makes the copy consistent - no thread is part way
// through the tables it guards. The parent then unlocks it. The child cannot: a recursive mutex
// records the owning thread, the child's one thread has a new id, and unlocking one it does not
// own is refused, which would leave the lock held for good. It gets a fresh mutex instead, which
// is sound precisely because the fork was taken with the lock held.
static void controller_fork_prepare() { controller_mutex().lock(); }
static void controller_fork_parent() { controller_mutex().unlock(); }

static void controller_fork_child() {
  pthread_mutexattr_t attr;

  pthread_mutexattr_init(&attr);
  pthread_mutexattr_settype(&attr, PTHREAD_MUTEX_RECURSIVE);
  pthread_mutex_init(controller_mutex().native_handle(), &attr);
  pthread_mutexattr_destroy(&attr);
}

namespace Logger {
int log_enabled;

void init() {
  log_enabled = getenv("FAKE_EVDEV_LOG") && atoi(getenv("FAKE_EVDEV_LOG"));
}

void log(const char *message, ...) {
  if (!log_enabled)
    return;

  va_list args;
  va_start(args, message);
  vfprintf(stderr, message, args);
  va_end(args);

  std::cerr.flush();
}
} // namespace Logger

__attribute__((constructor)) static void library_init() {
  // Warming the lazily built state, not owning it: when the loader runs this before anything
  // calls a hook - the ordinary case - the first hook finds everything already there. Nothing
  // below depends on it having run, which is the whole point.
  config();
  controller_mutex();
  controller_map();
}

__attribute__((visibility("hidden"))) static void
send_vibration(int strong, int weak, uint16_t duration_ms, uint16_t slot) {
  if (!fake_vibration_enabled())
    return;

  // Rumble is best effort. A full Android listener backlog must never park
  // winebus (or every input hook through controller_mutex()), and a closing
  // listener must not terminate the guest with SIGPIPE.
  int sock = socket(AF_UNIX, SOCK_STREAM | SOCK_NONBLOCK | SOCK_CLOEXEC, 0);
  if (sock < 0)
    return;

  struct sockaddr_un addr = {};
  addr.sun_family = AF_UNIX;
  const char *name = "winlator_vibration";
  memcpy(addr.sun_path + 1, name, strlen(name));
  socklen_t addrlen = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name);

  if (connect(sock, reinterpret_cast<struct sockaddr *>(&addr), addrlen) < 0) {
    syscall(SYS_close, sock);
    return;
  }

  uint16_t data[4];
  data[0] = static_cast<uint16_t>(strong);
  data[1] = static_cast<uint16_t>(weak);
  data[2] = duration_ms;
  data[3] = slot;
  send(sock, data, sizeof(data), MSG_DONTWAIT | MSG_NOSIGNAL);
  syscall(SYS_close, sock);
}

__attribute__((visibility("hidden"))) static void
check_ff_event(const struct input_event *ev, uint16_t slot) {
  if (ev->type != EV_FF)
    return;

  int id = ev->code;
  if (ev->value > 0) {
    auto it = ff_effects().find(id);
    if (it == ff_effects().end())
      return;

    uint16_t duration = it->second.replay.length;
    if (it->second.type == FF_RUMBLE) {
      send_vibration(it->second.u.rumble.strong_magnitude,
                     it->second.u.rumble.weak_magnitude, duration, slot);
    } else if (it->second.type == FF_PERIODIC) {
      send_vibration(it->second.u.periodic.magnitude,
                     it->second.u.periodic.magnitude, duration, slot);
    }
  } else {
    send_vibration(0, 0, 0, slot);
  }
}

__attribute__((visibility("hidden"))) char *
from_real_to_fake_path(const char *pathname) {
  const char *event = strrchr(pathname, '/') + 1;
  char *fake_path = nullptr;
  if (asprintf(&fake_path, "%s/%s", fake_hook_dir(), event) < 0)
    fake_path = nullptr;
  return fake_path;
}

__attribute__((visibility("hidden"))) static bool path_exists(const char *path) {
  return path && faccessat(AT_FDCWD, path, F_OK, 0) == 0;
}

__attribute__((visibility("hidden"))) static bool
is_fake_input_node_path(const char *pathname) {
  return pathname && (!strncmp(pathname, "/dev/input/event", 16) ||
                      !strncmp(pathname, "/dev/input/js", 13));
}

__attribute__((visibility("hidden"))) static bool
is_fake_udev_data_path(const char *pathname) {
  return pathname && fake_udev_data_dir() && *fake_udev_data_dir() &&
         !strncmp(pathname, "/run/udev/data/c13:", 19);
}

__attribute__((visibility("hidden"))) char *
from_real_to_fake_udev_data_path(const char *pathname) {
  const char *name = strrchr(pathname, '/') + 1;
  char *fake_path = nullptr;
  if (asprintf(&fake_path, "%s/%s", fake_udev_data_dir(), name) < 0)
    fake_path = nullptr;
  return fake_path;
}

__attribute__((visibility("hidden"))) const char *
get_event(const char *pathname) {
  const char *event = strrchr(pathname, '/') + 1;
  return event;
}

__attribute__((visibility("hidden"))) int get_event_number(const char *event) {
  if (!event)
    return -1;

  const char *digits = event;
  while (*digits && (*digits < '0' || *digits > '9'))
    digits++;

  return *digits ? atoi(digits) : -1;
}

__attribute__((visibility("hidden"))) static dev_t
get_fake_input_rdev(const char *event) {
  int event_number = get_event_number(event);
  if (event_number < 0)
    return makedev(FAKE_INPUT_MAJOR, 0);

  if (!strncmp(event, "event", 5))
    return makedev(FAKE_INPUT_MAJOR, FAKE_INPUT_EVENT_MINOR_BASE + event_number);
  if (!strncmp(event, "js", 2))
    return makedev(FAKE_INPUT_MAJOR, FAKE_INPUT_JS_MINOR_BASE + event_number);

  return makedev(FAKE_INPUT_MAJOR, event_number);
}

// Attach a guest open() of event<N>/js<N> to pad slot N. The fd returned is hermes's; its first
// read is the slot's full current state.
__attribute__((visibility("hidden"))) static int
attach_fake_input(const char *event, int flags) {
  int slot = get_event_number(event);
  hermes_handle handle = 0;
  int fd = hermes_attach(slot, flags, &handle);
  if (fd < 0) {
    Logger::log("Could not attach %s (slot %d): %s\n", event, slot,
                strerror(-fd));
    errno = -fd;
    return -1;
  }

  auto controller = std::make_shared<FakeController>();
  controller->event = strdup(event);
  controller->slot = slot;
  controller->handle = handle;
  {
    std::lock_guard<std::recursive_mutex> guard(controller_mutex());
    // A leftover entry means the fd number was closed behind this library's back and reused.
    // That handle can no longer be detached (it would close the new fd); only the entry goes.
    controller_map()[fd] = controller;
  }

  Logger::log("Adding controller, fd %d event %s slot %d\n", fd, event, slot);
  return fd;
}

// Under the Steam client a pad it manages is hidden from the game: the client lists it in
// SDL_GAMECONTROLLER_IGNORE_DEVICES, its overlay refuses the game's open() of the node, and the
// game is meant to see Steam Input's virtual gamepad instead. That one is a uinput device, which
// does not exist here, so the game was left with no controller at all. With
// FAKE_EVDEV_STEAM_VIRTUAL set the pad carries the virtual gamepad's identity for every process
// but the client's own, which must keep seeing the pad as the physical one it reads.
__attribute__((visibility("hidden"))) static bool presents_steam_virtual() {
  static int known;
  if (known == 0) {
    const char *enabled = getenv("FAKE_EVDEV_STEAM_VIRTUAL");
    char exe[PATH_MAX];
    ssize_t length = enabled && atoi(enabled) ? readlink("/proc/self/exe", exe, sizeof(exe) - 1) : -1;
    bool client = false;
    if (length > 0) {
      exe[length] = '\0';
      const char *name = strrchr(exe, '/');
      client = !strncmp(name ? name + 1 : exe, "steam", 5);
    }
    known = length > 0 && !client ? 1 : -1;
  }
  return known == 1;
}

// The virtual gamepad is an X-Box 360 pad, whose triggers are ABS_Z and ABS_RZ. Wine places a
// gamepad's axes by their position among the ones it advertises, so the triggers have to sit
// where that pad has them: as ABS_GAS and ABS_BRAKE they follow the sticks, the right stick
// lands on the left trigger, and a released right trigger reads as the right stick held up.
__attribute__((visibility("hidden"))) static uint16_t presented_abs_code(uint16_t code) {
  if (!presents_steam_virtual()) return code;
  if (code == ABS_BRAKE) return ABS_Z;
  if (code == ABS_GAS) return ABS_RZ;
  return code;
}

__attribute__((visibility("hidden"))) static uint16_t canonical_abs_code(uint16_t code) {
  if (!presents_steam_virtual()) return code;
  if (code == ABS_Z) return ABS_BRAKE;
  if (code == ABS_RZ) return ABS_GAS;
  return code;
}

__attribute__((visibility("hidden"))) static void
copy_slot_ioctl_string(int op, void *argp, const char *format, int event_number) {
  size_t size = _IOC_SIZE(op);
  if (!argp || size == 0)
    return;

  snprintf(static_cast<char *>(argp), size, format, event_number);
}

// write/writev on an unplugged pad fail with ENODEV, as evdev does.
__attribute__((visibility("hidden"))) static bool
fake_fd_write_fails(hermes_handle handle) {
  int removed = hermes_is_removed(handle);
  if (removed == 0)
    return false;
  errno = removed > 0 ? ENODEV : -removed;
  return true;
}

EXPORT int open(const char *pathname, int flags, ...) {
  va_list va;
  mode_t mode;
  int fd;
  bool hasMode;

  va_start(va, flags);

  hasMode = flags & O_CREAT;

  if (hasMode) {
    mode = va_arg(va, mode_t);
  }

  va_end(va);

  static auto my_open = reinterpret_cast<int (*)(const char *, int, ...)>(dlsym(RTLD_NEXT, "open"));

  char *fake_path = nullptr;
  const char *event = nullptr;
  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      event = get_event(pathname);
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      if (path_exists(fake_path)) {
        fd = attach_fake_input(event, flags);
        if (fd >= 0) {
          free(fake_path);
          return fd;
        }
        int saved_errno = errno;
        free(fake_path);
        errno = saved_errno;
        return -1;
      }
      pathname = fake_path;
    } else if (is_fake_udev_data_path(pathname)) {
      fake_path = from_real_to_fake_udev_data_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  if (hasMode)
    fd = my_open(pathname, flags, mode);
  else
    fd = my_open(pathname, flags);

  if (fake_path)
    free(fake_path);

  return fd;
}

EXPORT int openat(int dirfd, const char *pathname, int flags, ...) {
  va_list va;
  mode_t mode;
  int fd;
  bool hasMode;

  va_start(va, flags);

  hasMode = flags & O_CREAT;

  if (hasMode) {
    mode = va_arg(va, mode_t);
  }

  va_end(va);

  static auto my_openat = reinterpret_cast<int (*)(int, const char *, int, ...)>(dlsym(RTLD_NEXT, "openat"));

  char *fake_path = nullptr;
  const char *event = nullptr;
  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      event = get_event(pathname);
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      if (path_exists(fake_path)) {
        fd = attach_fake_input(event, flags);
        if (fd >= 0) {
          free(fake_path);
          return fd;
        }
        int saved_errno = errno;
        free(fake_path);
        errno = saved_errno;
        return -1;
      }
      pathname = fake_path;
    } else if (is_fake_udev_data_path(pathname)) {
      fake_path = from_real_to_fake_udev_data_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  if (hasMode)
    fd = my_openat(dirfd, pathname, flags, mode);
  else
    fd = my_openat(dirfd, pathname, flags);

  if (fake_path)
    free(fake_path);

  return fd;
}

EXPORT int stat(const char *pathname, struct stat *statbuf) {
  static auto my_stat = reinterpret_cast<decltype(&::stat)>(dlsym(RTLD_NEXT, "stat"));

  const char *event = nullptr;
  char *fake_path = nullptr;

  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      event = get_event(pathname);
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (is_fake_udev_data_path(pathname)) {
      fake_path = from_real_to_fake_udev_data_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  int ret = my_stat(pathname, statbuf);

  if (ret == 0 && event && get_event_number(event) >= 0) {
    statbuf->st_mode = (statbuf->st_mode & ~S_IFMT) | S_IFCHR;
    statbuf->st_rdev = get_fake_input_rdev(event);
  }

  if (fake_path)
    free(fake_path);

  return ret;
}

EXPORT int fstat(int fd, struct stat *buf) {
  static auto my_fstat = reinterpret_cast<decltype(&::fstat)>(dlsym(RTLD_NEXT, "fstat"));

  int ret = my_fstat(fd, buf);

  std::lock_guard<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (ret == 0 && controller != controller_map().end()) {
    buf->st_mode = (buf->st_mode & ~S_IFMT) | S_IFCHR;
    buf->st_rdev = get_fake_input_rdev(controller->second->event);
  }

  return ret;
}

EXPORT int access(const char *pathname, int mode) {
  static auto my_access = reinterpret_cast<decltype(&::access)>(dlsym(RTLD_NEXT, "access"));

  char *fake_path = nullptr;
  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (is_fake_udev_data_path(pathname)) {
      fake_path = from_real_to_fake_udev_data_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  int ret = my_access(pathname, mode);
  if (fake_path)
    free(fake_path);
  return ret;
}

EXPORT int faccessat(int dirfd, const char *pathname, int mode, int flags) {
  static auto my_faccessat = reinterpret_cast<decltype(&::faccessat)>(dlsym(RTLD_NEXT, "faccessat"));

  char *fake_path = nullptr;
  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (is_fake_udev_data_path(pathname)) {
      fake_path = from_real_to_fake_udev_data_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  int ret = my_faccessat(dirfd, pathname, mode, flags);
  if (fake_path)
    free(fake_path);
  return ret;
}

EXPORT int scandir(const char *dirp, struct dirent ***namelist,
                   int (*filter)(const struct dirent *),
                   int (*compar)(const struct dirent **,
                                 const struct dirent **)) {
  static auto my_scandir = reinterpret_cast<decltype(&::scandir)>(dlsym(RTLD_NEXT, "scandir"));

  if (dirp) {
    if (!strcmp(dirp, "/dev/input")) {
      dirp = fake_hook_dir();
    } else if (fake_udev_data_dir() && !strcmp(dirp, "/run/udev/data")) {
      dirp = fake_udev_data_dir();
    }
  }

  return my_scandir(dirp, namelist, filter, compar);
}

EXPORT int inotify_add_watch(int fd, const char *pathname, uint32_t mask) {
  static auto my_inotify_add_watch = reinterpret_cast<decltype(&::inotify_add_watch)>(dlsym(RTLD_NEXT, "inotify_add_watch"));

  char *fake_path = nullptr;
  if (pathname) {
    if (is_fake_input_node_path(pathname)) {
      fake_path = from_real_to_fake_path(pathname);
      if (!fake_path) {
        errno = ENOMEM;
        return -1;
      }
      pathname = fake_path;
    } else if (!strcmp(pathname, "/dev/input")) {
      pathname = fake_hook_dir();
    }
  }

  int ret = my_inotify_add_watch(fd, pathname, mask);
  if (fake_path)
    free(fake_path);
  return ret;
}

template <size_t N>
static int copy_ioctl_bits(int op, void *destination, const unsigned char (&bits)[N]) {
  size_t size = std::min<size_t>(_IOC_SIZE(op), N);
  if (size) memcpy(destination, bits, size);
  return static_cast<int>(size);
}

EXPORT int ioctl(int fd, ioctl_request_t op, ...) {
  va_list va;
  void *argp;

  va_start(va, op);
  argp = va_arg(va, void *);
  va_end(va);

  // The lock must not be held across the passthrough: binder's transport is a
  // blocking ioctl(BINDER_WRITE_READ), so a parked binder pool thread would own
  // controller_mutex() for as long as it waits and deadlock every other ioctl.
  std::unique_lock<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (controller == controller_map().end()) {
    guard.unlock();
    return syscall(SYS_ioctl, fd, op, argp);
  }

  int type = (op >> 8 & 0xFF);
  int number = (op >> 0 & 0xFF);
  const char *event = controller->second->event ? controller->second->event : "event0";
  int event_number = controller->second->slot;

  if (type == 0x45 && number == 0x1) {
    Logger::log("Hooking ioctl EVIOCGVERSION for event %s\n", event);
    int version = 65536;
    memcpy(argp, (void *)&version, sizeof(int));
    return 0;
  } else if (type == 0x45 && number == 0x2) {
    Logger::log("Hooking ioctl EVIOCGID for event %s\n", event);
    struct input_id id;
    memset(&id, 0, sizeof(id));
    id.bustype = 0x03;
    struct hermes_identity live;
    if (presents_steam_virtual()) {
      id.vendor = STEAM_VIRTUAL_VENDOR_ID;
      id.product = STEAM_VIRTUAL_PRODUCT_ID;
    } else if (live_pad_identity(controller->second->handle, &live)) {
      id.bustype = live.bustype;
      id.vendor = live.vendor;
      id.product = live.product;
    } else if (const PadIdentity *identity = fake_identity()) {
      id.vendor = identity->vendor;
      id.product = identity->product;
    } else {
      id.vendor = static_cast<uint16_t>(GAMEPAD_VENDOR_ID_BASE + event_number);
      id.product = static_cast<uint16_t>(GAMEPAD_PRODUCT_ID_BASE + event_number);
    }
    id.version = GAMEPAD_VERSION;
    memcpy(argp, (void *)&id, sizeof(id));
    return 0;
  } else if (type == 0x45 && number == 0x6) {
    Logger::log("Hooking ioctl EVIOCGNAME for event %s\n", event);
    struct hermes_identity live;
    if (!presents_steam_virtual() && live_pad_identity(controller->second->handle, &live)) {
      copy_live_name(op, argp, live);
      return 0;
    }
    copy_slot_ioctl_string(op, argp,
                           presents_steam_virtual() ? STEAM_VIRTUAL_NAME_TEMPLATE
                           : (fake_identity() ? fake_identity()->name_template : GAMEPAD_NAME_TEMPLATE),
                           event_number);
    return 0;
  } else if (type == 0x45 && number == 0x7) {
    Logger::log("Hooking ioctl EVIOCGPHYS for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_PHYS_TEMPLATE, event_number);
    return 0;
  } else if (type == 0x45 && number == 0x8) {
    Logger::log("Hooking ioctl EVIOCGUNIQ for event %s\n", event);
    copy_slot_ioctl_string(op, argp, GAMEPAD_UNIQ_TEMPLATE, event_number);
    return 0;
  } else if (type == 0x45 && number == 0x9) {
    unsigned char bitmask[(INPUT_PROP_MAX + 8) / 8] = {};
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x18) {
    // The buttons held right now. Asked without the lock: the query may wait briefly for a
    // publish in progress, and other threads' hooks must not wait with it.
    hermes_handle handle = controller->second->handle;
    guard.unlock();
    struct hermes_state state;
    int error = hermes_current_state(handle, &state);
    if (error < 0) {
      errno = -error;
      return -1;
    }
    unsigned char bitmask[(KEY_MAX + 8) / 8] = {};
    for (int i = 0; i < HERMES_BUTTONS; ++i) {
      if (state.buttons[i])
        bitmask[kPadButtonCodes[i] / 8] |= 1u << (kPadButtonCodes[i] % 8);
    }
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x20) {
    Logger::log("Hooking ioctl EVIOCGBIT(0, len) for event %s\n", event);
    unsigned char bitmask[(EV_MAX + 8) / 8] = {};
    bitmask[EV_SYN / 8] |= (1 << (EV_SYN % 8));
    bitmask[EV_KEY / 8] |= (1 << (EV_KEY % 8));
    bitmask[EV_ABS / 8] |= (1 << (EV_ABS % 8));
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x21) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_KEY, len) for event %s\n", event);
    unsigned char bitmask[(KEY_MAX + 8) / 8] = {};
    const int xbox_buttons[] = {BTN_A,    BTN_B,      BTN_X,      BTN_Y,
                                BTN_TL,   BTN_TR,     BTN_SELECT, BTN_START,
                                BTN_MODE, BTN_THUMBL, BTN_THUMBR};
    for (int button : xbox_buttons)
      bitmask[button / 8] |= (1 << (button % 8));
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x22) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_REL, len) for event %s\n", event);
    unsigned char bitmask[(REL_MAX + 8) / 8] = {};
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x23) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_ABS, len) for event %s\n", event);
    unsigned char bitmask[(ABS_MAX + 8) / 8] = {};
    bitmask[ABS_X / 8] |= (1 << (ABS_X % 8));
    bitmask[ABS_Y / 8] |= (1 << (ABS_Y % 8));
    bitmask[ABS_RX / 8] |= (1 << (ABS_RX % 8));
    bitmask[ABS_RY / 8] |= (1 << (ABS_RY % 8));
    for (uint16_t trigger : {ABS_GAS, ABS_BRAKE}) {
      uint16_t code = presented_abs_code(trigger);
      bitmask[code / 8] |= (1 << (code % 8));
    }
    bitmask[ABS_HAT0X / 8] |= (1 << (ABS_HAT0X % 8));
    bitmask[ABS_HAT0Y / 8] |= (1 << (ABS_HAT0Y % 8));
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x35) {
    Logger::log("Hooking ioctl EVIOCGBIT(EV_FF, len) for event %s\n", event);
    unsigned char bitmask[(FF_MAX + 8) / 8] = {};
    bitmask[FF_RUMBLE / 8] |= (1 << (FF_RUMBLE % 8));
    bitmask[FF_PERIODIC / 8] |= (1 << (FF_PERIODIC % 8));
    return copy_ioctl_bits(op, argp, bitmask);
  } else if (type == 0x45 && number == 0x80) {
    struct ff_effect *effect = static_cast<struct ff_effect *>(argp);
    if (effect->id == -1)
      effect->id = next_ff_id++;
    ff_effects()[effect->id] = *effect;

    uint16_t duration = effect->replay.length;
    uint16_t slot = static_cast<uint16_t>(event_number);
    if (effect->type == FF_RUMBLE) {
      send_vibration(effect->u.rumble.strong_magnitude,
                     effect->u.rumble.weak_magnitude, duration, slot);
    } else if (effect->type == FF_PERIODIC) {
      send_vibration(effect->u.periodic.magnitude, effect->u.periodic.magnitude,
                     duration, slot);
    }
    return 0;
  } else if (type == 0x45 && number == 0x81) {
    int id = (intptr_t)argp;
    ff_effects().erase(id);
    return 0;
  } else if (type == 0x45 && number == 0x84) {
    int max_effects = 16;
    memcpy(argp, &max_effects, sizeof(int));
    return 0;
  } else if (type == 0x45 && number >= 0x40 && number <= 0x51) {
    Logger::log("Hooking ioctl EVIOCGABS(ABS) for event %s\n", event);
    struct input_absinfo abs_info;
    memset(&abs_info, 0, sizeof(abs_info));
    uint16_t code = canonical_abs_code(number - 0x40);
    if (code == ABS_GAS || code == ABS_BRAKE) {
      abs_info.value = 0;
      abs_info.minimum = 0;
      abs_info.maximum = 255;
    } else if (code <= ABS_RY) {
      abs_info.value = 0;
      abs_info.minimum = -32768;
      abs_info.maximum = 32767;
    } else if (code == ABS_HAT0X || code == ABS_HAT0Y) {
      abs_info.value = 0;
      abs_info.minimum = -1;
      abs_info.maximum = 1;
    }
    // The axis's current value, asked without the lock (see EVIOCGKEY).
    hermes_handle handle = controller->second->handle;
    guard.unlock();
    struct hermes_state state;
    int error = hermes_current_state(handle, &state);
    if (error < 0) {
      errno = -error;
      return -1;
    }
    for (int i = 0; i < HERMES_AXES; ++i)
      if (kPadAxisCodes[i] == code) abs_info.value = state.axes[i];
    memcpy(argp, &abs_info, std::min<size_t>(_IOC_SIZE(op), sizeof(abs_info)));
    return 0;
  } else if (type == 0x45 && number == 0x90) {
    Logger::log("Hooking ioctl EVIOCGRAB for event %s\n", event);
    return 0;
  } else if (type == 0x6A && number == 0x1) {
    Logger::log("Hooking ioctl JSIOCGVERSION for event %s\n", event);
    int version = JS_VERSION;
    memcpy(argp, (void *)&version, sizeof(version));
    return 0;
  } else if (type == 0x6A && number == 0x11) {
    Logger::log("Hooking ioctl JSIOCGAXES for event %s\n", event);
    uint8_t axes = GAMEPAD_AXIS_COUNT;
    memcpy(argp, (void *)&axes, sizeof(axes));
    return 0;
  } else if (type == 0x6A && number == 0x12) {
    Logger::log("Hooking ioctl JSIOCGBUTTONS for event %s\n", event);
    uint8_t buttons = GAMEPAD_BUTTON_COUNT;
    memcpy(argp, (void *)&buttons, sizeof(buttons));
    return 0;
  } else if (type == 0x6A && number == 0x13) {
    Logger::log("Hooking ioctl JSIOCGNAME(len) for event %s\n", event);
    struct hermes_identity live;
    if (!presents_steam_virtual() && live_pad_identity(controller->second->handle, &live)) {
      copy_live_name(op, argp, live);
      return 0;
    }
    copy_slot_ioctl_string(op, argp,
                           presents_steam_virtual() ? STEAM_VIRTUAL_NAME_TEMPLATE
                           : (fake_identity() ? fake_identity()->name_template : GAMEPAD_NAME_TEMPLATE),
                           event_number);
    return 0;
  } else {
    Logger::log("Unhandled evdev ioctl, type %d number %d\n", type, number);
    guard.unlock();
    return syscall(SYS_ioctl, fd, op, argp);
  }
}

EXPORT int close(int fd) {
  static auto my_close = reinterpret_cast<decltype(&::close)>(dlsym(RTLD_NEXT, "close"));

  std::unique_lock<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (controller != controller_map().end()) {
    Logger::log("Removing controller, fd %d event %s\n", controller->first,
                controller->second->event ? controller->second->event : "(unknown)");
    hermes_handle handle = controller->second->handle;
    controller_map().erase(controller);
    guard.unlock();
    // hermes owns the fd: detaching releases the handle and closes it. A read blocked on it in
    // another thread fails with EBADF.
    if (hermes_detach(handle) == 0)
      return 0;
  } else {
    guard.unlock();
  }

  return my_close(fd);
}

EXPORT ssize_t read(int fd, void *buf, size_t count) {
  std::unique_lock<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (controller == controller_map().end()) {
    guard.unlock();
    return syscall(SYS_read, fd, buf, count);
  }
  hermes_handle handle = controller->second->handle;
  // A blocking read sleeps inside hermes, so the lock must not be held across it.
  guard.unlock();
  if (count < HERMES_EVENT_SIZE) {
    errno = EINVAL;
    return -1;
  }
  // O_NONBLOCK as the fd has it now: the program may have changed it since open().
  int flags = fcntl(fd, F_GETFL);
  bool nonblock = flags >= 0 && (flags & O_NONBLOCK);
  ssize_t n = hermes_read(handle, buf, count, nonblock ? 1 : 0);
  if (n < 0) {
    errno = static_cast<int>(-n);
    return -1;
  }
  // hermes delivers the canonical trigger codes; the Steam-virtual identity presents them
  // where an X-Box 360 pad has them.
  if (presents_steam_virtual()) {
    uint8_t *events = static_cast<uint8_t *>(buf);
    for (ssize_t off = 0; off + HERMES_EVENT_SIZE <= n; off += HERMES_EVENT_SIZE) {
      struct input_event ev;
      memcpy(&ev, events + off, sizeof(ev));
      if (ev.type != EV_ABS)
        continue;
      uint16_t code = presented_abs_code(ev.code);
      if (code == ev.code)
        continue;
      ev.code = code;
      memcpy(events + off, &ev, sizeof(ev));
    }
  }
  return n;
}

EXPORT ssize_t write(int fd, const void *buf, size_t count) {
  static auto my_write = reinterpret_cast<decltype(&::write)>(dlsym(RTLD_NEXT, "write"));

  std::unique_lock<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (controller != controller_map().end()) {
    if (fake_fd_write_fails(controller->second->handle))
      return -1;

    const struct input_event *ev = nullptr;
    uint16_t slot = static_cast<uint16_t>(controller->second->slot);
    if (count == sizeof(struct input_event)) {
      ev = static_cast<const struct input_event *>(buf);
      check_ff_event(ev, slot);
    }

    return static_cast<ssize_t>(count);
  }
  guard.unlock();
  return my_write(fd, buf, count);
}

EXPORT ssize_t writev(int fd, const struct iovec *iov, int iovcnt) {
  std::unique_lock<std::recursive_mutex> guard(controller_mutex());
  auto controller = controller_map().find(fd);
  if (controller != controller_map().end()) {
    if (fake_fd_write_fails(controller->second->handle))
      return -1;

    uint16_t slot = static_cast<uint16_t>(controller->second->slot);
    ssize_t total = 0;
    for (int i = 0; i < iovcnt; i++) {
      if (iov[i].iov_len == sizeof(struct input_event)) {
        const struct input_event *ev =
            static_cast<const struct input_event *>(iov[i].iov_base);
        check_ff_event(ev, slot);
      }
      total += static_cast<ssize_t>(iov[i].iov_len);
    }
    return total;
  }
  guard.unlock();
  return syscall(SYS_writev, fd, iov, iovcnt);
}

// Pad fds are real kernel fds that hermes keeps readable while input is pending and hung up
// once the pad is unplugged, so poll, ppoll and select need no emulation: they go straight to
// libc, which keeps the caller's timeout and applies ppoll's signal mask atomically with the wait.
EXPORT int poll(struct pollfd *fds, nfds_t nfds, int timeout) {
  static auto my_poll = reinterpret_cast<decltype(&::poll)>(dlsym(RTLD_NEXT, "poll"));
  return my_poll(fds, nfds, timeout);
}

EXPORT int ppoll(struct pollfd *fds, nfds_t nfds,
                 const struct timespec *timeout, const sigset_t *sigmask) {
  static auto my_ppoll = reinterpret_cast<decltype(&::ppoll)>(dlsym(RTLD_NEXT, "ppoll"));
  return my_ppoll(fds, nfds, timeout, sigmask);
}

EXPORT int select(int nfds, fd_set *readfds, fd_set *writefds,
                  fd_set *exceptfds, struct timeval *timeout) {
  static auto my_select = reinterpret_cast<decltype(&::select)>(dlsym(RTLD_NEXT, "select"));
  return my_select(nfds, readfds, writefds, exceptfds, timeout);
}

#ifdef __GLIBC__
// Steam's binaries were linked against an older glibc and reach the calls above through these
// names instead.
static mode_t open_mode(int flags, va_list va) {
  return (flags & (O_CREAT | O_TMPFILE)) ? va_arg(va, mode_t) : 0;
}

EXPORT int open64(const char *pathname, int flags, ...) {
  va_list va;
  va_start(va, flags);
  mode_t mode = open_mode(flags, va);
  va_end(va);
  return open(pathname, flags, mode);
}

EXPORT int openat64(int dirfd, const char *pathname, int flags, ...) {
  va_list va;
  va_start(va, flags);
  mode_t mode = open_mode(flags, va);
  va_end(va);
  return openat(dirfd, pathname, flags, mode);
}

EXPORT int stat64(const char *pathname, struct stat64 *statbuf) {
  return stat(pathname, reinterpret_cast<struct stat *>(statbuf));
}

EXPORT int fstat64(int fd, struct stat64 *buf) {
  return fstat(fd, reinterpret_cast<struct stat *>(buf));
}

EXPORT int __xstat(int version, const char *pathname, struct stat *statbuf) {
  (void)version;
  return stat(pathname, statbuf);
}

EXPORT int __xstat64(int version, const char *pathname, struct stat64 *statbuf) {
  (void)version;
  return stat(pathname, reinterpret_cast<struct stat *>(statbuf));
}

EXPORT int __fxstat(int version, int fd, struct stat *buf) {
  (void)version;
  return fstat(fd, buf);
}

EXPORT int __fxstat64(int version, int fd, struct stat64 *buf) {
  (void)version;
  return fstat(fd, reinterpret_cast<struct stat *>(buf));
}
#endif
