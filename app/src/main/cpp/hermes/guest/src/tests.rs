//! End-to-end scenarios through the C ABI. Every scenario runs in forked child processes: the
//! guest runtime is process-global (it reads FAKE_EVDEV_MEMFD_PATHS once, owns helper threads and
//! a fork handler), so each scenario gets a fresh process, and the multi-process ones (writer and
//! reader in different processes, a reader that forks, a writer that is killed) are real.
//! Run with `--test-threads=1`.

use super::*;
use hermes_core::abi::*;
use hermes_core::pad::PadInput;
use hermes_core::sys::{self, PollFd, POLLERR, POLLHUP, POLLIN};
use hermes_core::writer::SlotWriter;
use std::path::PathBuf;
use std::sync::atomic::AtomicU64;
use std::sync::atomic::Ordering::{Acquire, Relaxed, Release, SeqCst};
use std::time::{Duration, Instant};

extern "C" {
    fn fork() -> i32;
    fn waitpid(pid: i32, status: *mut i32, options: i32) -> i32;
    fn _exit(code: i32) -> !;
    fn kill(pid: i32, sig: i32) -> i32;
}

const SIGKILL: i32 = 9;

fn say(msg: &str) {
    let _ = sys::write(2, msg.as_bytes());
}

/// The library's own diagnostics (re-attach, orphaned, writer gone, unplug, resync), to stderr
/// with the pid, so a failing scenario shows which path the library took.
unsafe extern "C" fn test_log(level: c_int, line: *const core::ffi::c_char) {
    if level == 0 && std::env::var_os("HERMES_TEST_VERBOSE").is_none() {
        return;
    }
    let msg = std::ffi::CStr::from_ptr(line).to_string_lossy();
    say(&format!("  (pid {} lvl {}) {}\n", sys::getpid(), level, msg));
}

/// Run `f` in a forked child; returns the child's pid. The child never returns into the harness.
fn spawn<F: FnOnce() -> Result<(), String>>(name: &'static str, f: F) -> i32 {
    let pid = unsafe { fork() };
    assert!(pid >= 0, "fork failed");
    if pid == 0 {
        hermes_set_logger(Some(test_log));
        std::panic::set_hook(Box::new(move |info| say(&format!("[{}] panic: {}\n", name, info))));
        let code = match std::panic::catch_unwind(std::panic::AssertUnwindSafe(f)) {
            Ok(Ok(())) => 0,
            Ok(Err(m)) => {
                say(&format!("[{}] FAILED: {}\n", name, m));
                1
            }
            Err(_) => 2,
        };
        unsafe { _exit(code) }
    }
    pid
}

fn wait_pid(pid: i32) -> i32 {
    let mut st = 0i32;
    unsafe { waitpid(pid, &mut st, 0) };
    if st & 0x7f == 0 {
        (st >> 8) & 0xff
    } else {
        128 + (st & 0x7f)
    }
}

fn run<F: FnOnce() -> Result<(), String>>(name: &'static str, f: F) {
    let pid = spawn(name, f);
    assert_eq!(wait_pid(pid), 0, "scenario '{}' failed, see stderr", name);
}

macro_rules! ensure {
    ($cond:expr, $($fmt:tt)+) => {
        if !$cond {
            return Err(format!($($fmt)+));
        }
    };
}

/// Cross-process coordination words (MAP_SHARED | MAP_ANONYMOUS, inherited across fork).
struct Shm(*mut AtomicU64);
impl Shm {
    fn new() -> Shm {
        let p = sys::mmap(4096, sys::PROT_READ | sys::PROT_WRITE, sys::MAP_SHARED | sys::MAP_ANONYMOUS, -1).unwrap();
        Shm(p as *mut AtomicU64)
    }
    fn at(&self, i: usize) -> &AtomicU64 {
        assert!(i < 512);
        unsafe { &*self.0.add(i) }
    }
    fn get(&self, i: usize) -> u64 {
        self.at(i).load(SeqCst)
    }
    fn set(&self, i: usize, v: u64) {
        self.at(i).store(v, SeqCst)
    }
    fn wait(&self, i: usize, at_least: u64, secs: u64) -> Result<(), String> {
        let end = Instant::now() + Duration::from_secs(secs);
        while self.get(i) < at_least {
            if Instant::now() > end {
                return Err(format!("timed out waiting for shm[{}] >= {} (is {})", i, at_least, self.get(i)));
            }
            std::thread::yield_now();
        }
        Ok(())
    }
}
unsafe impl Send for Shm {}
unsafe impl Sync for Shm {}

struct Scene {
    dir: PathBuf,
    env: String,
}

impl Scene {
    fn new(tag: &str) -> Scene {
        use std::sync::atomic::AtomicU32;
        static N: AtomicU32 = AtomicU32::new(0);
        let dir = std::env::temp_dir().join(format!(
            "hermes-g-{}-{}-{}",
            tag,
            std::process::id(),
            N.fetch_add(1, Relaxed)
        ));
        let _ = std::fs::remove_dir_all(&dir);
        std::fs::create_dir_all(dir.join("hermes")).unwrap();
        let env = (0..4)
            .map(|s| format!("{}={}/hermes/pad{}", s, dir.display(), s))
            .collect::<Vec<_>>()
            .join(";");
        Scene { dir, env }
    }
    fn backing(&self) -> String {
        format!("{}/hermes", self.dir.display())
    }
    fn pad(&self, slot: u32) -> PathBuf {
        self.dir.join("hermes").join(format!("pad{}", slot))
    }
    /// Inside a child, before the first call into the library.
    fn enter(&self) {
        std::env::set_var("FAKE_EVDEV_MEMFD_PATHS", &self.env);
    }
    fn writer(&self, slot: u32) -> SlotWriter {
        let mut w = SlotWriter::new(slot);
        w.prepare(self.backing().as_bytes()).expect("prepare");
        w
    }
}

fn pad(buttons: u16) -> PadInput {
    PadInput { buttons, axes: [0; AXES] }
}

fn pad_axes(buttons: u16, axes: [i32; AXES]) -> PadInput {
    PadInput { buttons, axes }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct E {
    t: u16,
    c: u16,
    v: i32,
}

fn parse(buf: &[u8]) -> Vec<E> {
    buf.chunks_exact(EVENT_SIZE)
        .map(|b| E {
            t: u16::from_le_bytes([b[16], b[17]]),
            c: u16::from_le_bytes([b[18], b[19]]),
            v: i32::from_le_bytes([b[20], b[21], b[22], b[23]]),
        })
        .collect()
}

fn key(f: &[E], code: u16) -> Option<i32> {
    f.iter().find(|e| e.t == EV_KEY && e.c == code).map(|e| e.v)
}

fn abs(f: &[E], code: u16) -> Option<i32> {
    f.iter().find(|e| e.t == EV_ABS && e.c == code).map(|e| e.v)
}

fn is_full(f: &[E]) -> bool {
    f.len() == 20
        && (0..BUTTONS).all(|b| f[b].t == EV_KEY && f[b].c == BUTTON_CODES[b])
        && (0..AXES).all(|i| f[BUTTONS + i].t == EV_ABS && f[BUTTONS + i].c == AXIS_CODES[i])
        && f[19] == E { t: EV_SYN, c: SYN_REPORT, v: 0 }
}

fn neutral_full(f: &[E]) -> bool {
    is_full(f) && f[..19].iter().all(|e| e.v == 0)
}

/// A reader over one handle: buffers events and hands out whole frames.
struct Rd {
    h: u32,
    fd: i32,
    pending: Vec<E>,
}

impl Rd {
    fn attach(slot: i32, flags: i32) -> Result<Rd, String> {
        let mut h = 0u32;
        let fd = unsafe { hermes_attach(slot, flags, &mut h) };
        ensure!(fd >= 0, "attach({}) failed: {}", slot, fd);
        ensure!(hermes_fd(h) == fd, "hermes_fd mismatch");
        Ok(Rd { h, fd, pending: Vec::new() })
    }

    /// One nonblocking read; Err(errno).
    fn read_nb(&mut self) -> Result<usize, i32> {
        let mut buf = [0u8; EVENT_SIZE * 64];
        let r = unsafe { hermes_read(self.h, buf.as_mut_ptr() as *mut c_void, buf.len(), 1) };
        if r < 0 {
            return Err((-r) as i32);
        }
        let ev = parse(&buf[..r as usize]);
        let n = ev.len();
        self.pending.extend(ev);
        Ok(n)
    }

    fn poll(&self, ms: i32) -> i16 {
        let mut p = PollFd { fd: self.fd, events: POLLIN, revents: 0 };
        match sys::poll(&mut p, 1, ms) {
            Ok(0) => 0,
            Ok(_) => p.revents,
            Err(_) => 0,
        }
    }

    fn take_frame(&mut self) -> Option<Vec<E>> {
        let end = self.pending.iter().position(|e| e.t == EV_SYN && e.c == SYN_REPORT)?;
        Some(self.pending.drain(..=end).collect())
    }

    /// The next whole frame, waiting (natively, with poll) up to `ms`.
    fn frame(&mut self, ms: u64) -> Result<Vec<E>, String> {
        let end = Instant::now() + Duration::from_millis(ms);
        loop {
            if let Some(f) = self.take_frame() {
                return Ok(f);
            }
            let left = end.saturating_duration_since(Instant::now());
            if left.is_zero() {
                return Err(format!("no frame within {} ms (partial: {:?})", ms, self.pending));
            }
            self.poll(left.as_millis().clamp(1, 1000) as i32);
            match self.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read failed: errno {}", e)),
            }
        }
    }

    /// Wait for a frame satisfying `pred`, skipping others.
    fn frame_where(&mut self, ms: u64, what: &str, pred: impl Fn(&[E]) -> bool) -> Result<Vec<E>, String> {
        let end = Instant::now() + Duration::from_millis(ms);
        loop {
            let left = end.saturating_duration_since(Instant::now());
            if left.is_zero() {
                return Err(format!("never saw {}", what));
            }
            match self.frame(left.as_millis().max(1) as u64) {
                Ok(f) if pred(&f) => return Ok(f),
                Ok(_) => {}
                Err(e) => return Err(format!("waiting for {}: {}", what, e)),
            }
        }
    }

    /// Everything deliverable right now.
    fn drain(&mut self) -> Result<Vec<Vec<E>>, String> {
        loop {
            match self.read_nb() {
                Ok(_) => {}
                Err(sys::EAGAIN) => break,
                Err(e) => return Err(format!("read failed: errno {}", e)),
            }
        }
        let mut out = Vec::new();
        while let Some(f) = self.take_frame() {
            out.push(f);
        }
        ensure!(self.pending.is_empty(), "torn frame left over: {:?}", self.pending);
        Ok(out)
    }

    fn state(&self) -> Result<HermesState, i32> {
        let mut s = HermesState { buttons: [0; BUTTONS], axes: [0; AXES] };
        let r = unsafe { hermes_current_state(self.h, &mut s) };
        if r < 0 {
            Err(-r)
        } else {
            Ok(s)
        }
    }
}

/// Tracks one button's edges across frames: strict alternation, MSC_SCAN before each EV_KEY,
/// never two edges of the same button inside one frame.
struct Taps {
    code: u16,
    down: bool,
    presses: u64,
    releases: u64,
}

impl Taps {
    fn new(code: u16) -> Taps {
        Taps { code, down: false, presses: 0, releases: 0 }
    }
    fn feed(&mut self, f: &[E]) -> Result<(), String> {
        let mut edges = 0;
        for (i, e) in f.iter().enumerate() {
            if e.t != EV_KEY || e.c != self.code {
                continue;
            }
            if is_full(f) {
                self.down = e.v == 1;
                continue;
            }
            edges += 1;
            ensure!(edges == 1, "two edges of {:#x} in one frame: {:?}", self.code, f);
            ensure!(
                i > 0 && f[i - 1] == E { t: EV_MSC, c: MSC_SCAN, v: self.code as i32 },
                "EV_KEY without MSC_SCAN before it: {:?}",
                f
            );
            if e.v == 1 {
                ensure!(!self.down, "press while already down (press #{})", self.presses + 1);
                self.down = true;
                self.presses += 1;
            } else {
                ensure!(self.down, "release while already up (release #{})", self.releases + 1);
                self.down = false;
                self.releases += 1;
            }
        }
        Ok(())
    }
}

fn stats() -> StatsView {
    let mut s = StatsView::default();
    unsafe { hermes_get_stats(&mut s, core::mem::size_of::<StatsView>()) };
    s
}

// ---------------------------------------------------------------------------------------------

#[test]
fn unknown_slots_bad_handles_and_small_buffers() {
    let sc = Scene::new("args");
    run("args", || {
        std::env::set_var("FAKE_EVDEV_MEMFD_PATHS", format!("0={0}/pad0;2={0}/pad2", sc.backing()));
        ensure!(hermes_init() == 0b101, "init mask {}", hermes_init());
        let mut h = 0u32;
        for s in [1, 3, 4, -1, 99] {
            let r = unsafe { hermes_attach(s, 0, &mut h) };
            ensure!(r == -sys::ENODEV, "slot {} gave {}", s, r);
        }
        ensure!(unsafe { hermes_attach(0, 0, core::ptr::null_mut()) } == -sys::EINVAL, "null out handle");
        let mut buf = [0u8; 64];
        ensure!(unsafe { hermes_read(12345, buf.as_mut_ptr() as *mut c_void, 64, 1) } == -(sys::EBADF as isize), "bad handle read");
        ensure!(hermes_detach(0) == -sys::EBADF, "detach 0");
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(hermes_slot(r.h) == 0, "slot");
        let n = unsafe { hermes_read(r.h, buf.as_mut_ptr() as *mut c_void, 23, 1) };
        ensure!(n == -(sys::EINVAL as isize), "short buffer gave {}", n);
        // one event at a time: the full-state frame continues across reads
        let mut evs = Vec::new();
        for _ in 0..20 {
            let n = unsafe { hermes_read(r.h, buf.as_mut_ptr() as *mut c_void, 47, 1) };
            ensure!(n == 24, "a 47-byte buffer takes exactly one event, got {}", n);
            evs.extend(parse(&buf[..24]));
        }
        ensure!(neutral_full(&evs), "split full frame {:?}", evs);
        ensure!(r.read_nb() == Err(sys::EAGAIN), "nothing more");
        let h = r.h;
        ensure!(hermes_detach(h) == 0, "detach");
        ensure!(hermes_detach(h) == -sys::EBADF, "double detach");
        ensure!(hermes_fd(h) == -sys::EBADF, "fd after detach");
        ensure!(r.read_nb() == Err(sys::EBADF), "read after detach");
        Ok(())
    });
}

