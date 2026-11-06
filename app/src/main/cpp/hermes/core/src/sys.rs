//! Raw Linux system calls (aarch64 and x86_64) plus the few libc entry points the transport uses.
//!
//! File-descriptor operations go straight to the kernel with `svc`/`syscall`, never through
//! libc's `read`/`write`/`close`/`open`/`fstat`/`poll`...: the guest library lives inside an
//! LD_PRELOAD interposer that exports those very symbols, and a call through the PLT would land
//! back in the interposer's hooks. The libc functions used here (getenv, clock_gettime, the
//! pthread calls, sigfillset, abort) are not hooked by it. Errors come back as positive errno.

use core::ffi::{c_char, c_int, c_void};
use core::sync::atomic::AtomicU32;

#[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
compile_error!("hermes supports aarch64 and x86_64 (host tests) only");

#[cfg(target_arch = "aarch64")]
mod nr {
    pub const DUP3: usize = 24;
    pub const FCNTL: usize = 25;
    pub const IOCTL: usize = 29;
    pub const FLOCK: usize = 32;
    pub const MKDIRAT: usize = 34;
    pub const UNLINKAT: usize = 35;
    pub const RENAMEAT: usize = 38;
    pub const FTRUNCATE: usize = 46;
    pub const OPENAT: usize = 56;
    pub const CLOSE: usize = 57;
    pub const READ: usize = 63;
    pub const WRITE: usize = 64;
    pub const PPOLL: usize = 73;
    pub const NEWFSTATAT: usize = 79;
    pub const FSTAT: usize = 80;
    pub const FUTEX: usize = 98;
    pub const SCHED_YIELD: usize = 124;
    pub const PRCTL: usize = 167;
    pub const GETPID: usize = 172;
    pub const SOCKET: usize = 198;
    pub const SOCKETPAIR: usize = 199;
    pub const SENDTO: usize = 206;
    pub const RECVFROM: usize = 207;
    pub const MUNMAP: usize = 215;
    pub const MMAP: usize = 222;
}

#[cfg(target_arch = "x86_64")]
mod nr {
    pub const READ: usize = 0;
    pub const WRITE: usize = 1;
    pub const CLOSE: usize = 3;
    pub const FSTAT: usize = 5;
    pub const MMAP: usize = 9;
    pub const MUNMAP: usize = 11;
    pub const IOCTL: usize = 16;
    pub const SCHED_YIELD: usize = 24;
    pub const GETPID: usize = 39;
    pub const SOCKET: usize = 41;
    pub const SENDTO: usize = 44;
    pub const RECVFROM: usize = 45;
    pub const SOCKETPAIR: usize = 53;
    pub const FCNTL: usize = 72;
    pub const FLOCK: usize = 73;
    pub const FTRUNCATE: usize = 77;
    pub const PRCTL: usize = 157;
    pub const FUTEX: usize = 202;
    pub const OPENAT: usize = 257;
    pub const MKDIRAT: usize = 258;
    pub const NEWFSTATAT: usize = 262;
    pub const UNLINKAT: usize = 263;
    pub const RENAMEAT: usize = 264;
    pub const PPOLL: usize = 271;
    pub const DUP3: usize = 292;
}

pub const EPERM: i32 = 1;
pub const ENOENT: i32 = 2;
pub const EINTR: i32 = 4;
pub const EBADF: i32 = 9;
pub const EAGAIN: i32 = 11;
pub const ENOMEM: i32 = 12;
pub const EACCES: i32 = 13;
pub const EFAULT: i32 = 14;
pub const EEXIST: i32 = 17;
pub const ENODEV: i32 = 19;
pub const EINVAL: i32 = 22;
pub const EMFILE: i32 = 24;
pub const ENOSPC: i32 = 28;
pub const ENAMETOOLONG: i32 = 36;
pub const ENODATA: i32 = 61;
pub const ETIMEDOUT: i32 = 110;

pub const O_RDONLY: i32 = 0;
pub const O_RDWR: i32 = 2;
pub const O_CREAT: i32 = 0o100;
pub const O_EXCL: i32 = 0o200;
pub const O_NONBLOCK: i32 = 0o4000;
pub const O_CLOEXEC: i32 = 0o2000000;

const AT_FDCWD: isize = -100;
pub const PROT_READ: usize = 1;
pub const PROT_WRITE: usize = 2;
pub const MAP_SHARED: usize = 1;
pub const MAP_ANONYMOUS: usize = 0x20;

