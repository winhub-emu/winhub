//! A fixed-size, allocation-free log line builder (no `core::fmt` on the guest path).

use core::ffi::c_char;

pub struct Msg {
    buf: [u8; 200],
    len: usize,
}

impl Default for Msg {
    fn default() -> Self {
        Msg::new()
    }
}

impl Msg {
    pub const fn new() -> Msg {
        Msg { buf: [0; 200], len: 0 }
    }

    pub fn s(&mut self, s: &[u8]) -> &mut Msg {
        for &c in s {
            if self.len + 1 < self.buf.len() {
                self.buf[self.len] = c;
                self.len += 1;
            }
        }
        self
    }

    pub fn u(&mut self, mut v: u64) -> &mut Msg {
        let mut tmp = [0u8; 20];
        let mut i = tmp.len();
        loop {
            i -= 1;
            tmp[i] = b'0' + (v % 10) as u8;
            v /= 10;
            if v == 0 {
                break;
            }
        }
        self.s(&tmp[i..])
    }

    pub fn i(&mut self, v: i64) -> &mut Msg {
        if v < 0 {
            self.s(b"-");
            self.u(v.unsigned_abs())
        } else {
            self.u(v as u64)
        }
    }

    pub fn x(&mut self, v: u64) -> &mut Msg {
        const HEX: &[u8; 16] = b"0123456789abcdef";
        let mut tmp = [0u8; 16];
        for k in 0..16 {
            tmp[15 - k] = HEX[((v >> (k * 4)) & 0xf) as usize];
        }
        self.s(&tmp)
    }

    pub fn bytes(&self) -> &[u8] {
        &self.buf[..self.len]
    }

    /// NUL-terminated view (valid while `self` lives).
    pub fn c_str(&mut self) -> *const c_char {
        let n = self.len.min(self.buf.len() - 1);
        self.buf[n] = 0;
        self.buf.as_ptr() as *const c_char
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn formats_numbers() {
        let mut m = Msg::new();
        m.s(b"a ").u(0).s(b" ").u(1234567890123).s(b" ").i(-42).s(b" ").x(0xbeef);
        assert_eq!(m.bytes(), b"a 0 1234567890123 -42 000000000000beef");
        let mut long = Msg::new();
        for _ in 0..100 {
            long.s(b"xyz");
        }
        assert_eq!(long.bytes().len(), 199);
    }
}