#[test]
fn late_reader_gets_full_current_state_first() {
    let sc = Scene::new("late");
    run("late", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        w.set_pad(pad_axes(1 << B_A, [20000, -300, 0, 0, 128, 0, -1, 0]));
        w.set_overlay(1 << B_MODE);
        let mut r = Rd::attach(0, sys::O_NONBLOCK | sys::O_CLOEXEC)?;
        ensure!(r.poll(0) & POLLIN != 0, "a new handle is readable at once");
        let f = r.frame(1000)?;
        ensure!(is_full(&f), "first frame must be full state: {:?}", f);
        ensure!(key(&f, BTN_A) == Some(1) && key(&f, BTN_MODE) == Some(1) && key(&f, BTN_B) == Some(0), "{:?}", f);
        ensure!(abs(&f, ABS_X) == Some(20000) && abs(&f, ABS_Y) == Some(-300), "{:?}", f);
        ensure!(abs(&f, ABS_BRAKE) == Some(128) && abs(&f, ABS_HAT0X) == Some(-1), "{:?}", f);
        ensure!(r.drain()?.is_empty(), "nothing after the full state");
        ensure!(r.poll(0) == 0, "not readable once drained");
        ensure!(hermes_has_pending(r.h) == 0, "has_pending");
        let s = r.state().map_err(|e| format!("state {}", e))?;
        ensure!(s.buttons[B_A] == 1 && s.buttons[B_MODE] == 1 && s.axes[A_LX] == 20000, "state");
        let fdflags = sys::fcntl(r.fd, sys::F_GETFD, 0).unwrap();
        ensure!(fdflags & sys::FD_CLOEXEC != 0, "O_CLOEXEC honoured");
        let fl = sys::fcntl(r.fd, sys::F_GETFL, 0).unwrap();
        ensure!(fl & sys::O_NONBLOCK as usize != 0, "O_NONBLOCK honoured");
        let r2 = Rd::attach(0, 0)?;
        let fdflags = sys::fcntl(r2.fd, sys::F_GETFD, 0).unwrap();
        ensure!(fdflags & sys::FD_CLOEXEC == 0, "no O_CLOEXEC unless asked");
        Ok(())
    });
}

#[test]
fn reader_before_writer_and_first_open_is_not_a_removal() {
    let sc = Scene::new("before");
    run("before", || {
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?; // nothing exists yet
        let f = r.frame(1000)?;
        ensure!(neutral_full(&f), "neutral pad while there is no backing: {:?}", f);
        ensure!(r.drain()?.is_empty(), "quiet");
        let mut w = sc.writer(0); // backing appears, never opened
        r.frame_where(3000, "re-attach full state", |f| is_full(f))?;
        let mut r2 = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(neutral_full(&r2.frame(1000)?), "second reader neutral");
        w.open();
        w.set_pad(pad(1 << B_B));
        for rd in [&mut r, &mut r2] {
            let f = rd.frame_where(2000, "B press", |f| key(f, BTN_B) == Some(1))?;
            ensure!(!is_full(&f), "a change frame, not a resync: {:?}", f);
            ensure!(hermes_is_removed(rd.h) == 0, "first open must not look like an unplug");
        }
        Ok(())
    });
}

#[test]
fn taps_between_reads_keep_order_across_buttons() {
    let sc = Scene::new("qam");
    run("qam", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        // Quick Access Menu: Guide down, A down, A up, Guide up, all before the reader looks.
        w.set_overlay(1 << B_MODE);
        w.set_overlay((1 << B_MODE) | (1 << B_A));
        w.set_overlay(1 << B_MODE);
        w.set_overlay(0);
        // and a plain tap of X
        w.set_pad(pad(1 << B_X));
        w.set_pad(pad(0));
        std::thread::sleep(Duration::from_millis(20));
        let frames = r.drain()?;
        let seq: Vec<(u16, i32)> = frames
            .iter()
            .map(|f| {
                let k: Vec<_> = f.iter().filter(|e| e.t == EV_KEY).collect();
                assert_eq!(k.len(), 1, "one edge per frame here: {:?}", f);
                (k[0].c, k[0].v)
            })
            .collect();
        ensure!(
            seq == vec![(BTN_MODE, 1), (BTN_A, 1), (BTN_A, 0), (BTN_MODE, 0), (BTN_X, 1), (BTN_X, 0)],
            "edges out of order: {:?}",
            seq
        );
        // the overlay never releases what the pad holds
        w.set_pad(pad(1 << B_A));
        w.set_overlay(1 << B_A);
        w.set_overlay(0);
        std::thread::sleep(Duration::from_millis(20));
        let frames = r.drain()?;
        let a: Vec<i32> = frames.iter().filter_map(|f| key(f, BTN_A)).collect();
        ensure!(a == vec![1], "clearing the overlay released a held button: {:?}", a);
        Ok(())
    });
}

#[test]
fn two_process_100k_taps_zero_loss() {
    const N: u64 = 100_000;
    const WINDOW: u64 = 32;
    let sc = Scene::new("100k");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 taps fully seen by the reader, 3 reader done
    let wpid = spawn("100k-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let end = Instant::now() + Duration::from_secs(120);
        for i in 0..N {
            while i >= shm.get(2) + WINDOW {
                ensure!(Instant::now() < end, "reader stalled at {} of {}", shm.get(2), N);
                std::thread::yield_now();
            }
            w.set_pad(pad(1 << B_A));
            w.set_pad(pad(0));
        }
        shm.wait(3, 1, 120)?;
        Ok(())
    });
    let rpid = spawn("100k-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full state first");
        shm.set(1, 1);
        let mut t = Taps::new(BTN_A);
        while t.releases < N {
            if r.poll(5000) == 0 {
                return Err(format!("no readiness for 5 s at {} presses / {} releases", t.presses, t.releases));
            }
            for f in r.drain()? {
                t.feed(&f)?;
            }
            shm.set(2, t.releases);
        }
        shm.set(3, 1);
        let s = stats();
        ensure!(t.presses == N && t.releases == N, "presses {} releases {}", t.presses, t.releases);
        ensure!(s.coalesced_taps == 0 && s.resyncs == 0, "coalesced {} resyncs {}", s.coalesced_taps, s.resyncs);
        say(&format!(
            "[100k] journal frames {} counter frames {} helper wakes {}\n",
            s.journal_frames, s.counter_frames, s.helper_wakes
        ));
        Ok(())
    });
    let (a, b) = (wait_pid(wpid), wait_pid(rpid));
    assert_eq!((a, b), (0, 0), "writer/reader exit codes");
}