pub const F_GETFD: usize = 1;
pub const F_SETFD: usize = 2;
pub const F_GETFL: usize = 3;
pub const F_SETFL: usize = 4;
pub const F_DUPFD_CLOEXEC: usize = 1030;
pub const FD_CLOEXEC: usize = 1;

pub const LOCK_SH: usize = 1;
pub const LOCK_EX: usize = 2;
pub const LOCK_NB: usize = 4;
pub const LOCK_UN: usize = 8;

const FUTEX_WAIT: usize = 0;
const FUTEX_WAKE: usize = 1;
const FUTEX_PRIVATE_FLAG: usize = 128;

const AF_UNIX: usize = 1;
const AF_INET: usize = 2;
const SOCK_STREAM: usize = 1;
const SOCK_DGRAM: usize = 2;
const SOCK_NONBLOCK: usize = 0o4000;
const SOCK_CLOEXEC: usize = 0o2000000;
const MSG_DONTWAIT: usize = 0x40;
const MSG_NOSIGNAL: usize = 0x4000;
const FIONREAD: usize = 0x541b;
const PR_SET_NAME: usize = 15;

pub const POLLIN: i16 = 0x001;
pub const POLLOUT: i16 = 0x004;
pub const POLLERR: i16 = 0x008;
pub const POLLHUP: i16 = 0x010;
pub const POLLNVAL: i16 = 0x020;
pub const POLLRDNORM: i16 = 0x040;
pub const POLLWRNORM: i16 = 0x100;

const S_IFMT: u32 = 0o170000;
const S_IFREG: u32 = 0o100000;

const CLOCK_REALTIME: c_int = 0;
const CLOCK_MONOTONIC: c_int = 1;
const SIG_SETMASK: c_int = 2;
const PTHREAD_CREATE_DETACHED: c_int = 1;

#[repr(C)]
#[derive(Clone, Copy, Default)]
pub struct Timespec {
    pub sec: i64,
    pub nsec: i64,
}

/// Big enough and aligned enough for `pthread_attr_t` and `sigset_t` on glibc and bionic.
#[repr(C, align(16))]
struct Opaque([u8; 256]);

impl Opaque {
    const fn new() -> Opaque {
        Opaque([0; 256])
    }
    fn p(&mut self) -> *mut c_void {
        self.0.as_mut_ptr() as *mut c_void
    }
}

extern "C" {
    fn getenv(name: *const c_char) -> *mut c_char;
    fn clock_gettime(clk: c_int, ts: *mut Timespec) -> c_int;
    fn pthread_create(
        t: *mut u64,
        attr: *const c_void,
        f: extern "C" fn(*mut c_void) -> *mut c_void,
        arg: *mut c_void,
    ) -> c_int;
    fn pthread_attr_init(attr: *mut c_void) -> c_int;
    fn pthread_attr_destroy(attr: *mut c_void) -> c_int;
    fn pthread_attr_setstacksize(attr: *mut c_void, size: usize) -> c_int;
    fn pthread_attr_setdetachstate(attr: *mut c_void, state: c_int) -> c_int;
    fn pthread_sigmask(how: c_int, set: *const c_void, old: *mut c_void) -> c_int;
    fn sigfillset(set: *mut c_void) -> c_int;
    fn pthread_atfork(
        prepare: Option<unsafe extern "C" fn()>,
        parent: Option<unsafe extern "C" fn()>,
        child: Option<unsafe extern "C" fn()>,
    ) -> c_int;
    #[link_name = "abort"]
    fn libc_abort() -> !;
}

#[inline(always)]
unsafe fn raw(n: usize, a0: usize, a1: usize, a2: usize, a3: usize, a4: usize, a5: usize) -> isize {
    let ret: usize;
    #[cfg(target_arch = "aarch64")]
    core::arch::asm!(
        "svc 0",
        in("x8") n,
        inlateout("x0") a0 => ret,
        in("x1") a1,
        in("x2") a2,
        in("x3") a3,
        in("x4") a4,
        in("x5") a5,
        options(nostack),
    );
    #[cfg(target_arch = "x86_64")]
    core::arch::asm!(
        "syscall",
        inlateout("rax") n => ret,
        in("rdi") a0,
        in("rsi") a1,
        in("rdx") a2,
        in("r10") a3,
        in("r8") a4,
        in("r9") a5,
        lateout("rcx") _,
        lateout("r11") _,
        options(nostack),
    );
    ret as isize
}

