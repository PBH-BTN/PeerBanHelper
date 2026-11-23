use std::io;
use std::mem;
use std::ptr;

use windows_sys::Win32::Foundation::{GlobalFree, HGLOBAL, HWND};
use windows_sys::Win32::System::DataExchange::{
    CloseClipboard, EmptyClipboard, OpenClipboard, SetClipboardData,
};
use windows_sys::Win32::System::Memory::{GMEM_MOVEABLE, GlobalAlloc, GlobalLock, GlobalUnlock};
use windows_sys::Win32::System::Ole::CF_UNICODETEXT;

pub(super) fn set_text(owner: HWND, text: &str) -> io::Result<()> {
    let text: Vec<_> = text.encode_utf16().chain([0]).collect();
    let memory = ClipboardBuffer::new(&text)?;
    // SAFETY: the live tray window owns the clipboard transaction on its desktop thread.
    if unsafe { OpenClipboard(owner) } == 0 {
        return Err(io::Error::last_os_error());
    }
    let _clipboard = Clipboard;
    // SAFETY: the clipboard is open. The unlocked and movable allocation transfers on success.
    unsafe {
        if EmptyClipboard() == 0 || SetClipboardData(u32::from(CF_UNICODETEXT), memory.0).is_null()
        {
            return Err(io::Error::last_os_error());
        }
    }
    mem::forget(memory);
    Ok(())
}

struct Clipboard;

impl Drop for Clipboard {
    fn drop(&mut self) {
        // SAFETY: created only after the same thread opened the clipboard successfully.
        unsafe { CloseClipboard() };
    }
}

struct ClipboardBuffer(HGLOBAL);

impl ClipboardBuffer {
    fn new(text: &[u16]) -> io::Result<Self> {
        // SAFETY: allocate space for the UTF-16 content including its terminator.
        let memory = unsafe { GlobalAlloc(GMEM_MOVEABLE, mem::size_of_val(text)) };
        if memory.is_null() {
            return Err(io::Error::last_os_error());
        }
        let memory = Self(memory);
        // SAFETY: the allocation belongs to this buffer. It remains locked while writing.
        unsafe {
            let destination = GlobalLock(memory.0).cast::<u16>();
            if destination.is_null() {
                return Err(io::Error::last_os_error());
            }
            ptr::copy_nonoverlapping(text.as_ptr(), destination, text.len());
            GlobalUnlock(memory.0);
        }
        Ok(memory)
    }
}

impl Drop for ClipboardBuffer {
    fn drop(&mut self) {
        // SAFETY: the buffer is unlocked and still owned by this value.
        unsafe { GlobalFree(self.0) };
    }
}