#[test]
fn burst_taps_are_all_delivered_or_accounted_for() {
    const N: u64 = 100_000;
    let sc = Scene::new("burst");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 writer finished, 3 reader done
    let wpid = spawn("burst-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        for _ in 0..N {
            w.set_pad(pad(1 << B_Y));
            w.set_pad(pad(0));
        }
        w.set_pad(pad_axes(0, [777, 0, 0, 0, 0, 0, 0, 0]));
        shm.set(2, 1);
        shm.wait(3, 1, 120)?;
        Ok(())
    });
    let rpid = spawn("burst-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        shm.set(1, 1);
        let mut t = Taps::new(BTN_Y);
        let mut x = 0;
        let end = Instant::now() + Duration::from_secs(120);
        loop {
            ensure!(Instant::now() < end, "never caught up");
            r.poll(100);
            for f in r.drain()? {
                t.feed(&f)?;
                if let Some(v) = abs(&f, ABS_X) {
                    x = v;
                }
            }
            if shm.get(2) == 1 && x == 777 && hermes_has_pending(r.h) == 0 {
                break;
            }
        }
        shm.set(3, 1);
        let s = stats();
        ensure!(!t.down && t.presses == t.releases, "ends released: {} / {}", t.presses, t.releases);
        ensure!(
            t.presses + s.coalesced_taps == N,
            "taps delivered {} + coalesced {} != {}",
            t.presses,
            s.coalesced_taps,
            N
        );
        ensure!(s.resyncs == 0, "resyncs {}", s.resyncs);
        say(&format!("[burst] delivered {} coalesced {} counter frames {}\n", t.presses, s.coalesced_taps, s.counter_frames));
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn stick_spam_never_starves_buttons_and_catches_up_at_once() {
    const PUBLISHES: u64 = 300_000;
    const EVERY: u64 = 1000;
    const TAPS: u64 = PUBLISHES / EVERY;
    let sc = Scene::new("spam");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 B taps fully seen, 3 writer finished, 4 reader done
    let wpid = spawn("spam-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let mut seed = 0x1234_5678u64;
        let end = Instant::now() + Duration::from_secs(120);
        let mut taps = 0u64;
        for i in 0..PUBLISHES {
            seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
            let v = (seed >> 33) as i32;
            let axes = [v % 32767, -(v % 30000), v % 1000, v % 77, v & 255, (v >> 8) & 255, 0, 0];
            let b = if i % EVERY == 0 {
                while taps >= shm.get(2) + 32 {
                    ensure!(Instant::now() < end, "reader stalled");
                    std::thread::yield_now();
                }
                taps += 1;
                1 << B_B
            } else {
                0
            };
            w.set_pad(pad_axes(b, axes));
        }
        // every tap delivered before the final state, so the catch-up below measures sticks only
        shm.wait(2, TAPS, 120)?;
        w.set_pad(pad_axes(0, [111, -222, 333, -444, 55, 66, 1, -1]));
        shm.set(3, 1);
        shm.wait(4, 1, 120)?;
        Ok(())
    });
    let rpid = spawn("spam-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        shm.set(1, 1);
        let mut t = Taps::new(BTN_B);
        let mut axes = [0i32; AXES];
        let (mut reads, mut axis_only) = (0u64, 0u64);
        let absorb = |f: &[E], t: &mut Taps, axes: &mut [i32; AXES], axis_only: &mut u64| -> Result<(), String> {
            t.feed(f)?;
            if !f.iter().any(|e| e.t == EV_KEY) {
                *axis_only += 1;
            }
            for e in f {
                if e.t == EV_ABS {
                    axes[AXIS_CODES.iter().position(|c| *c == e.c).unwrap()] = e.v;
                }
            }
            Ok(())
        };
        let end = Instant::now() + Duration::from_secs(120);
        while shm.get(3) == 0 {
            ensure!(Instant::now() < end, "writer never finished");
            std::thread::sleep(Duration::from_micros(500)); // a deliberately slow reader
            r.poll(10);
            match r.read_nb() {
                Ok(_) => reads += 1,
                Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            while let Some(f) = r.take_frame() {
                absorb(&f, &mut t, &mut axes, &mut axis_only)?;
            }
            shm.set(2, t.releases);
        }
        // The writer is done: whatever is left is at most the one latest stick frame, delivered
        // within the pacing interval.
        const FINAL: [i32; AXES] = [111, -222, 333, -444, 55, 66, 1, -1];
        let tail_end = Instant::now() + Duration::from_millis(200);
        let mut tail = 0usize;
        while axes != FINAL && Instant::now() < tail_end {
            r.poll(20);
            match r.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            while let Some(f) = r.take_frame() {
                tail += 1;
                absorb(&f, &mut t, &mut axes, &mut axis_only)?;
            }
        }
        ensure!(tail <= 1, "catch-up took {} frames", tail);
        ensure!(r.drain()?.is_empty(), "nothing after the final state");
        shm.set(4, 1);
        say(&format!("[spam] {} reads, {} stick-only frames for {} publishes\n", reads, axis_only, PUBLISHES));
        ensure!(axis_only <= reads + 2, "sticks were not coalesced: {} frames over {} reads", axis_only, reads);
        ensure!(axes == FINAL, "final axes {:?}", axes);
        ensure!(t.presses == TAPS && t.releases == TAPS, "B taps {} / {} of {}", t.presses, t.releases, TAPS);
        ensure!(stats().coalesced_taps == 0, "no tap may be coalesced");
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn writer_restart_mid_stream_goes_neutral_then_resyncs() {
    let sc = Scene::new("restart");
    let shm = Shm::new();
    // shm: 0 writer A ready, 1 reader saw A, 2 reader saw neutral, 3 reader saw B, 4 reader saw Y tap, 5 B ready
    let wa = spawn("writer-a", || {
        let mut w = sc.writer(0);
        w.open();
        w.set_pad(pad_axes(1 << B_A, [1000, 0, 0, 0, 0, 0, 0, 0]));
        shm.set(0, 1);
        loop {
            std::thread::sleep(Duration::from_millis(100));
        }
    });
    shm.wait(0, 1, 30).unwrap();
    let rp = spawn("restart-reader", || {
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        let f = r.frame(1000)?;
        ensure!(key(&f, BTN_A) == Some(1) && abs(&f, ABS_X) == Some(1000), "writer A state: {:?}", f);
        shm.set(1, 1);
        r.frame_where(5000, "neutral after the writer died", neutral_full)?;
        ensure!(hermes_is_removed(r.h) == 0, "a dead writer is not an unplug");
        shm.set(2, 1);
        let f = r.frame_where(5000, "writer B state", |f| key(f, BTN_B) == Some(1))?;
        ensure!(abs(&f, ABS_X) == Some(-2000) && key(&f, BTN_A) != Some(1), "{:?}", f);
        let st = r.state().map_err(|e| format!("state {}", e))?;
        ensure!(st.buttons[B_B] == 1 && st.buttons[B_A] == 0 && st.axes[A_LX] == -2000, "state after B");
        shm.set(3, 1);
        r.frame_where(3000, "Y press", |f| key(f, BTN_Y) == Some(1) && !is_full(f))?;
        r.frame_where(3000, "Y release", |f| key(f, BTN_Y) == Some(0) && !is_full(f))?;
        ensure!(hermes_is_removed(r.h) == 0, "never removed");
        let s = stats();
        ensure!(s.writer_dead >= 1 && s.reattaches >= 1, "stats {:?}", s);
        shm.set(4, 1);
        Ok(())
    });
    shm.wait(1, 1, 30).unwrap();
    unsafe { kill(wa, SIGKILL) };
    wait_pid(wa);
    shm.wait(2, 1, 30).unwrap();
    let wb = spawn("writer-b", || {
        let mut w = sc.writer(0);
        w.open();
        w.set_pad(pad_axes(1 << B_B, [-2000, 0, 0, 0, 0, 0, 0, 0]));
        shm.set(5, 1);
        shm.wait(3, 1, 30)?;
        w.set_pad(pad_axes((1 << B_B) | (1 << B_Y), [-2000, 0, 0, 0, 0, 0, 0, 0]));
        w.set_pad(pad_axes(1 << B_B, [-2000, 0, 0, 0, 0, 0, 0, 0]));
        shm.wait(4, 1, 30)?;
        Ok(())
    });
    assert_eq!((wait_pid(rp), wait_pid(wb)), (0, 0));
}

#[test]
fn foreign_truncated_empty_and_missing_backings_stay_present_then_heal() {
    let sc = Scene::new("foreign");
    run("foreign", || {
        std::fs::write(sc.pad(0), b"NOT_OURS".repeat(512)).unwrap();
        let mut trunc = b"HERMESIO".to_vec();
        trunc.resize(100, 0);
        std::fs::write(sc.pad(1), &trunc).unwrap();
        std::fs::write(sc.pad(2), b"").unwrap();
        std::fs::create_dir_all(sc.pad(3)).unwrap();
        sc.enter();
        let mut rs = Vec::new();
        for s in 0..4 {
            let mut r = Rd::attach(s, sys::O_NONBLOCK)?;
            let f = r.frame(1000)?;
            ensure!(neutral_full(&f), "slot {}: neutral full state expected: {:?}", s, f);
            ensure!(r.read_nb() == Err(sys::EAGAIN), "slot {}: quiet", s);
            ensure!(hermes_is_removed(r.h) == 0 && hermes_has_pending(r.h) == 0, "slot {}", s);
            let st = r.state().map_err(|e| format!("state {}", e))?;
            ensure!(st.buttons.iter().all(|b| *b == 0) && st.axes.iter().all(|a| *a == 0), "neutral state");
            rs.push(r);
        }
        let mut w0 = sc.writer(0); // replaces the foreign file
        w0.open();
        w0.set_pad(pad(1 << B_A));
        rs[0].frame_where(3000, "slot 0 healed", |f| key(f, BTN_A) == Some(1))?;
        let mut w1 = sc.writer(1); // replaces the truncated one
        w1.open();
        w1.set_pad(pad(1 << B_X));
        rs[1].frame_where(3000, "slot 1 healed", |f| key(f, BTN_X) == Some(1))?;
        for r in &rs {
            ensure!(hermes_is_removed(r.h) == 0, "never removed");
        }
        Ok(())
    });
}

#[test]
fn forked_reader_gets_its_own_stream() {
    const T: u64 = 500;
    let sc = Scene::new("fork");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 taps requested, 2 taps done, 3 parent progress, 4 child progress,
    //      5 child has its baseline, 9 quit
    let wp = spawn("fork-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        let mut done = 0;
        let end = Instant::now() + Duration::from_secs(120);
        while shm.get(9) == 0 {
            ensure!(Instant::now() < end, "never told to quit");
            while done < shm.get(1) {
                w.set_pad(pad(1 << B_X));
                w.set_pad(pad(0));
                done += 1;
                shm.set(2, done);
            }
            std::thread::yield_now();
        }
        Ok(())
    });
    let rp = spawn("fork-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let mut r = Rd::attach(0, 0)?; // blocking fd: the library never blocks on it internally
        ensure!(is_full(&r.frame(1000)?), "full first");
        let child = unsafe { fork() };
        ensure!(child >= 0, "fork");
        if child == 0 {
            let res = (|| -> Result<(), String> {
                // same fd number, own readiness, own position: a full state comes first
                ensure!(r.poll(2000) & POLLIN != 0, "child fd readable after fork");
                let f = r.frame(2000)?;
                ensure!(is_full(&f), "child starts with a full-state frame: {:?}", f);
                // Taps published before that frame are part of its state by design, so the
                // parent only starts tapping once the child has its baseline.
                shm.set(5, 1);
                let mut t = Taps::new(BTN_X);
                let end = Instant::now() + Duration::from_secs(60);
                while t.releases < T {
                    if Instant::now() >= end {
                        let st = r.state();
                        return Err(format!(
                            "child stuck at {}; pending {} removed {} state {:?}; stats {:?}",
                            t.releases,
                            hermes_has_pending(r.h),
                            hermes_is_removed(r.h),
                            st.map(|s| (s.buttons, s.axes)),
                            stats()
                        ));
                    }
                    r.poll(100);
                    for f in r.drain()? {
                        t.feed(&f)?;
                    }
                    shm.set(4, t.releases);
                }
                ensure!(t.presses == T, "child presses {}", t.presses);
                Ok(())
            })();
            if let Err(e) = &res {
                say(&format!("[fork-child] FAILED: {}\n", e));
            }
            unsafe { _exit(if res.is_ok() { 0 } else { 1 }) }
        }
        shm.wait(5, 1, 30)?;
        let mut t = Taps::new(BTN_X);
        let mut issued = 0u64;
        let end = Instant::now() + Duration::from_secs(60);
        while t.releases < T {
            ensure!(
                Instant::now() < end,
                "parent stuck at {} (issued {}); stats {:?}",
                t.releases,
                issued,
                stats()
            );
            let slowest = t.releases.min(shm.get(4));
            if issued < T && issued < slowest + 16 {
                issued += 1;
                shm.set(1, issued);
            }
            r.poll(5);
            for f in r.drain()? {
                t.feed(&f)?;
            }
            shm.set(3, t.releases);
        }
        let code = wait_pid(child);
        shm.set(9, 1);
        ensure!(code == 0, "forked reader exited {}", code);
        ensure!(t.presses == T, "parent presses {}", t.presses);
        Ok(())
    });
    assert_eq!((wait_pid(rp), wait_pid(wp)), (0, 0));
}

#[test]
fn unplug_is_a_sticky_removal_with_pollhup_but_reset_is_not() {
    let sc = Scene::new("unplug");
    run("unplug", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        w.set_pad(pad(1 << B_A));
        r.frame_where(1000, "A", |f| key(f, BTN_A) == Some(1))?;
        w.reset(); // in-game menu neutralise / soft release: not an unplug
        r.frame_where(1000, "A released", |f| key(f, BTN_A) == Some(0))?;
        ensure!(hermes_is_removed(r.h) == 0, "reset is not a removal");
        w.set_pad(pad(1 << B_A));
        r.frame_where(1000, "A again", |f| key(f, BTN_A) == Some(1))?;
        w.unplug();
        let end = Instant::now() + Duration::from_secs(2);
        loop {
            let rev = r.poll(100);
            if rev & POLLHUP != 0 {
                break;
            }
            ensure!(Instant::now() < end, "no native POLLHUP after unplug (revents {:#x})", rev);
            let _ = r.read_nb();
        }
        ensure!(r.read_nb() == Err(sys::ENODEV), "read after unplug must be ENODEV");
        ensure!(hermes_is_removed(r.h) == 1, "removed");
        ensure!(hermes_has_pending(r.h) == -sys::ENODEV, "has_pending");
        ensure!(r.state().err() == Some(sys::ENODEV), "current_state");
        let rev = hermes_poll_revents(r.h, POLLIN);
        ensure!(rev & POLLHUP != 0 && rev & POLLERR != 0, "poll_revents {:#x}", rev);
        // a new controller on the same slot: the old handle stays removed, a fresh open works
        w.open();
        w.set_pad(pad(1 << B_B));
        std::thread::sleep(Duration::from_millis(50));
        ensure!(hermes_is_removed(r.h) == 1, "removal is sticky");
        let mut r2 = Rd::attach(0, sys::O_NONBLOCK)?;
        let f = r2.frame(1000)?;
        ensure!(is_full(&f) && key(&f, BTN_B) == Some(1) && key(&f, BTN_A) == Some(0), "fresh open sees the new pad: {:?}", f);
        ensure!(hermes_is_removed(r2.h) == 0, "fresh handle not removed");
        ensure!(hermes_detach(r.h) == 0 && hermes_detach(r2.h) == 0, "detach");
        Ok(())
    });
}

#[test]
fn native_poll_wakes_within_two_ms() {
    let sc = Scene::new("latency");
    run("latency", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        let mut lat = Vec::new();
        for i in 0..300 {
            r.drain()?;
            ensure!(r.poll(0) == 0, "readable with nothing pending (iteration {})", i);
            let t0 = Instant::now();
            w.set_pad(pad(if i % 2 == 0 { 1 << B_A } else { 0 }));
            ensure!(r.poll(1000) & POLLIN != 0, "no wake within 1 s (iteration {})", i);
            lat.push(t0.elapsed());
            r.frame(100)?;
        }
        lat.sort();
        let (p50, p99, max) = (lat[150], lat[296], lat[299]);
        say(&format!("[latency] publish->poll wake p50 {:?} p99 {:?} max {:?}\n", p50, p99, max));
        ensure!(p50 < Duration::from_millis(2), "median wake {:?} exceeds 2 ms", p50);
        ensure!(p99 < Duration::from_millis(50), "p99 wake {:?}", p99);
        Ok(())
    });
}

#[test]
fn blocking_read_wakes_on_input_and_fails_ebadf_on_concurrent_detach() {
    let sc = Scene::new("block");
    run("block", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, 0)?;
        r.frame(1000)?;
        let h = r.h;
        let t = std::thread::spawn(move || {
            let mut buf = [0u8; EVENT_SIZE * 8];
            let t0 = Instant::now();
            let n = unsafe { hermes_read(h, buf.as_mut_ptr() as *mut c_void, buf.len(), 0) };
            (n, t0.elapsed(), parse(&buf[..n.max(0) as usize]))
        });
        std::thread::sleep(Duration::from_millis(50));
        let t0 = Instant::now();
        w.set_pad(pad(1 << B_START));
        let (n, _, ev) = t.join().unwrap();
        ensure!(n > 0 && key(&ev, BTN_START) == Some(1), "blocking read returned {} {:?}", n, ev);
        ensure!(t0.elapsed() < Duration::from_millis(500), "slow wake {:?}", t0.elapsed());
        let t = std::thread::spawn(move || {
            let mut buf = [0u8; EVENT_SIZE * 8];
            let n = unsafe { hermes_read(h, buf.as_mut_ptr() as *mut c_void, buf.len(), 0) };
            n
        });
        std::thread::sleep(Duration::from_millis(50));
        let t0 = Instant::now();
        ensure!(hermes_detach(h) == 0, "detach");
        let n = t.join().unwrap();
        ensure!(n == -(sys::EBADF as isize), "blocked read after detach gave {}", n);
        ensure!(t0.elapsed() < Duration::from_millis(500), "EBADF took {:?}", t0.elapsed());
        Ok(())
    });
}

#[test]
fn session_release_goes_neutral_and_next_prepare_reattaches() {
    let sc = Scene::new("session");
    run("session", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        w.set_pad(pad(1 << B_A));
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(key(&r.frame(1000)?, BTN_A) == Some(1), "A held");
        w.release(); // session end
        r.frame_where(2000, "neutral after release", neutral_full)?;
        ensure!(hermes_is_removed(r.h) == 0, "release is not an unplug");
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?; // next session
        let f = r.frame_where(3000, "re-attach", |f| is_full(f))?;
        ensure!(key(&f, BTN_A) == Some(1), "the writer's state carried into the new file: {:?}", f);
        w.set_pad(pad(1 << B_B));
        let f = r.frame_where(1000, "B", |f| key(f, BTN_B) == Some(1))?;
        ensure!(key(&f, BTN_A) == Some(0) && !is_full(&f), "{:?}", f);
        let mut st = SlotStatsView::default();
        ensure!(unsafe { hermes_slot_stats(0, &mut st, core::mem::size_of::<SlotStatsView>()) } == 0, "slot stats");
        ensure!(st.publishes >= 2 && st.reader_reattaches >= 1 && st.retired == 0, "{:?}", st);
        Ok(())
    });
}

#[test]
fn current_state_is_never_torn() {
    let sc = Scene::new("torn");
    run("torn", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let r = Rd::attach(0, sys::O_NONBLOCK)?;
        let stop = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let s2 = stop.clone();
        let t = std::thread::spawn(move || {
            let mut v = 0i32;
            while !s2.load(Acquire) {
                v = (v + 1) % 30000;
                let b = if v % 2 == 1 { 1 << B_A } else { 0 };
                w.set_pad(pad_axes(b, [v, v, v, v, v & 255, v & 255, 0, 0]));
            }
        });
        for _ in 0..100_000 {
            let s = r.state().map_err(|e| format!("state {}", e))?;
            let v = s.axes[0];
            ensure!(s.axes[..4].iter().all(|a| *a == v), "torn sticks {:?}", s.axes);
            ensure!(s.axes[4] == v & 255 && s.axes[5] == v & 255, "torn triggers {:?}", s.axes);
            ensure!(s.buttons[B_A] == v & 1, "torn buttons {:?} vs {}", s.buttons, v);
        }
        stop.store(true, Release);
        t.join().unwrap();
        Ok(())
    });
}

#[test]
fn idle_pad_sleeps_and_a_raw_read_cannot_wedge_readiness() {
    let sc = Scene::new("idle");
    run("idle", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        // a program that reads the fd raw (it inherited it across exec, say) gets one byte, not an event
        let mut raw = [0u8; 24];
        let n = sys::read(r.fd, &mut raw).map_err(|e| format!("raw read {}", e))?;
        ensure!(n == 1, "raw read returned {} bytes", n);
        // the readiness flag heals by itself (the full state is still owed)
        let end = Instant::now() + Duration::from_secs(3);
        while r.poll(100) & POLLIN == 0 {
            ensure!(Instant::now() < end, "readiness never came back");
        }
        ensure!(is_full(&r.frame(100)?), "full state");
        let s0 = stats();
        std::thread::sleep(Duration::from_millis(400));
        let s1 = stats();
        let wakes = (s1.helper_wakes + s1.helper_timeouts) - (s0.helper_wakes + s0.helper_timeouts);
        say(&format!("[idle] helper wake-ups in 400 ms: {}\n", wakes));
        ensure!(wakes <= 400 / 4 + 40, "idle helper woke {} times in 400 ms", wakes);
        ensure!(s1.frames == s0.frames && r.poll(0) == 0, "idle pad delivered something");
        Ok(())
    });
}