#[inline]
fn ck(r: isize) -> Result<usize, i32> {
    if (-4095..0).contains(&r) {
        Err((-r) as i32)
    } else {
        Ok(r as usize)
    }
}

#[inline]
fn fdarg(fd: i32) -> usize {
    fd as isize as usize
}

pub fn openat(path: *const c_char, flags: i32, mode: u32) -> Result<i32, i32> {
    ck(unsafe { raw(nr::OPENAT, AT_FDCWD as usize, path as usize, flags as u32 as usize, mode as usize, 0, 0) })
        .map(|v| v as i32)
}

pub fn close(fd: i32) {
    if fd >= 0 {
        unsafe {
            raw(nr::CLOSE, fdarg(fd), 0, 0, 0, 0, 0);
        }
    }
}

pub fn read(fd: i32, buf: &mut [u8]) -> Result<usize, i32> {
    ck(unsafe { raw(nr::READ, fdarg(fd), buf.as_mut_ptr() as usize, buf.len(), 0, 0, 0) })
}

pub fn write(fd: i32, buf: &[u8]) -> Result<usize, i32> {
    ck(unsafe { raw(nr::WRITE, fdarg(fd), buf.as_ptr() as usize, buf.len(), 0, 0, 0) })
}

/// The fields of `struct stat` the transport needs.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct StatInfo {
    pub dev: u64,
    pub ino: u64,
    pub mode: u32,
    pub size: i64,
}

impl StatInfo {
    pub fn is_regular(&self) -> bool {
        self.mode & S_IFMT == S_IFREG
    }
}

fn decode_stat(b: &[u64; 32]) -> StatInfo {
    // Both layouts: st_dev @0, st_ino @8, st_size @48. st_mode is @16 on aarch64 (generic
    // layout) and @24 on x86_64.
    #[cfg(target_arch = "aarch64")]
    let mode = b[2] as u32;
    #[cfg(target_arch = "x86_64")]
    let mode = b[3] as u32;
    StatInfo { dev: b[0], ino: b[1], mode, size: b[6] as i64 }
}

pub fn fstat(fd: i32) -> Result<StatInfo, i32> {
    let mut b = [0u64; 32];
    ck(unsafe { raw(nr::FSTAT, fdarg(fd), b.as_mut_ptr() as usize, 0, 0, 0, 0) })?;
    Ok(decode_stat(&b))
}

pub fn stat(path: *const c_char) -> Result<StatInfo, i32> {
    let mut b = [0u64; 32];
    ck(unsafe { raw(nr::NEWFSTATAT, AT_FDCWD as usize, path as usize, b.as_mut_ptr() as usize, 0, 0, 0) })?;
    Ok(decode_stat(&b))
}

pub fn ftruncate(fd: i32, len: usize) -> Result<(), i32> {
    ck(unsafe { raw(nr::FTRUNCATE, fdarg(fd), len, 0, 0, 0, 0) }).map(|_| ())
}

pub fn mmap(len: usize, prot: usize, flags: usize, fd: i32) -> Result<*mut u8, i32> {
    ck(unsafe { raw(nr::MMAP, 0, len, prot, flags, fdarg(fd), 0) }).map(|p| p as *mut u8)
}

pub fn munmap(p: *mut u8, len: usize) {
    if !p.is_null() {
        unsafe {
            raw(nr::MUNMAP, p as usize, len, 0, 0, 0, 0);
        }
    }
}

/// FUTEX_WAIT. `Ok` = woken; `Err(EAGAIN)` = the word no longer held `expected`;
/// `Err(ETIMEDOUT)`, `Err(EINTR)` (signal), `Err(EFAULT)` (mapping gone) as the kernel reports.
pub fn futex_wait(word: *const AtomicU32, expected: u32, timeout_ns: Option<u64>, shared: bool) -> Result<(), i32> {
    let ts;
    let tsp = match timeout_ns {
        Some(ns) => {
            ts = Timespec { sec: (ns / 1_000_000_000) as i64, nsec: (ns % 1_000_000_000) as i64 };
            &ts as *const Timespec as usize
        }
        None => 0,
    };
    let op = FUTEX_WAIT | if shared { 0 } else { FUTEX_PRIVATE_FLAG };
    ck(unsafe { raw(nr::FUTEX, word as usize, op, expected as usize, tsp, 0, 0) }).map(|_| ())
}

