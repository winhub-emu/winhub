//! HERMES core: a shared-STATE controller transport.
//!
//! Each pad slot (0..3) is one fixed-size file created by the app. The file never grows: it holds
//! the authoritative current state of the pad, published through a small ring of versioned state
//! copies (single writer, any number of lock-free readers), plus per-button edge counters and a
//! bounded journal of recent button edges so taps between two guest reads are replayed in order.
//! Readers turn "what changed since I last looked" into evdev frames; sticks are coalesced to the
//! latest value, button edges never are.
//!
//! Module map:
//! * [`abi`]    evdev codes and the canonical button/axis order
//! * [`pad`]    conversion of the Java `GamepadState` fields to canonical values
//! * [`proto`]  the publish/read protocol, generic over the atomic type (model-checked with loom)
//! * [`layout`] the on-disk layout of one slot file and the [`layout::Snapshot`] it publishes
//! * [`frame`]  the per-reader cursor that turns snapshots into ordered evdev frames
//! * [`ff`]     the force-feedback engine: effects, the two-motor mixer, HXR1 rumble datagrams
//! * [`sys`]    raw system calls and the few libc entry points used
//! * [`writer`] the app-side writer engine (used by the JNI library and by tests)
//! * [`guest`]  the guest-side runtime behind the C ABI (handles, readiness, wake-up threads)
#![cfg_attr(not(test), no_std)]
#![allow(clippy::missing_safety_doc)]
#![allow(clippy::declare_interior_mutable_const)]
#![allow(clippy::needless_range_loop)]

pub mod abi;
pub mod ff;
pub mod frame;
pub mod guest;
pub mod layout;
pub mod msg;
pub mod pad;
pub mod proto;
pub mod sys;
pub mod writer;