// ---------------------------------------------------------------------------------------------
// axis pacing (HERMES_AXIS_HZ)

const T_DEFAULT: Duration = Duration::from_millis(8); // HERMES_AXIS_HZ=125

fn sleep_until(t: Instant) {
    let now = Instant::now();
    if t > now {
        std::thread::sleep(t - now);
    }
}

fn stick(i: u32) -> [i32; AXES] {
    let v = 1 + ((i as i32) * 37) % 30000;
    [v, -v, v / 2, 0, (i % 255) as i32 + 1, 0, 0, 0]
}

fn is_axis_only(f: &[E]) -> bool {
    !is_full(f) && !f.iter().any(|e| e.t == EV_KEY) && f.iter().any(|e| e.t == EV_ABS)
}

fn pct(v: &mut [u64], p: usize) -> u64 {
    v.sort_unstable();
    v[((v.len() - 1) * p) / 100]
}

#[test]
fn stick_only_frames_are_paced_to_axis_hz() {
    let sc = Scene::new("pace");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 writer done, 3 spam start (mono ns), 4 spam end, 5 reader done
    let wpid = spawn("pace-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let start = Instant::now();
        shm.set(3, sys::mono_ns());
        for i in 0..1000u32 {
            sleep_until(start + Duration::from_micros(2000 * i as u64)); // 500 Hz for 2 s
            w.set_pad(pad_axes(0, stick(i)));
        }
        shm.set(4, sys::mono_ns());
        shm.set(2, 1);
        shm.wait(5, 1, 30)?;
        Ok(())
    });
    let rpid = spawn("pace-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        std::env::set_var("HERMES_AXIS_HZ", "125"); // a fixed rate behaves as before auto mode
        ensure!(hermes_axis_hz() == 125 && hermes_axis_auto() == 0, "fixed rate {}", hermes_axis_hz());
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        shm.set(1, 1);
        let mut stamps = Vec::new();
        let mut quiet_since: Option<Instant> = None;
        let end = Instant::now() + Duration::from_secs(30);
        loop {
            ensure!(Instant::now() < end, "writer never finished");
            r.poll(20);
            match r.read_nb() {
                Ok(_) => quiet_since = None,
                Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            while let Some(f) = r.take_frame() {
                ensure!(is_axis_only(&f), "only stick frames expected: {:?}", f);
                stamps.push(sys::mono_ns());
            }
            if shm.get(2) == 1 {
                let q = *quiet_since.get_or_insert_with(Instant::now);
                if q.elapsed() > Duration::from_millis(60) {
                    break;
                }
            }
        }
        shm.set(5, 1);
        let (t0, t1) = (shm.get(3), shm.get(4));
        let inside = stamps.iter().filter(|t| **t >= t0 && **t <= t1).count();
        let secs = (t1 - t0) as f64 / 1e9;
        let rate = inside as f64 / secs;
        let s = stats();
        let mut st = SlotStatsView::default();
        unsafe { hermes_slot_stats(0, &mut st, core::mem::size_of::<SlotStatsView>()) };
        say(&format!(
            "[pace] 1000 publishes in {:.3} s -> {} stick frames ({:.1}/s); paced {} merged {}; file: paced {} merged {}\n",
            secs, inside, rate, s.paced_frames, s.merged_axis_publishes, st.reader_paced_frames, st.reader_merged_axis_publishes
        ));
        ensure!(rate <= 125.0 * 1.1, "{:.1} stick frames/s exceeds 125 Hz + 10 %", rate);
        ensure!(rate >= 125.0 * 0.75, "{:.1} stick frames/s: pacing starved the reader", rate);
        ensure!(s.paced_frames > 0 && s.merged_axis_publishes > 0, "pacing counters {:?}", s);
        ensure!(st.reader_paced_frames > 0 && st.reader_merged_axis_publishes > 0, "file counters {:?}", st);
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn rest_value_reaches_a_reader_blocked_in_poll_within_t() {
    let sc = Scene::new("rest");
    run("rest", || {
        sc.enter();
        std::env::set_var("HERMES_AXIS_HZ", "125");
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        let (h, fd) = (r.h, r.fd);
        let rest_at = std::sync::Arc::new(AtomicU64::new(0));
        let ra = rest_at.clone();
        // The reader only ever sleeps in a plain poll(-1): nothing but the fd's own readiness can
        // wake it, so a held stick frame must be signalled by the library at its deadline.
        let reader = std::thread::spawn(move || -> Result<(), String> {
            let mut x = 0i32;
            let mut buf = [0u8; EVENT_SIZE * 64];
            loop {
                let mut p = PollFd { fd, events: POLLIN, revents: 0 };
                sys::poll(&mut p, 1, -1).map_err(|e| format!("poll errno {}", e))?;
                let n = unsafe { hermes_read(h, buf.as_mut_ptr() as *mut c_void, buf.len(), 1) };
                if n == -(sys::EAGAIN as isize) {
                    continue;
                }
                ensure!(n > 0, "read {}", n);
                for e in parse(&buf[..n as usize]) {
                    if e.t == EV_KEY && e.c == BTN_START && e.v == 1 {
                        return Ok(());
                    }
                    if e.t == EV_ABS && e.c == ABS_X {
                        if e.v == 0 && x != 0 {
                            ra.store(sys::mono_ns(), SeqCst);
                        }
                        x = e.v;
                    }
                }
            }
        });
        let mut lat = Vec::new();
        for trial in 0..20u32 {
            rest_at.store(0, SeqCst);
            let start = Instant::now();
            for k in 0..25u32 {
                sleep_until(start + Duration::from_micros(2000 * k as u64)); // 500 Hz, 50 ms
                w.set_pad(pad_axes(0, stick(trial * 100 + k)));
            }
            sleep_until(start + Duration::from_micros(2000 * 25 + 700 * (trial % 4) as u64));
            let t_last = sys::mono_ns();
            w.set_pad(pad(0)); // let go: the rest position, then silence
            let end = Instant::now() + Duration::from_millis(500);
            while rest_at.load(SeqCst) == 0 {
                ensure!(Instant::now() < end, "trial {}: the rest value never arrived", trial);
                std::thread::sleep(Duration::from_micros(200));
            }
            lat.push(rest_at.load(SeqCst) - t_last);
            std::thread::sleep(Duration::from_millis(30));
        }
        w.set_pad(pad(1 << B_START));
        reader.join().map_err(|_| "reader panicked".to_string())??;
        let worst = *lat.iter().max().unwrap();
        let p95 = pct(&mut lat, 95);
        let p50 = pct(&mut lat, 50);
        say(&format!("[rest] last publish -> rest value in a poll(-1) reader: p50 {} us p95 {} us max {} us (T = 8 ms)\n", p50 / 1000, p95 / 1000, worst / 1000));
        let bound = (T_DEFAULT + Duration::from_millis(2)).as_nanos() as u64;
        ensure!(p95 <= bound, "p95 {} us exceeds T + 2 ms", p95 / 1000);
        ensure!(worst <= bound + 8_000_000, "worst {} us", worst / 1000);
        Ok(())
    });
}

#[test]
fn taps_go_out_at_once_and_in_order_during_paced_stick_spam() {
    let sc = Scene::new("tapspam");
    run("tapspam", || {
        sc.enter();
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        let edges = std::sync::Arc::new(std::sync::Mutex::new(Vec::<(u64, i32)>::new()));
        let done = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let (we, wd) = (edges.clone(), done.clone());
        let writer = std::thread::spawn(move || {
            let start = Instant::now();
            let mut down = false;
            for i in 0..750u32 {
                sleep_until(start + Duration::from_micros(2000 * i as u64)); // 500 Hz, 1.5 s
                let press = match i % 25 {
                    0 => true,
                    12 => false,
                    _ => down,
                };
                if press != down {
                    we.lock().unwrap().push((sys::mono_ns(), press as i32));
                    down = press;
                }
                w.set_pad(pad_axes(if down { 1 << B_A } else { 0 }, stick(i)));
            }
            if down {
                we.lock().unwrap().push((sys::mono_ns(), 0));
                w.set_pad(pad_axes(0, stick(9999)));
            }
            wd.store(true, SeqCst);
        });
        let mut got: Vec<(u64, i32)> = Vec::new();
        let mut t = Taps::new(BTN_A);
        let end = Instant::now() + Duration::from_secs(30);
        loop {
            ensure!(Instant::now() < end, "stuck");
            r.poll(20);
            match r.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            let now = sys::mono_ns();
            while let Some(f) = r.take_frame() {
                t.feed(&f)?;
                if let Some(v) = key(&f, BTN_A) {
                    got.push((now, v));
                }
            }
            if done.load(SeqCst) && got.len() >= edges.lock().unwrap().len() {
                break;
            }
        }
        writer.join().unwrap();
        let sent = edges.lock().unwrap().clone();
        ensure!(got.len() == sent.len(), "{} edges sent, {} received", sent.len(), got.len());
        ensure!(got.iter().map(|g| g.1).eq(sent.iter().map(|s| s.1)), "edges out of order");
        let mut lat: Vec<u64> = got.iter().zip(&sent).map(|(g, s)| g.0.saturating_sub(s.0)).collect();
        let worst = *lat.iter().max().unwrap();
        let p95 = pct(&mut lat, 95);
        let p50 = pct(&mut lat, 50);
        say(&format!("[tapspam] {} button edges under 500 Hz stick spam: p50 {} us p95 {} us max {} us\n", sent.len(), p50 / 1000, p95 / 1000, worst / 1000));
        ensure!(p95 <= 1_000_000, "button edge p95 {} us exceeds 1 ms", p95 / 1000);
        ensure!(worst <= 10_000_000, "button edge worst {} us", worst / 1000);
        Ok(())
    });
}

#[test]
fn axis_hz_zero_restores_unpaced_stick_frames() {
    let sc = Scene::new("unpaced");
    run("unpaced", || {
        sc.enter();
        std::env::set_var("HERMES_AXIS_HZ", "0");
        ensure!(hermes_axis_hz() == 0, "rate {}", hermes_axis_hz());
        let mut w = sc.writer(0);
        w.open();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        let done = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let wd = done.clone();
        let writer = std::thread::spawn(move || {
            let start = Instant::now();
            for i in 0..500u32 {
                sleep_until(start + Duration::from_micros(2000 * i as u64)); // 500 Hz, 1 s
                w.set_pad(pad_axes(0, stick(i)));
            }
            wd.store(true, SeqCst);
        });
        let mut frames = 0u64;
        let end = Instant::now() + Duration::from_secs(30);
        while !done.load(SeqCst) {
            ensure!(Instant::now() < end, "stuck");
            r.poll(20);
            match r.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            while let Some(f) = r.take_frame() {
                ensure!(is_axis_only(&f), "{:?}", f);
                frames += 1;
            }
        }
        writer.join().unwrap();
        frames += r.drain()?.len() as u64;
        let s = stats();
        say(&format!("[unpaced] 500 publishes at 500 Hz -> {} stick frames, paced {}\n", frames, s.paced_frames));
        ensure!(frames >= 350, "only {} frames: HERMES_AXIS_HZ=0 must not pace", frames);
        ensure!(s.paced_frames == 0, "paced {}", s.paced_frames);
        Ok(())
    });
}

// ---------------------------------------------------------------------------------------------
// auto pacing: frame credits, one stick-only frame per game frame (HERMES_AXIS_HZ unset)

/// What an auto-paced reader saw (all CLOCK_MONOTONIC ns, shared by both processes).
struct AutoRun {
    start: u64,
    sticks: Vec<u64>,
    edges: Vec<(u64, i32)>,
    sent_edges: Vec<(u64, i32)>,
    frames: Vec<u64>,
    first_publish: u64,
    rest_sent: u64,
    rest_seen: u64,
    stats: StatsView,
    slot: SlotStatsView,
}

const MS_NS: u64 = 1_000_000;

impl AutoRun {
    fn window(&self, from_ms: u64, to_ms: u64) -> (u64, u64) {
        (self.start + from_ms * MS_NS, self.start + to_ms * MS_NS)
    }
    /// Stick-only frames per second that arrived in [start + from_ms, start + to_ms).
    fn rate(&self, from_ms: u64, to_ms: u64) -> f64 {
        let (a, b) = self.window(from_ms, to_ms);
        let n = self.sticks.iter().filter(|t| **t >= a && **t < b).count();
        n as f64 * 1000.0 / (to_ms - from_ms) as f64
    }
    fn sticks_in(&self, from_ms: u64, to_ms: u64) -> usize {
        let (a, b) = self.window(from_ms, to_ms);
        self.sticks.iter().filter(|t| **t >= a && **t < b).count()
    }
    fn frames_in(&self, from_ms: u64, to_ms: u64) -> usize {
        let (a, b) = self.window(from_ms, to_ms);
        self.frames.iter().filter(|t| **t >= a && **t < b).count()
    }
    /// Smallest gap between consecutive stick frames inside the window.
    fn min_gap_ms(&self, from_ms: u64, to_ms: u64) -> f64 {
        let (a, b) = self.window(from_ms, to_ms);
        let w: Vec<u64> = self.sticks.iter().copied().filter(|t| *t >= a && *t < b).collect();
        w.windows(2).map(|p| (p[1] - p[0]) as f64 / 1e6).fold(f64::MAX, f64::min)
    }
}

/// One auto-pacing scenario: the game "presents" at each phase's fps (0 = frozen / no feed)
/// through the real frame feed (`writer::game_frame`); the pad publishes stick motion at 500 Hz
/// during `moving` (ms from the start), with a tap of A every 100 ms if `taps`; then the rest
/// position (at `rest_ms`, or as the last publish).
struct Script {
    phases: &'static [(u32, u64)],
    moving: (u64, u64),
    taps: bool,
    rest_ms: Option<u64>,
    env: &'static [(&'static str, &'static str)],
}

/// Writer process plays `script`; reader process (auto mode) records arrivals, then runs `check`.
fn auto_run<C: FnOnce(&AutoRun) -> Result<(), String>>(tag: &'static str, script: Script, check: C) {
    let sc = Scene::new(tag);
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 writer done, 3 start, 4 rest sent, 5 reader done,
    //      6 edges sent, 7 first stick publish, 8 frames fed, 100 + 2k / 101 + 2k edge k,
    //      290 + k game frame k
    let wpid = spawn("auto-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let start = Instant::now();
        shm.set(3, sys::mono_ns());
        let phases = script.phases;
        let fshm = Shm(shm.0);
        let feeder = std::thread::spawn(move || {
            let mut t0 = start;
            let mut k = 0usize;
            for &(fps, ms) in phases {
                let end = t0 + Duration::from_millis(ms);
                if fps > 0 {
                    let step = Duration::from_nanos(1_000_000_000 / fps as u64);
                    let mut next = t0;
                    while next < end {
                        sleep_until(next);
                        let now = sys::mono_ns();
                        hermes_core::writer::game_frame(now);
                        if k < 220 {
                            fshm.set(290 + k, now);
                        }
                        k += 1;
                        fshm.set(8, k.min(220) as u64);
                        next += step;
                    }
                }
                sleep_until(end);
                t0 = end;
            }
        });
        let edge = |v: bool, nedges: &mut usize| {
            if *nedges < 90 {
                shm.set(100 + 2 * *nedges, sys::mono_ns());
                shm.set(101 + 2 * *nedges, v as u64);
                *nedges += 1;
                shm.set(6, *nedges as u64);
            }
        };
        let (from, to) = script.moving;
        let mut down = false;
        let mut nedges = 0usize;
        let mut i = (from / 2) as u32;
        while (i as u64) * 2 < to {
            sleep_until(start + Duration::from_micros(2000 * i as u64));
            if script.taps {
                let want = i % 50 < 10;
                if want != down {
                    edge(want, &mut nedges);
                    down = want;
                }
            }
            if shm.get(7) == 0 {
                shm.set(7, sys::mono_ns());
            }
            w.set_pad(pad_axes(if down { 1 << B_A } else { 0 }, stick(i)));
            i += 1;
        }
        if down {
            edge(false, &mut nedges);
            w.set_pad(pad_axes(0, stick(i)));
        }
        if let Some(ms) = script.rest_ms {
            sleep_until(start + Duration::from_millis(ms));
        }
        shm.set(4, sys::mono_ns());
        w.set_pad(pad(0)); // let go
        shm.set(2, 1);
        shm.wait(5, 1, 60)?;
        feeder.join().map_err(|_| "feeder panicked".to_string())?;
        Ok(())
    });
    let rpid = spawn("auto-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter(); // HERMES_AXIS_HZ unset: frame credits
        for (k, v) in script.env {
            std::env::set_var(k, v);
        }
        ensure!(hermes_axis_auto() == 1, "frame credits are the default");
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        shm.set(1, 1);
        let mut run = AutoRun {
            start: 0,
            sticks: Vec::new(),
            edges: Vec::new(),
            sent_edges: Vec::new(),
            frames: Vec::new(),
            first_publish: 0,
            rest_sent: 0,
            rest_seen: 0,
            stats: StatsView::default(),
            slot: SlotStatsView::default(),
        };
        let mut x = 1;
        let end = Instant::now() + Duration::from_secs(60);
        while !(shm.get(2) == 1 && run.rest_seen != 0) {
            ensure!(Instant::now() < end, "never saw the rest position");
            r.poll(20);
            match r.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            let now = sys::mono_ns();
            while let Some(f) = r.take_frame() {
                if let Some(v) = key(&f, BTN_A) {
                    run.edges.push((now, v));
                } else if is_axis_only(&f) {
                    run.sticks.push(now);
                }
                if let Some(v) = abs(&f, ABS_X) {
                    if v == 0 && x != 0 && shm.get(4) != 0 && run.rest_seen == 0 {
                        run.rest_seen = now;
                    }
                    x = v;
                }
            }
        }
        unsafe { hermes_slot_stats(0, &mut run.slot, core::mem::size_of::<SlotStatsView>()) };
        run.stats = stats();
        shm.set(5, 1);
        run.start = shm.get(3);
        run.first_publish = shm.get(7);
        run.rest_sent = shm.get(4);
        for k in 0..shm.get(6) as usize {
            run.sent_edges.push((shm.get(100 + 2 * k), shm.get(101 + 2 * k) as i32));
        }
        for k in 0..shm.get(8) as usize {
            run.frames.push(shm.get(290 + k));
        }
        check(&run)
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0), "auto pacing scenario '{}'", tag);
}