/// FUTEX_WAKE; returns how many waiters were woken.
pub fn futex_wake(word: *const AtomicU32, n: i32, shared: bool) -> usize {
    let op = FUTEX_WAKE | if shared { 0 } else { FUTEX_PRIVATE_FLAG };
    ck(unsafe { raw(nr::FUTEX, word as usize, op, n as u32 as usize, 0, 0, 0) }).unwrap_or(0)
}

/// A connected AF_UNIX stream pair, both ends close-on-exec and blocking.
pub fn socketpair() -> Result<[i32; 2], i32> {
    let mut sv = [-1i32; 2];
    ck(unsafe { raw(nr::SOCKETPAIR, AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0, sv.as_mut_ptr() as usize, 0, 0) })?;
    Ok(sv)
}

/// Queue one byte without blocking and without SIGPIPE.
pub fn send_byte(fd: i32) -> Result<usize, i32> {
    let b = [1u8];
    ck(unsafe { raw(nr::SENDTO, fdarg(fd), b.as_ptr() as usize, 1, MSG_DONTWAIT | MSG_NOSIGNAL, 0, 0) })
}

/// Discard whatever is queued on a socket, never blocking.
pub fn drain(fd: i32) {
    let mut buf = [0u8; 64];
    for _ in 0..16 {
        match ck(unsafe { raw(nr::RECVFROM, fdarg(fd), buf.as_mut_ptr() as usize, buf.len(), MSG_DONTWAIT, 0, 0) }) {
            Ok(n) if n == buf.len() => continue,
            _ => break,
        }
    }
}

/// A UDP socket, close-on-exec and non-blocking (the force-feedback datagrams).
pub fn udp_socket() -> Result<i32, i32> {
    ck(unsafe { raw(nr::SOCKET, AF_INET, SOCK_DGRAM | SOCK_CLOEXEC | SOCK_NONBLOCK, 0, 0, 0, 0) }).map(|v| v as i32)
}

/// `struct sockaddr_in`.
#[repr(C)]
#[allow(dead_code)] // read by the kernel, never by us
struct SockAddrIn {
    family: u16,
    port: u16,     // network order
    addr: [u8; 4], // network order
    zero: [u8; 8],
}

/// Send one datagram to 127.0.0.1:`port`, never blocking and without SIGPIPE.
pub fn send_loopback(fd: i32, buf: &[u8], port: u16) -> Result<usize, i32> {
    let sa = SockAddrIn { family: AF_INET as u16, port: u16::from_ne_bytes(port.to_be_bytes()), addr: [127, 0, 0, 1], zero: [0; 8] };
    ck(unsafe {
        raw(
            nr::SENDTO,
            fdarg(fd),
            buf.as_ptr() as usize,
            buf.len(),
            MSG_DONTWAIT | MSG_NOSIGNAL,
            &sa as *const SockAddrIn as usize,
            core::mem::size_of::<SockAddrIn>(),
        )
    })
}

pub fn fionread(fd: i32) -> Result<i32, i32> {
    let mut v: i32 = 0;
    ck(unsafe { raw(nr::IOCTL, fdarg(fd), FIONREAD, &mut v as *mut i32 as usize, 0, 0, 0) })?;
    Ok(v)
}

pub fn fcntl(fd: i32, cmd: usize, arg: usize) -> Result<usize, i32> {
    ck(unsafe { raw(nr::FCNTL, fdarg(fd), cmd, arg, 0, 0, 0) })
}

pub fn dup3(old: i32, new: i32, flags: i32) -> Result<(), i32> {
    ck(unsafe { raw(nr::DUP3, fdarg(old), fdarg(new), flags as u32 as usize, 0, 0, 0) }).map(|_| ())
}

pub fn flock(fd: i32, op: usize) -> Result<(), i32> {
    ck(unsafe { raw(nr::FLOCK, fdarg(fd), op, 0, 0, 0, 0) }).map(|_| ())
}

pub fn renameat(old: *const c_char, new: *const c_char) -> Result<(), i32> {
    ck(unsafe { raw(nr::RENAMEAT, AT_FDCWD as usize, old as usize, AT_FDCWD as usize, new as usize, 0, 0) })
        .map(|_| ())
}

pub fn unlinkat(path: *const c_char) -> Result<(), i32> {
    ck(unsafe { raw(nr::UNLINKAT, AT_FDCWD as usize, path as usize, 0, 0, 0, 0) }).map(|_| ())
}