#[test]
fn credits_send_one_stick_frame_per_game_frame_at_60_fps() {
    let s = Script { phases: &[(60, 2400)], moving: (0, 2200), taps: false, rest_ms: None, env: &[] };
    auto_run("cred60", s, |r| {
        let rate = r.rate(400, 2200);
        let (sticks, frames) = (r.sticks_in(400, 2200), r.frames_in(400, 2200));
        let rest = (r.rest_seen - r.rest_sent) as f64 / 1e6;
        say(&format!(
            "[cred60] {:.1} stick frames/s; {} stick frames for {} game frames; mode {} at {} Hz; frame count {}, last interval {:.2} ms; rest after {:.1} ms\n",
            rate, sticks, frames, r.slot.reader_axis_mode, r.stats.axis_hz, r.slot.frame_count,
            r.slot.frame_interval_ns as f64 / 1e6, rest
        ));
        ensure!(sticks <= frames + 1, "{} stick frames for {} game frames", sticks, frames);
        ensure!((52.0..=62.0).contains(&rate), "{:.1}/s at 60 fps, want about 60", rate);
        // at exactly 60 fps either the frames or the 60/s ceiling may be the tighter limit
        ensure!(matches!(r.stats.axis_mode, 1 | 5) && matches!(r.slot.reader_axis_mode, 1 | 5), "mode {} / {}", r.stats.axis_mode, r.slot.reader_axis_mode);
        ensure!(r.slot.frame_count >= 100, "frame count {}", r.slot.frame_count);
        ensure!(rest <= 25.0, "rest position {:.1} ms after the last publish", rest);
        Ok(())
    });
}

#[test]
fn credits_have_no_ramp_after_an_idle_app_speeds_up() {
    // The app idles at 5 fps (it redraws on change), then the stick moves and it speeds to 100 fps:
    // the first stick frame goes at once, then the 60/s ceiling governs.
    let s = Script { phases: &[(5, 1000), (100, 1200)], moving: (1000, 2200), taps: false, rest_ms: None, env: &[] };
    auto_run("credramp", s, |r| {
        let first = r.sticks.iter().copied().find(|t| *t >= r.first_publish).unwrap_or(0);
        let lat = first.saturating_sub(r.first_publish) as f64 / 1e6;
        let second = r.sticks.iter().copied().find(|t| *t > first).unwrap_or(0);
        let gap2 = second.saturating_sub(first) as f64 / 1e6;
        let early = r.rate(1050, 1250);
        let later = r.rate(1050, 2200);
        let gap = r.min_gap_ms(1050, 2200);
        say(&format!("[credramp] first stick frame {:.3} ms after the first publish, next {:.1} ms later; first 200 ms {:.1}/s, then {:.1}/s, min gap {:.1} ms (game 100 fps, ceiling 60)\n", lat, gap2, early, later, gap));
        ensure!(first != 0 && lat <= 1.0, "first stick frame {:.3} ms after the first publish", lat);
        ensure!(gap2 <= 18.0, "the second stick frame came {:.1} ms later (the ceiling is 16.7 ms)", gap2);
        ensure!(early >= 50.0, "a ramp: {:.1}/s in the first 200 ms", early);
        ensure!((55.0..=62.0).contains(&later), "{:.1}/s at 100 fps under a 60/s ceiling", later);
        ensure!(gap >= 15.5, "stick frames {:.1} ms apart under a 16.7 ms ceiling", gap);
        ensure!(r.stats.axis_mode == 5, "mode {} (want 5, the ceiling)", r.stats.axis_mode);
        Ok(())
    });
}

#[test]
fn credits_follow_a_dip_to_30_fps_immediately() {
    let s = Script { phases: &[(160, 1000), (30, 1200)], moving: (0, 2200), taps: false, rest_ms: None, env: &[] };
    auto_run("creddip", s, |r| {
        let before = r.rate(300, 1000);
        // from the first 30 fps frame after the dip on (its predecessor's credit may still be
        // spent under the ceiling just after the dip)
        let after = r.rate(1040, 2200);
        let gap = r.min_gap_ms(1040, 2200);
        let (sticks, frames) = (r.sticks_in(1040, 2200), r.frames_in(1040, 2200));
        say(&format!("[creddip] 160 fps: {:.1}/s (ceiling 60); after the dip to 30 fps: {:.1}/s, min gap {:.1} ms, {} stick frames for {} game frames\n", before, after, gap, sticks, frames));
        ensure!((55.0..=62.0).contains(&before), "before the dip {:.1}/s", before);
        ensure!(sticks <= frames + 1, "{} stick frames for {} game frames", sticks, frames);
        ensure!((26.0..=32.0).contains(&after), "after the dip {:.1}/s, want about 30", after);
        ensure!(gap >= 28.0, "stick frames {:.1} ms apart after the dip (a game frame is 33 ms)", gap);
        Ok(())
    });
}

#[test]
fn credits_are_capped_at_60_per_second_for_a_160_fps_game() {
    let s = Script { phases: &[(160, 2400)], moving: (0, 2200), taps: false, rest_ms: None, env: &[] };
    auto_run("cred160", s, |r| {
        let rate = r.rate(400, 2200);
        let gap = r.min_gap_ms(400, 2200);
        let rest = (r.rest_seen - r.rest_sent) as f64 / 1e6;
        say(&format!("[cred160] 160 fps game: {:.1} stick frames/s, min gap {:.1} ms; mode {} at {} Hz, ceiling {} Hz; rest after {:.1} ms\n",
            rate, gap, r.slot.reader_axis_mode, r.slot.reader_axis_hz, r.slot.reader_auto_max, rest));
        ensure!((55.0..=62.0).contains(&rate), "{:.1}/s for a 160 fps game, want the 60/s ceiling", rate);
        ensure!(gap >= 15.5, "stick frames {:.1} ms apart under a 16.7 ms ceiling", gap);
        ensure!(r.slot.reader_axis_mode == 5 && r.slot.reader_axis_hz == 60 && r.slot.reader_auto_max == 60,
            "mode {} hz {} max {}", r.slot.reader_axis_mode, r.slot.reader_axis_hz, r.slot.reader_auto_max);
        ensure!(rest <= 20.0, "rest position {:.1} ms after the last publish (one ceiling interval is 16.7 ms)", rest);
        Ok(())
    });
}

#[test]
fn credits_govern_below_the_ceiling_at_40_fps() {
    let s = Script { phases: &[(40, 2200)], moving: (0, 2200), taps: false, rest_ms: None, env: &[] };
    auto_run("cred40", s, |r| {
        let rate = r.rate(400, 2200);
        let (sticks, frames) = (r.sticks_in(400, 2200), r.frames_in(400, 2200));
        say(&format!("[cred40] 40 fps game: {:.1} stick frames/s, {} stick frames for {} game frames, mode {}\n", rate, sticks, frames, r.slot.reader_axis_mode));
        ensure!((37.0..=41.0).contains(&rate), "{:.1}/s at 40 fps, want 40", rate);
        ensure!(sticks <= frames + 1, "{} stick frames for {} game frames", sticks, frames);
        ensure!(r.slot.reader_axis_mode == 1, "mode {} (want 1, credits)", r.slot.reader_axis_mode);
        Ok(())
    });
}

#[test]
fn the_auto_ceiling_is_tunable() {
    let s = Script { phases: &[(160, 2200)], moving: (0, 2200), taps: false, rest_ms: None, env: &[("HERMES_AXIS_AUTO_MAX", "120")] };
    auto_run("credmax120", s, |r| {
        let rate = r.rate(400, 2200);
        say(&format!("[credmax120] HERMES_AXIS_AUTO_MAX=120, 160 fps game: {:.1} stick frames/s, mode {} at {} Hz\n", rate, r.slot.reader_axis_mode, r.slot.reader_axis_hz));
        ensure!((108.0..=122.0).contains(&rate), "{:.1}/s, want about 120", rate);
        ensure!(r.slot.reader_auto_max == 120 && r.slot.reader_axis_mode == 5, "max {} mode {}", r.slot.reader_auto_max, r.slot.reader_axis_mode);
        Ok(())
    });
}

#[test]
fn credits_still_deliver_to_a_frozen_game_within_55_ms() {
    // The game presents its last frame at ~983 ms and then freezes (not yet stale). The stick keeps
    // moving until 990 ms, so the change after that last frame has no credit and is held; it is let
    // go (the rest position) at 1000 ms. The held change must still arrive within the 50 ms bound.
    let s = Script { phases: &[(60, 990), (0, 700)], moving: (0, 990), taps: false, rest_ms: Some(1000), env: &[] };
    auto_run("credfrozen", s, |r| {
        let last_frame = r.frames.last().copied().unwrap_or(0);
        let held_from = last_frame; // the first change after it waits for a credit
        let rest = (r.rest_seen - r.rest_sent) as f64 / 1e6;
        let since_frame = r.rest_seen.saturating_sub(held_from) as f64 / 1e6;
        say(&format!("[credfrozen] game frozen after its frame at +{:.1} ms: the held change (rest position) arrived {:.1} ms after the last publish, {:.1} ms after the last frame, mode {}\n",
            (last_frame - r.start) as f64 / 1e6, rest, since_frame, r.stats.axis_mode));
        ensure!(rest >= 20.0, "the rest position went out after {:.1} ms: it was not held, the stall path did not run", rest);
        // held at the latest from the rest publish on; the stall bound is 50 ms
        ensure!(rest <= 55.0, "a held change waited {:.1} ms for a frozen game", rest);
        ensure!(r.stats.paced_frames >= 1, "no held frame counted");
        Ok(())
    });
}

#[test]
fn credits_fall_back_to_60_hz_without_a_fresh_feed() {
    let s = Script { phases: &[(120, 800), (0, 1600)], moving: (0, 2400), taps: false, rest_ms: None, env: &[] };
    auto_run("credstale", s, |r| {
        let fed = r.rate(300, 800);
        let stale = r.rate(1150, 2400);
        say(&format!("[credstale] fed 120 fps (ceiling 60): {:.1}/s; feed stopped: {:.1}/s (want 60); fallbacks {}, mode {}\n", fed, stale, r.stats.auto_fallbacks, r.stats.axis_mode));
        ensure!((55.0..=62.0).contains(&fed), "with the feed (120 fps, ceiling 60) {:.1}/s", fed);
        ensure!((54.0..=66.0).contains(&stale), "without a fresh feed {:.1}/s, want 60", stale);
        ensure!(r.stats.auto_fallbacks >= 1 && r.slot.reader_auto_fallbacks >= 1, "fallbacks {} / {}", r.stats.auto_fallbacks, r.slot.reader_auto_fallbacks);
        ensure!(r.stats.axis_mode == 2 && r.slot.reader_axis_mode == 2, "mode {} / {}", r.stats.axis_mode, r.slot.reader_axis_mode);
        Ok(())
    });
}

#[test]
fn credits_keep_button_edges_immediate_and_in_order() {
    let s = Script { phases: &[(30, 1000), (0, 600), (120, 600)], moving: (0, 2200), taps: true, rest_ms: None, env: &[] };
    auto_run("credtaps", s, |r| {
        ensure!(!r.sent_edges.is_empty(), "no taps sent");
        ensure!(r.edges.len() == r.sent_edges.len(), "{} edges sent, {} received", r.sent_edges.len(), r.edges.len());
        ensure!(r.edges.iter().map(|e| e.1).eq(r.sent_edges.iter().map(|e| e.1)), "edges out of order");
        let mut lat: Vec<u64> = r.edges.iter().zip(&r.sent_edges).map(|(g, s)| g.0.saturating_sub(s.0)).collect();
        let worst = *lat.iter().max().unwrap();
        let p95 = pct(&mut lat, 95);
        say(&format!("[credtaps] {} edges at 30 fps / no feed / 120 fps: p95 {} us max {} us\n", r.edges.len(), p95 / 1000, worst / 1000));
        ensure!(p95 <= 1_000_000, "button edge p95 {} us", p95 / 1000);
        ensure!(worst <= 10_000_000, "button edge worst {} us", worst / 1000);
        Ok(())
    });
}