pub fn mkdirat(path: *const c_char, mode: u32) -> Result<(), i32> {
    ck(unsafe { raw(nr::MKDIRAT, AT_FDCWD as usize, path as usize, mode as usize, 0, 0, 0) }).map(|_| ())
}

/// ppoll with a millisecond timeout (negative = forever). Used by tests and diagnostics.
pub fn poll(fds: *mut PollFd, n: usize, timeout_ms: i32) -> Result<usize, i32> {
    // ppoll writes the remaining time back, so the timespec must be writable.
    let mut ts = Timespec { sec: (timeout_ms / 1000) as i64, nsec: ((timeout_ms % 1000) as i64) * 1_000_000 };
    let tsp = if timeout_ms < 0 { 0 } else { &mut ts as *mut Timespec as usize };
    ck(unsafe { raw(nr::PPOLL, fds as usize, n, tsp, 0, 8, 0) })
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct PollFd {
    pub fd: i32,
    pub events: i16,
    pub revents: i16,
}

pub fn getpid() -> i32 {
    unsafe { raw(nr::GETPID, 0, 0, 0, 0, 0, 0) as i32 }
}

pub fn sched_yield() {
    unsafe {
        raw(nr::SCHED_YIELD, 0, 0, 0, 0, 0, 0);
    }
}

/// Name the calling thread (NUL-terminated, at most 16 bytes including the NUL).
pub fn set_thread_name(name: &[u8]) {
    unsafe {
        raw(nr::PRCTL, PR_SET_NAME, name.as_ptr() as usize, 0, 0, 0, 0);
    }
}

pub fn mono_ns() -> u64 {
    let mut ts = Timespec::default();
    unsafe {
        clock_gettime(CLOCK_MONOTONIC, &mut ts);
    }
    (ts.sec as u64).wrapping_mul(1_000_000_000).wrapping_add(ts.nsec as u64)
}

/// CLOCK_REALTIME as (seconds, microseconds): the evdev event timestamp.
pub fn realtime_us() -> (i64, i64) {
    let mut ts = Timespec::default();
    unsafe {
        clock_gettime(CLOCK_REALTIME, &mut ts);
    }
    (ts.sec, ts.nsec / 1000)
}

pub fn env(name: &[u8]) -> Option<&'static [u8]> {
    debug_assert!(name.last() == Some(&0));
    let p = unsafe { getenv(name.as_ptr() as *const c_char) };
    if p.is_null() {
        return None;
    }
    let mut n = 0usize;
    while n < 65536 && unsafe { *p.add(n) } != 0 {
        n += 1;
    }
    Some(unsafe { core::slice::from_raw_parts(p as *const u8, n) })
}

/// Start a detached thread with every signal blocked, so it can never run a handler that the
/// host process (Wine in particular) installed for its own threads.
pub fn spawn_detached(f: extern "C" fn(*mut c_void) -> *mut c_void, arg: *mut c_void, stack: usize) -> bool {
    unsafe {
        let mut attr = Opaque::new();
        if pthread_attr_init(attr.p()) != 0 {
            return false;
        }
        pthread_attr_setdetachstate(attr.p(), PTHREAD_CREATE_DETACHED);
        // Below the libc minimum the call fails and the default stack is used instead.
        let _ = pthread_attr_setstacksize(attr.p(), stack);
        let mut all = Opaque::new();
        let mut old = Opaque::new();
        sigfillset(all.p());
        let masked = pthread_sigmask(SIG_SETMASK, all.p(), old.p()) == 0;
        let mut t: u64 = 0;
        let r = pthread_create(&mut t, attr.p(), f, arg);
        if masked {
            pthread_sigmask(SIG_SETMASK, old.p(), core::ptr::null_mut());
        }
        pthread_attr_destroy(attr.p());
        r == 0
    }
}

pub fn register_atfork_child(child: unsafe extern "C" fn()) -> bool {
    unsafe { pthread_atfork(None, None, Some(child)) == 0 }
}

pub fn abort() -> ! {
    unsafe { libc_abort() }
}

/// A NUL-terminated path assembled in a stack buffer.
pub struct PathBuf {
    buf: [u8; PATH_MAX],
    len: usize,
}

pub const PATH_MAX: usize = 1100;

impl Default for PathBuf {
    fn default() -> Self {
        PathBuf::new()
    }
}

impl PathBuf {
    pub const fn new() -> PathBuf {
        PathBuf { buf: [0; PATH_MAX], len: 0 }
    }