#[test]
fn the_frame_feed_rings_only_while_a_reader_waits_for_a_frame() {
    let sc = Scene::new("credwake");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 wakes after the idle second, 3 wakes after the
    //      moving second, 4 frames, 5 reader done
    let wpid = spawn("wake-writer", || {
        let mut w = sc.writer(0);
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let start = Instant::now();
        let feeder = std::thread::spawn(move || {
            for k in 0..80u64 {
                sleep_until(start + Duration::from_millis(25 * k)); // 40 fps for 2 s (below the ceiling)
                hermes_core::writer::game_frame(sys::mono_ns());
            }
        });
        // first second: the game runs, the pad is idle
        sleep_until(start + Duration::from_millis(1000));
        shm.set(2, hermes_core::writer::frame_wakes() + 1);
        // second second: the stick moves at 500 Hz, so readers wait for frame credits
        for i in 0..500u32 {
            sleep_until(start + Duration::from_millis(1000) + Duration::from_micros(2000 * i as u64));
            w.set_pad(pad_axes(0, stick(i)));
        }
        feeder.join().map_err(|_| "feeder panicked".to_string())?;
        shm.set(3, hermes_core::writer::frame_wakes() + 1);
        shm.set(4, w.stat(hermes_core::writer::STAT_GAME_FRAMES));
        w.set_pad(pad(0));
        shm.wait(5, 1, 30)?;
        Ok(())
    });
    let rpid = spawn("wake-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        r.frame(1000)?;
        shm.set(1, 1);
        let end = Instant::now() + Duration::from_secs(30);
        while shm.get(3) == 0 {
            ensure!(Instant::now() < end, "writer never finished");
            r.poll(20);
            let _ = r.drain()?;
        }
        shm.set(5, 1);
        let (idle, moving, frames) = (shm.get(2) - 1, shm.get(3) - 1, shm.get(4));
        say(&format!("[credwake] {} game frames; frame wakes: {} while the pad was idle, {} more while the stick moved\n", frames, idle, moving - idle));
        ensure!(idle == 0, "{} futex wakes for frames while no reader waited", idle);
        ensure!(moving > idle, "no frame ever woke a waiting reader");
        ensure!(moving - idle <= 40, "{} wakes for 40 frames", moving - idle);
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn idle_slots_barely_wake_and_a_publish_at_the_idle_edge_is_not_lost() {
    let sc = Scene::new("idlewake");
    run("idlewake", || {
        sc.enter();
        let mut w0 = sc.writer(0);
        w0.open();
        let mut w1 = sc.writer(1);
        w1.open();
        let mut r0 = Rd::attach(0, sys::O_NONBLOCK)?;
        r0.frame(1000)?;
        // slot 1 was opened once and closed again: its wake thread has no handle left
        let r1 = Rd::attach(1, sys::O_NONBLOCK)?;
        ensure!(hermes_detach(r1.h) == 0, "detach");
        let slot_wakes = |slot: i32| -> u64 {
            let mut v = SlotStatsView::default();
            unsafe { hermes_slot_stats(slot, &mut v, core::mem::size_of::<SlotStatsView>()) };
            v.reader_helper_wakes
        };
        std::thread::sleep(Duration::from_millis(700)); // past the active window
        let (s0, a0, b0) = (stats(), slot_wakes(0), slot_wakes(1));
        std::thread::sleep(Duration::from_secs(5));
        let (s1, a1, b1) = (stats(), slot_wakes(0), slot_wakes(1));
        let total = (s1.helper_wakes + s1.helper_timeouts) - (s0.helper_wakes + s0.helper_timeouts);
        say(&format!(
            "[idlewake] 5 s without input: wake-thread wake-ups {} in the process, slot 0 (open) {}, slot 1 (closed) {}\n",
            total, a1 - a0, b1 - b0
        ));
        ensure!(a1 - a0 <= 12, "an idle open slot woke {} times in 5 s", a1 - a0);
        ensure!(b1 - b0 == 0, "a slot with no handle woke {} times", b1 - b0);
        ensure!(total <= 12, "{} wake-ups in 5 s", total);
        ensure!(s1.frames == s0.frames, "an idle pad delivered something");
        // A publish landing right as the wake thread turns idle (250 ms after the last activity)
        // must still be signalled at once: sweep the publish across that edge.
        let mut lat = Vec::new();
        for k in 0..40u64 {
            r0.drain()?;
            let t0 = Instant::now();
            w0.set_pad(pad_axes(if k % 2 == 0 { 1 << B_A } else { 0 }, [k as i32 * 100, 0, 0, 0, 0, 0, 0, 0]));
            ensure!(r0.poll(1000) & POLLIN != 0, "publish {} never signalled", k);
            lat.push(t0.elapsed());
            r0.frame(100)?;
            std::thread::sleep(Duration::from_micros(245_000 + 250 * k)); // 245 .. 255 ms
        }
        lat.sort();
        let (p50, worst) = (lat[20], lat[39]);
        say(&format!("[idlewake] publishes across the idle edge: p50 {:?} max {:?}\n", p50, worst));
        ensure!(p50 < Duration::from_millis(2), "p50 {:?}", p50);
        ensure!(worst < Duration::from_millis(20), "a publish at the idle edge took {:?}", worst);
        Ok(())
    });
}

// ---------------------------------------------------------------------------------------------
// control block (format 1.5): live pacing and controller identity

use hermes_core::layout::{
    Identity, Shared, CTL_AXIS_FIXED, CTL_AXIS_UNPACED, CTL_AXIS_UNSET, FILE_SIZE, ID_KEY_LEN, ID_NAME_LEN,
};

const NO_ID: IdentityView =
    IdentityView { gen: 0, bustype: 0, vendor: 0, product: 0, version: 0, key: [0; ID_KEY_LEN], name: [0; ID_NAME_LEN] };

fn ident(h: u32) -> Result<IdentityView, i32> {
    let mut v = NO_ID;
    let r = unsafe { hermes_identity(h, &mut v, core::mem::size_of::<IdentityView>()) };
    if r < 0 {
        Err(-r)
    } else {
        Ok(v)
    }
}

fn slot_ident(slot: i32) -> Result<IdentityView, i32> {
    let mut v = NO_ID;
    let r = unsafe { hermes_slot_identity(slot, &mut v, core::mem::size_of::<IdentityView>()) };
    if r < 0 {
        Err(-r)
    } else {
        Ok(v)
    }
}

fn text(b: &[u8]) -> Result<String, String> {
    let n = b.iter().position(|c| *c == 0).ok_or("string field not NUL-terminated")?;
    String::from_utf8(b[..n].to_vec()).map_err(|_| format!("not UTF-8: {:?}", &b[..n]))
}

fn id_name(v: &IdentityView) -> String {
    text(&v.name).unwrap_or_else(|e| e)
}

fn slot_view(slot: i32) -> SlotStatsView {
    let mut st = SlotStatsView::default();
    unsafe { hermes_slot_stats(slot, &mut st, core::mem::size_of::<SlotStatsView>()) };
    st
}

/// Map a slot file read-write (to play an older or damaged writer in a test).
fn map_shared(path: &std::path::Path) -> &'static Shared {
    let mut p = sys::PathBuf::new();
    assert!(p.push(path.to_str().unwrap().as_bytes()));
    let fd = sys::openat(p.as_ptr(), sys::O_RDWR | sys::O_CLOEXEC, 0).unwrap();
    let m = sys::mmap(FILE_SIZE, sys::PROT_READ | sys::PROT_WRITE, sys::MAP_SHARED, fd).unwrap();
    sys::close(fd);
    unsafe { Shared::at(m) }
}

#[test]
fn identity_is_taken_at_attach_and_never_changes_under_an_open_handle() {
    let sc = Scene::new("ident");
    let shm = Shm::new();
    // shm: 0 writer ready (identity A), 1 reader holds h1, 2 identity B set, 3 reader checked B,
    //      4 file recreated, 5 reader saw the re-attach, 6 identity cleared, 7 reader done
    let xbox = || Identity::new(3, 0x045e, 0x028e, 0x0114, b"xbox360", b"Xbox 360 Controller (0)");
    let ds = || Identity::new(3, 0x054c, 0x0ce6, 0x8111, b"ds5", "DualSense Wireless Controller \u{2013} P1".as_bytes());
    let wpid = spawn("ident-writer", || {
        let mut w = SlotWriter::new(0);
        w.set_identity(Some(xbox())); // before the file exists, as the app does
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        w.set_identity(Some(ds()));
        shm.set(2, 1);
        shm.wait(3, 1, 30)?;
        w.release(); // session end, then a new session: a new file
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        shm.set(4, 1);
        shm.wait(5, 1, 30)?;
        w.set_identity(None);
        shm.set(6, 1);
        shm.wait(7, 1, 30)?;
        Ok(())
    });
    let rpid = spawn("ident-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        ensure!(hermes_api_version() == 3, "api version {}", hermes_api_version());
        let s0 = slot_ident(0).map_err(|e| format!("slot identity errno {}", e))?;
        ensure!(
            id_name(&s0) == "Xbox 360 Controller (0)"
                && text(&s0.key)? == "xbox360"
                && (s0.bustype, s0.vendor, s0.product, s0.version) == (3, 0x045e, 0x028e, 0x0114),
            "slot identity {:?}",
            s0
        );
        ensure!(slot_ident(1).err() == Some(sys::ENODEV), "no file for slot 1: {:?}", slot_ident(1));
        ensure!(slot_ident(7).err() == Some(sys::ENODEV), "slot out of range");
        let mut h1 = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&h1.frame(1000)?), "full first");
        let a = ident(h1.h).map_err(|e| format!("identity errno {}", e))?;
        ensure!(id_name(&a) == "Xbox 360 Controller (0)" && a.gen == s0.gen, "h1 identity {:?}", a);
        shm.set(1, 1);
        shm.wait(2, 1, 30)?;
        let b = slot_ident(0).map_err(|e| format!("slot identity errno {}", e))?;
        ensure!(id_name(&b) == "DualSense Wireless Controller \u{2013} P1" && b.gen > a.gen, "slot identity B {:?}", b);
        // the open handle keeps A, through reads and refreshes
        for _ in 0..5 {
            h1.drain()?;
            let v = ident(h1.h).map_err(|e| format!("identity errno {}", e))?;
            ensure!(id_name(&v) == "Xbox 360 Controller (0)" && v.gen == a.gen, "h1 changed under the open handle: {:?}", v);
            std::thread::sleep(Duration::from_millis(2));
        }
        // a new attach takes B
        let h2 = Rd::attach(0, sys::O_NONBLOCK)?;
        let v2 = ident(h2.h).map_err(|e| format!("identity errno {}", e))?;
        ensure!(id_name(&v2) == id_name(&b) && v2.product == 0x0ce6 && text(&v2.key)? == "ds5", "h2 identity {:?}", v2);
        // a forked child keeps each handle's identity
        let (h1h, h2h) = (h1.h, h2.h);
        let child = spawn("ident-child", move || {
            ensure!(id_name(&ident(h1h).map_err(|e| format!("errno {}", e))?) == "Xbox 360 Controller (0)", "child h1");
            ensure!(id_name(&ident(h2h).map_err(|e| format!("errno {}", e))?).starts_with("DualSense"), "child h2");
            Ok(())
        });
        ensure!(wait_pid(child) == 0, "forked child saw other identities");
        shm.set(3, 1);
        shm.wait(4, 1, 30)?;
        // h1 re-attaches to the new file: that, and only that, takes the current identity
        let end = Instant::now() + Duration::from_secs(5);
        loop {
            let _ = h1.drain();
            let v = ident(h1.h).map_err(|e| format!("identity errno {}", e))?;
            if id_name(&v) == id_name(&b) {
                ensure!(v.gen == b.gen, "re-attached identity gen {} (file {})", v.gen, b.gen);
                break;
            }
            ensure!(id_name(&v) == "Xbox 360 Controller (0)", "h1 identity {:?}", v);
            ensure!(Instant::now() < end, "h1 never re-attached to the new file");
            std::thread::sleep(Duration::from_millis(5));
        }
        shm.set(5, 1);
        shm.wait(6, 1, 30)?;
        ensure!(slot_ident(0).err() == Some(sys::ENODATA), "cleared identity: {:?}", slot_ident(0));
        ensure!(id_name(&ident(h1.h).map_err(|e| format!("errno {}", e))?) == id_name(&b), "h1 keeps B after the clear");
        let h3 = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ident(h3.h).err() == Some(sys::ENODATA), "a new attach after the clear has no identity");
        ensure!(hermes_detach(h3.h) == 0, "detach");
        ensure!(ident(h3.h).err() == Some(sys::EBADF), "stale handle");
        let st = slot_view(0);
        let s = stats();
        say(&format!(
            "[ident] file: control gen {} applied {} id gen attached {} identity reads {}; process: applied {} busy {} reads {}\n",
            st.control_gen, st.reader_ctl_gen_applied, st.reader_id_gen_attached, st.reader_identity_reads, s.ctl_applied, s.ctl_busy, s.identity_reads
        ));
        ensure!(st.control_gen > b.gen as u64 && st.reader_id_gen_attached == st.control_gen, "stats {:?}", st);
        ensure!(st.reader_identity_reads > 0 && s.identity_reads > 0, "identity reads counted");
        shm.set(7, 1);
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn the_app_changes_stick_pacing_live() {
    let sc = Scene::new("live");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 -> unpaced at (ns), 3 -> environment at (ns),
    //      4 spam start, 5 spam end, 6 reader done
    let wpid = spawn("live-writer", || {
        let mut w = SlotWriter::new(0);
        w.set_session(CTL_AXIS_FIXED, 30, 0, 0, 0);
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let start = Instant::now();
        shm.set(4, sys::mono_ns());
        for i in 0..1500u32 {
            sleep_until(start + Duration::from_micros(2000 * i as u64)); // 500 Hz for 3 s
            if i == 500 {
                w.set_session(CTL_AXIS_UNPACED, 0, 0, 0, 0);
                shm.set(2, sys::mono_ns());
            }
            if i == 1000 {
                w.set_session(CTL_AXIS_UNSET, 0, 0, 0, 0);
                shm.set(3, sys::mono_ns());
            }
            w.set_pad(pad_axes(0, stick(i)));
        }
        shm.set(5, sys::mono_ns());
        shm.wait(6, 1, 30)?;
        Ok(())
    });
    let rpid = spawn("live-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        std::env::set_var("HERMES_AXIS_HZ", "125"); // what mode 0 hands back to
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        ensure!(hermes_axis_hz() == 30 && hermes_axis_auto() == 0, "the app's fixed 30 Hz: {}", hermes_axis_hz());
        shm.set(1, 1);
        let mut stamps = Vec::new();
        let (mut hz_unpaced, mut hz_env) = (None, None);
        let end = Instant::now() + Duration::from_secs(30);
        while shm.get(5) == 0 || r.poll(60) != 0 {
            ensure!(Instant::now() < end, "writer never finished");
            r.poll(20);
            match r.read_nb() {
                Ok(_) | Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("read errno {}", e)),
            }
            while let Some(f) = r.take_frame() {
                ensure!(is_axis_only(&f), "only stick frames expected: {:?}", f);
                stamps.push(sys::mono_ns());
            }
            let now = sys::mono_ns();
            if hz_unpaced.is_none() && shm.get(2) != 0 && now > shm.get(2) + 100_000_000 {
                hz_unpaced = Some(hermes_axis_hz());
            }
            if hz_env.is_none() && shm.get(3) != 0 && now > shm.get(3) + 100_000_000 {
                hz_env = Some(hermes_axis_hz());
            }
        }
        shm.set(6, 1);
        let (t0, c1, c2, t1) = (shm.get(4), shm.get(2), shm.get(3), shm.get(5));
        let rate = |a: u64, b: u64| stamps.iter().filter(|t| **t >= a && **t < b).count() as f64 / ((b - a) as f64 / 1e9);
        let settle = 50_000_000;
        let (fixed, unpaced, env) = (rate(t0 + settle, c1), rate(c1 + settle, c2), rate(c2 + settle, t1));
        let first_after = stamps.iter().find(|t| **t > c1).map(|t| (t - c1) as f64 / 1e6).unwrap_or(f64::MAX);
        let s = stats();
        say(&format!(
            "[live] fixed 30: {:.1}/s; unpaced: {:.1}/s (first frame {:.2} ms after the change); env 125: {:.1}/s; applied {} busy {}\n",
            fixed, unpaced, first_after, env, s.ctl_applied, s.ctl_busy
        ));
        ensure!(fixed <= 33.0 && fixed >= 20.0, "fixed 30 Hz phase ran at {:.1}/s", fixed);
        ensure!(unpaced >= 200.0, "unpaced phase only {:.1}/s", unpaced);
        ensure!(first_after < 15.0, "the change took {:.2} ms to apply (old 33 ms deadline?)", first_after);
        ensure!(env <= 125.0 * 1.1 && env >= 125.0 * 0.7, "mode 0 should hand back HERMES_AXIS_HZ=125: {:.1}/s", env);
        ensure!(hz_unpaced == Some(0) && hz_env == Some(125), "effective rate reported {:?} / {:?}", hz_unpaced, hz_env);
        ensure!(s.ctl_applied >= 3, "control gens applied: {}", s.ctl_applied);
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

#[test]
fn an_old_format_file_keeps_the_environment_and_has_no_identity() {
    let sc = Scene::new("old");
    run("old-format", || {
        let mut w = SlotWriter::new(0);
        w.set_session(CTL_AXIS_FIXED, 30, 0, 0, 0);
        w.set_identity(Some(Identity::new(3, 1, 2, 3, b"key", b"name")));
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        // play a format 1.4 writer: same bytes, older minor
        map_shared(&sc.pad(0)).header.minor.store(4, Relaxed);
        sc.enter();
        std::env::set_var("HERMES_AXIS_HZ", "125");
        let applied = stats().ctl_applied;
        let mut r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(is_full(&r.frame(1000)?), "full first");
        w.set_pad(pad_axes(0, stick(3)));
        r.frame(1000)?;
        ensure!(hermes_axis_hz() == 125, "environment pacing expected, got {}", hermes_axis_hz());
        ensure!(ident(r.h).err() == Some(sys::ENODATA), "no identity from a 1.4 file");
        ensure!(slot_ident(0).err() == Some(sys::ENODATA), "no slot identity from a 1.4 file");
        let st = slot_view(0);
        ensure!(st.control_gen == 0 && st.reader_ctl_gen_applied == 0, "no control stats from a 1.4 file: {:?}", st);
        ensure!(stats().ctl_applied == applied, "nothing applied");
        Ok(())
    });
}

fn churn_id(i: u32) -> Identity {
    let name = format!("Pad {:06} {}", i, "\u{e9}".repeat((i % 25) as usize));
    let key = format!("k{}", i % 100_000);
    Identity::new(3, i as u16, (i >> 16) as u16, name.len() as u16, key.as_bytes(), name.as_bytes())
}

/// The identity a view claims to be must be exactly the one the writer stored under its numbers.
fn churn_check(v: &IdentityView) -> Result<(), String> {
    let i = v.vendor as u32 | ((v.product as u32) << 16);
    let want = churn_id(i);
    let name = text(&v.name)?;
    let key = text(&v.key)?;
    ensure!(
        name.as_bytes() == Identity::text(&want.name) && key.as_bytes() == Identity::text(&want.key) && v.version == want.version,
        "torn identity: #{} name {:?} key {:?} version {}",
        i,
        name,
        key,
        v.version
    );
    Ok(())
}

#[test]
fn identity_churn_never_tears_a_name() {
    const CHANGES: u32 = 100_000;
    let sc = Scene::new("churn");
    let shm = Shm::new();
    // shm: 0 writer ready, 1 reader ready, 2 writer done, 3 reader checks so far, 4 reader done
    let wpid = spawn("churn-writer", || {
        let mut w = SlotWriter::new(0);
        w.set_identity(Some(churn_id(0)));
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        shm.set(0, 1);
        shm.wait(1, 1, 30)?;
        let end = Instant::now() + Duration::from_secs(120);
        let mut seen = 0;
        for i in 1..=CHANGES {
            w.set_identity(Some(churn_id(i)));
            // interleave: every 250 changes, let the reader finish at least one more check
            if i % 250 == 0 {
                while shm.get(3) <= seen {
                    ensure!(Instant::now() < end, "reader stalled");
                    std::thread::yield_now();
                }
                seen = shm.get(3);
            }
        }
        shm.set(2, 1);
        shm.wait(4, 1, 120)?;
        Ok(())
    });
    let rpid = spawn("churn-reader", || {
        shm.wait(0, 1, 30)?;
        sc.enter();
        let keeper = Rd::attach(0, sys::O_NONBLOCK)?;
        let kept = ident(keeper.h).map_err(|e| format!("errno {}", e))?;
        churn_check(&kept)?;
        shm.set(1, 1);
        let (mut attaches, mut slot_reads, mut last_gen) = (0u64, 0u64, 0u32);
        while shm.get(2) == 0 {
            let r = Rd::attach(0, sys::O_NONBLOCK)?;
            let v = ident(r.h).map_err(|e| format!("identity errno {}", e))?;
            churn_check(&v)?;
            ensure!(v.gen >= last_gen, "identity gen went backwards: {} after {}", v.gen, last_gen);
            last_gen = v.gen;
            ensure!(hermes_detach(r.h) == 0, "detach");
            attaches += 1;
            match slot_ident(0) {
                Ok(v) => {
                    churn_check(&v)?;
                    slot_reads += 1;
                }
                Err(sys::EAGAIN) => {}
                Err(e) => return Err(format!("slot identity errno {}", e)),
            }
            shm.set(3, attaches);
        }
        let k = ident(keeper.h).map_err(|e| format!("errno {}", e))?;
        ensure!(k.gen == kept.gen && k.name == kept.name, "the long-lived handle's identity changed");
        let last = slot_ident(0).map_err(|e| format!("errno {}", e))?;
        churn_check(&last)?;
        ensure!(last.vendor as u32 | ((last.product as u32) << 16) == CHANGES, "final identity #{}", last.vendor);
        shm.set(4, 1);
        let s = stats();
        say(&format!(
            "[churn] {} changes; {} attaches + {} slot reads checked, none torn; control reads busy {}\n",
            CHANGES, attaches, slot_reads, s.ctl_busy
        ));
        ensure!(attaches >= (CHANGES / 250) as u64, "only {} attaches interleaved", attaches);
        Ok(())
    });
    assert_eq!((wait_pid(wpid), wait_pid(rpid)), (0, 0));
}

// ---------------------------------------------------------------------------------------------
// force feedback (API 3)

use hermes_core::ff::{self, Envelope, FfEffect};
use hermes_core::layout::{CTL_FF_OFF, CTL_FF_ON};
use hermes_core::writer::STAT_READER_FF_DATAGRAMS;

/// A stand-in for the app's rumble listener (HXR1 datagrams on 127.0.0.1), on a free port this
/// process's engine is pointed at. Make it before the first call into the library.
struct Listener(std::net::UdpSocket);

impl Listener {
    fn new() -> Listener {
        let s = std::net::UdpSocket::bind("127.0.0.1:0").expect("bind a UDP port");
        std::env::set_var("HERMES_XINPUT_RUMBLE_PORT", s.local_addr().unwrap().port().to_string());
        Listener(s)
    }

    /// The next datagram within `ms`: (slot, strong, weak, duration ms).
    fn next(&self, ms: u64) -> Result<(u8, u16, u16, u16), String> {
        self.0.set_read_timeout(Some(Duration::from_millis(ms.max(1)))).unwrap();
        let mut b = [0u8; 64];
        let n = self.0.recv(&mut b).map_err(|e| format!("no datagram within {} ms ({})", ms, e))?;
        ensure!(n == 12 && b[0] == 14 && &b[1..5] == b"HXR1", "not a HXR1 datagram: {:?}", &b[..n]);
        let u = |i: usize| u16::from_le_bytes([b[i], b[i + 1]]);
        Ok((b[5], u(6), u(8), u(10)))
    }
}

fn ff_up(h: u32, e: FfEffect) -> Result<i16, i32> {
    let mut e = e;
    let r = unsafe { hermes_ff_upload(h, &mut e, core::mem::size_of::<FfEffect>()) };
    if r < 0 {
        Err(-r)
    } else {
        Ok(e.id)
    }
}

fn ff_caps(h: u32) -> Result<[u8; 16], i32> {
    let mut b = [0u8; 16];
    let r = unsafe { hermes_ff_bits(h, b.as_mut_ptr(), b.len()) };
    if r < 0 {
        Err(-r)
    } else if r != 16 {
        Err(0)
    } else {
        Ok(b)
    }
}

const STOP: (u8, u16, u16, u16) = (1, 0, 0, 1500);

#[test]
fn ff_effects_reach_the_app_as_hxr1_datagrams() {
    let sc = Scene::new("ff");
    run("ff-e2e", || {
        let rx = Listener::new();
        let mut w = sc.writer(1);
        w.open();
        sc.enter();
        let r = Rd::attach(1, sys::O_NONBLOCK)?;
        ensure!(hermes_api_version() == 3, "api version {}", hermes_api_version());
        ensure!(ff_caps(r.h) == Ok(ff::bits(true)), "mode On by default: {:?}", ff_caps(r.h));
        let mut small = [0u8; 4];
        ensure!(unsafe { hermes_ff_bits(r.h, small.as_mut_ptr(), 4) } == 4, "a short buffer gets the first bytes");
        ensure!(unsafe { hermes_ff_bits(r.h, core::ptr::null_mut(), 0) } == 0, "len 0");
        ensure!(unsafe { hermes_ff_bits(0, small.as_mut_ptr(), 4) } == -sys::EBADF, "bad handle");
        let mut e = FfEffect::rumble(1, 1);
        ensure!(unsafe { hermes_ff_upload(r.h, &mut e, 10) } == -sys::EINVAL, "short size");
        ensure!(unsafe { hermes_ff_upload(r.h, core::ptr::null_mut(), 44) } == -sys::EINVAL, "NULL");
        ensure!(hermes_ff_play(r.h, 0, 1) == -sys::EINVAL, "no effect 0 yet");

        // a 300 ms rumble: a datagram when it starts, the stop when its run is over
        let id = ff_up(r.h, FfEffect::rumble(0xC000, 0x4000).replay(300, 0)).map_err(|e| format!("upload errno {}", e))?;
        ensure!(id == 0, "first id {}", id);
        let t0 = Instant::now();
        ensure!(hermes_ff_play(r.h, 0, 1) == 0, "play");
        let d = rx.next(1000)?;
        ensure!(d == (1, 0xC000, 0x4000, 1500), "start datagram {:?}", d);
        let d = rx.next(2000)?;
        let ms = t0.elapsed().as_millis();
        ensure!(d == STOP, "stop datagram {:?}", d);
        ensure!((280..1000).contains(&ms), "stopped after {} ms (a run of 300 ms)", ms);
        ensure!(rx.next(200).is_err(), "nothing after the stop");

        // a constant force on the strong motor at half gain, stopped by the game
        ensure!(hermes_ff_set_gain(r.h, 0x8000) == 0, "gain");
        let c = ff_up(r.h, FfEffect::constant(0x7FFF, Envelope::NONE)).map_err(|e| format!("upload errno {}", e))?;
        ensure!(c == 1, "second id {}", c);
        ensure!(hermes_ff_play(r.h, 1, 1) == 0, "play constant");
        let d = rx.next(1000)?;
        ensure!(d == (1, 0x8000, 0, 1500), "constant at half gain {:?}", d);
        ensure!(hermes_ff_play(r.h, 1, 0) == 0, "stop");
        ensure!(rx.next(1000)? == STOP, "stopped");
        ensure!(hermes_ff_set_gain(r.h, 0xFFFF) == 0, "gain back");

        // the effects are the slot's: another handle plays them, and uploads its own
        let r2 = Rd::attach(1, sys::O_NONBLOCK)?;
        let q = ff_up(r2.h, FfEffect::rumble(0x1000, 0)).map_err(|e| format!("upload errno {}", e))?;
        ensure!(q == 2, "third id {}", q);
        ensure!(hermes_ff_play(r2.h, 0, 1) == 0, "the other handle plays effect 0");
        ensure!(rx.next(1000)? == (1, 0xC000, 0x4000, 1500), "shared effect");
        ensure!(rx.next(2000)? == STOP, "its stop");
        // an endless rumble the first handle uploaded, playing when it detaches: stopped and erased
        let e = ff_up(r.h, FfEffect::rumble(0x2000, 0x2000)).map_err(|e| format!("upload errno {}", e))?;
        ensure!(e == 3 && hermes_ff_play(r.h, 3, 1) == 0, "endless rumble {}", e);
        ensure!(rx.next(1000)? == (1, 0x2000, 0x2000, 1500), "endless rumble on");
        ensure!(hermes_detach(r.h) == 0, "detach");
        ensure!(rx.next(1000)? == STOP, "detach stops what the handle uploaded");
        ensure!(hermes_ff_play(r2.h, 1, 1) == -sys::EINVAL && hermes_ff_play(r2.h, 3, 1) == -sys::EINVAL, "and erases it");
        ensure!(hermes_ff_erase(r2.h, 2) == 0, "the other handle's effect stayed");
        ensure!(ff_up(r2.h, FfEffect::rumble(1, 1)) == Ok(0), "the freed ids are reused, lowest first");
        ensure!(hermes_ff_play(r.h, 0, 1) == -sys::EBADF, "stale handle");

        // counters: the process's, and the slot file's (an ff call flushes them into it)
        std::thread::sleep(Duration::from_millis(50)); // the engine counts a datagram after sending it
        let s = stats();
        ensure!(
            (s.ff_uploads, s.ff_plays, s.ff_datagrams, s.ff_send_failures) == (5, 4, 8, 0),
            "process ff stats: uploads {} plays {} datagrams {} failures {}",
            s.ff_uploads,
            s.ff_plays,
            s.ff_datagrams,
            s.ff_send_failures
        );
        ensure!(hermes_ff_set_autocenter(r2.h, 0x4000) == 0, "autocenter accepted");
        let st = slot_view(1);
        ensure!(
            (st.reader_ff_uploads, st.reader_ff_plays, st.reader_ff_datagrams, st.reader_ff_guard_fades) == (5, 4, 8, 0),
            "slot ff stats {:?}",
            st
        );
        ensure!(w.stat(STAT_READER_FF_DATAGRAMS) == 8, "the app reads them: {}", w.stat(STAT_READER_FF_DATAGRAMS));
        ensure!(hermes_detach(r2.h) == 0, "detach r2");
        Ok(())
    });
}

#[test]
fn ff_mode_comes_from_the_control_block_and_the_environment_wins() {
    let sc = Scene::new("ffmode");
    let sine = || FfEffect::periodic(ff::FF_SINE, 100, 0x4000, 0, 0, Envelope::NONE);
    run("ff-mode-app", || {
        let mut w = SlotWriter::new(0);
        w.set_ff_mode(CTL_FF_OFF);
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        sc.enter();
        let r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ff_caps(r.h) == Ok(ff::bits(false)), "the app's Off: rumble and gain only, {:?}", ff_caps(r.h));
        ensure!(ff_up(r.h, sine()) == Err(sys::EINVAL), "periodic in Off");
        ensure!(ff_up(r.h, FfEffect::constant(0x4000, Envelope::NONE)) == Err(sys::EINVAL), "constant in Off");
        ensure!(ff_up(r.h, FfEffect::rumble(0x4000, 0)) == Ok(0), "rumble in Off");
        // the app turns it on: the open handle keeps what it told the game, a new one gets On
        w.set_ff_mode(CTL_FF_ON);
        ensure!(ff_caps(r.h) == Ok(ff::bits(false)), "the open handle keeps Off");
        ensure!(ff_up(r.h, sine()) == Err(sys::EINVAL), "still Off");
        let r2 = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ff_caps(r2.h) == Ok(ff::bits(true)), "a new handle takes On");
        ensure!(ff_up(r2.h, sine()) == Ok(1), "periodic in On");
        Ok(())
    });
    run("ff-mode-env-on", || {
        let mut w = SlotWriter::new(0);
        w.set_ff_mode(CTL_FF_OFF);
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        sc.enter();
        std::env::set_var("HERMES_DINPUT_FF", "1");
        let r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ff_caps(r.h) == Ok(ff::bits(true)), "HERMES_DINPUT_FF=1 wins over the app's Off");
        ensure!(ff_up(r.h, sine()) == Ok(0), "periodic");
        Ok(())
    });
    run("ff-mode-env-off", || {
        let mut w = SlotWriter::new(0);
        w.set_ff_mode(CTL_FF_ON);
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        sc.enter();
        std::env::set_var("HERMES_DINPUT_FF", "0");
        let r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ff_caps(r.h) == Ok(ff::bits(false)), "HERMES_DINPUT_FF=0 wins over the app's On");
        ensure!(ff_up(r.h, sine()) == Err(sys::EINVAL), "periodic in Off");
        Ok(())
    });
    run("ff-mode-older-file", || {
        let mut w = SlotWriter::new(0);
        w.set_ff_mode(CTL_FF_OFF);
        w.prepare(sc.backing().as_bytes()).map_err(|e| format!("prepare {}", e))?;
        w.open();
        // a format 1.5 block carries no ff_mode (its word is reserved): force feedback is on
        let sh = map_shared(&sc.pad(0));
        sh.header.minor.store(5, Relaxed);
        sh.ctl.rec[hermes_core::layout::rec::FF_MODE].store(0, Relaxed);
        sc.enter();
        let r = Rd::attach(0, sys::O_NONBLOCK)?;
        ensure!(ff_caps(r.h) == Ok(ff::bits(true)), "a 1.5 file: On");
        ensure!(ff_up(r.h, sine()) == Ok(0), "periodic");
        ensure!(slot_view(0).reader_ff_uploads == 0, "no ff counters in a 1.5 file");
        Ok(())
    });
}

/// One thread's share of the smoke test: 10k calls on its own effects (at most 6, so two threads
/// never fill the slot's 16), every result checked. A short pause every 50 calls spreads them over
/// ~100+ ms, so they also contend with the engine thread's ticks and sends, not just each other.
fn ff_churn(h: u32, seed: u32) -> Result<u64, String> {
    let mut mine: Vec<(i16, u16)> = Vec::new();
    let mut x = seed;
    let mut calls = 0u64;
    for i in 0..10_000u32 {
        x = x.wrapping_mul(1_103_515_245).wrapping_add(12_345);
        let pick = (x >> 16) as usize;
        let r: c_int = match i % 6 {
            0 | 1 if mine.len() < 6 => {
                let e = match pick % 3 {
                    0 => FfEffect::rumble(pick as u16, (pick >> 3) as u16).replay((pick % 50) as u16, 0),
                    1 => FfEffect::periodic(ff::FF_SINE, (pick % 200) as u16, 0x3000, 0, 0, Envelope::NONE),
                    _ => FfEffect::constant(0x2000, Envelope::NONE).replay(20, 5),
                };
                let kind = e.kind;
                match ff_up(h, e) {
                    Ok(id) => {
                        mine.push((id, kind));
                        0
                    }
                    Err(e) => -e,
                }
            }
            2 if !mine.is_empty() => hermes_ff_play(h, mine[pick % mine.len()].0 as c_int, 1 + (pick % 3) as i32),
            3 if !mine.is_empty() => {
                let (id, kind) = mine[pick % mine.len()];
                let mut e = match kind {
                    ff::FF_RUMBLE => FfEffect::rumble(0x7000, 0x100),
                    ff::FF_PERIODIC => FfEffect::periodic(ff::FF_SQUARE, 40, 0x1000, 0x100, 0, Envelope::NONE),
                    _ => FfEffect::constant(-0x1000, Envelope::NONE),
                };
                e.id = id;
                unsafe { hermes_ff_upload(h, &mut e, core::mem::size_of::<FfEffect>()) }
            }
            4 if !mine.is_empty() => {
                if pick % 2 == 0 {
                    hermes_ff_play(h, mine[pick % mine.len()].0 as c_int, 0)
                } else {
                    hermes_ff_set_gain(h, (pick as u16) | 0x8000)
                }
            }
            5 if !mine.is_empty() => {
                let (id, _) = mine.swap_remove(pick % mine.len());
                hermes_ff_erase(h, id as c_int)
            }
            _ => {
                let mut b = [0u8; 16];
                match unsafe { hermes_ff_bits(h, b.as_mut_ptr(), b.len()) } {
                    16 => 0,
                    n => n.min(-1),
                }
            }
        };
        ensure!(r == 0, "call {} (op {}) failed: {}", i, i % 6, r);
        calls += 1;
        if i % 50 == 49 {
            std::thread::sleep(Duration::from_micros(500));
        }
    }
    for (id, _) in mine.drain(..) {
        ensure!(hermes_ff_erase(h, id as c_int) == 0, "final erase of {}", id);
    }
    Ok(calls)
}

#[test]
fn ff_two_threads_10k_calls_each() {
    let sc = Scene::new("ffthreads");
    run("ff-threads", || {
        let rx = Listener::new();
        let mut w = sc.writer(2);
        w.open();
        sc.enter();
        let a = Rd::attach(2, sys::O_NONBLOCK)?;
        let b = Rd::attach(2, sys::O_NONBLOCK)?;
        // drain the listener on its own thread: a full socket buffer would drop the last datagrams
        let stop = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(false));
        let sock = rx.0.try_clone().map_err(|e| format!("clone socket: {}", e))?;
        let got = {
            let stop = stop.clone();
            std::thread::spawn(move || {
                sock.set_read_timeout(Some(Duration::from_millis(20))).unwrap();
                let mut v: Vec<(u8, u16, u16)> = Vec::new();
                let mut buf = [0u8; 64];
                while !stop.load(SeqCst) {
                    if let Ok(12) = sock.recv(&mut buf) {
                        v.push((buf[5], u16::from_le_bytes([buf[6], buf[7]]), u16::from_le_bytes([buf[8], buf[9]])));
                    }
                }
                v
            })
        };
        let (ha, hb) = (a.h, b.h);
        let t0 = Instant::now();
        let ta = std::thread::spawn(move || ff_churn(ha, 1));
        let tb = std::thread::spawn(move || ff_churn(hb, 2));
        let na = ta.join().map_err(|_| "thread a panicked".to_string())??;
        let nb = tb.join().map_err(|_| "thread b panicked".to_string())??;
        let secs = t0.elapsed().as_secs_f64();
        std::thread::sleep(Duration::from_millis(300)); // the engine's last ticks, and its stop
        stop.store(true, SeqCst);
        let v = got.join().map_err(|_| "listener thread panicked".to_string())?;
        let zero = |d: &(u8, u16, u16)| d.1 == 0 && d.2 == 0;
        let nonzero = v.iter().filter(|&d| !zero(d)).count();
        let s = stats();
        say(&format!(
            "[ff-threads] 2 x {} calls in {:.2} s; {} datagrams ({} non-zero); uploads {} plays {} fades {} failures {}\n",
            na,
            secs,
            v.len(),
            nonzero,
            s.ff_uploads,
            s.ff_plays,
            s.ff_guard_fades,
            s.ff_send_failures
        ));
        ensure!(na == 10_000 && nb == 10_000, "calls {} / {}", na, nb);
        ensure!(v.iter().all(|d| d.0 == 2), "datagrams for slot 2 only");
        ensure!(v.last().map_or(true, zero), "the motors end stopped: {:?}", v.last());
        ensure!(v.windows(2).all(|p| !(zero(&p[0]) && zero(&p[1]))), "a stop is sent once");
        ensure!(nonzero as f64 <= (secs + 0.35) * 25.0 + 2.0, "{} non-zero datagrams in {:.2} s (25/s at most)", nonzero, secs);
        ensure!(s.ff_send_failures == 0 && s.ff_uploads > 0 && s.ff_plays > 0, "stats");
        ensure!(secs >= 0.05 && nonzero >= 2, "the calls overlapped the engine: {:.3} s, {} non-zero datagrams", secs, nonzero);
        ensure!(hermes_detach(a.h) == 0 && hermes_detach(b.h) == 0, "detach");
        Ok(())
    });
}