    /// Appends `s`; returns false (leaving the path unchanged) if it would not fit.
    pub fn push(&mut self, s: &[u8]) -> bool {
        if self.len + s.len() + 1 > PATH_MAX || s.contains(&0) {
            return false;
        }
        self.buf[self.len..self.len + s.len()].copy_from_slice(s);
        self.len += s.len();
        self.buf[self.len] = 0;
        true
    }

    pub fn push_u(&mut self, v: u64) -> bool {
        let mut tmp = [0u8; 20];
        let mut i = tmp.len();
        let mut v = v;
        loop {
            i -= 1;
            tmp[i] = b'0' + (v % 10) as u8;
            v /= 10;
            if v == 0 {
                break;
            }
        }
        self.push(&tmp[i..])
    }

    pub fn as_ptr(&self) -> *const c_char {
        self.buf.as_ptr() as *const c_char
    }

    pub fn bytes(&self) -> &[u8] {
        &self.buf[..self.len]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn stat_layout_and_file_ops() {
        let dir = std::env::temp_dir().join(format!("hermes-sys-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let mut p = PathBuf::new();
        assert!(p.push(dir.to_str().unwrap().as_bytes()) && p.push(b"/f") && p.push_u(42));
        let fd = openat(p.as_ptr(), O_RDWR | O_CREAT | O_EXCL | O_CLOEXEC, 0o600).unwrap();
        ftruncate(fd, 4096).unwrap();
        let a = fstat(fd).unwrap();
        let b = stat(p.as_ptr()).unwrap();
        let meta = std::fs::metadata(dir.join("f42")).unwrap();
        use std::os::unix::fs::MetadataExt;
        assert_eq!(a, b);
        assert_eq!(a.size, 4096);
        assert_eq!(a.ino, meta.ino());
        assert_eq!(a.dev, meta.dev());
        assert!(a.is_regular());
        assert!(!stat(b"/\0".as_ptr() as *const c_char).unwrap().is_regular());
        assert_eq!(openat(p.as_ptr(), O_RDWR | O_CREAT | O_EXCL, 0o600), Err(EEXIST));
        close(fd);
        unlinkat(p.as_ptr()).unwrap();
        assert_eq!(stat(p.as_ptr()), Err(ENOENT));
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn futex_timeout_and_mismatch() {
        let w = AtomicU32::new(5);
        let t0 = mono_ns();
        assert_eq!(futex_wait(&w, 5, Some(2_000_000), false), Err(ETIMEDOUT));
        assert!(mono_ns() - t0 >= 1_500_000);
        assert_eq!(futex_wait(&w, 6, Some(2_000_000), false), Err(EAGAIN));
        assert_eq!(futex_wake(&w, 1, false), 0);
    }

    #[test]
    fn socket_byte_and_drain() {
        let sv = socketpair().unwrap();
        assert_eq!(fionread(sv[0]).unwrap(), 0);
        send_byte(sv[1]).unwrap();
        send_byte(sv[1]).unwrap();
        assert_eq!(fionread(sv[0]).unwrap(), 2);
        drain(sv[0]);
        assert_eq!(fionread(sv[0]).unwrap(), 0);
        let mut pfd = PollFd { fd: sv[0], events: POLLIN, revents: 0 };
        assert_eq!(poll(&mut pfd, 1, 0).unwrap(), 0);
        close(sv[1]);
        assert_eq!(poll(&mut pfd, 1, 100).unwrap(), 1);
        assert!(pfd.revents & POLLHUP != 0, "peer close must report POLLHUP, got {:#x}", pfd.revents);
        close(sv[0]);
    }

    #[test]
    fn udp_datagram_to_loopback() {
        let rx = std::net::UdpSocket::bind("127.0.0.1:0").unwrap();
        rx.set_read_timeout(Some(std::time::Duration::from_secs(2))).unwrap();
        let port = rx.local_addr().unwrap().port();
        let fd = udp_socket().unwrap();
        assert!(fcntl(fd, F_GETFL, 0).unwrap() & O_NONBLOCK as usize != 0, "non-blocking");
        assert!(fcntl(fd, F_GETFD, 0).unwrap() & FD_CLOEXEC != 0, "close-on-exec");
        assert_eq!(send_loopback(fd, b"HXR1-test", port), Ok(9));
        let mut b = [0u8; 32];
        let (n, from) = rx.recv_from(&mut b).unwrap();
        assert_eq!(&b[..n], b"HXR1-test");
        assert!(from.ip().is_loopback());
        close(fd);
    }
}
