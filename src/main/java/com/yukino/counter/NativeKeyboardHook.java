package com.yukino.counter;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.LPARAM;
import com.sun.jna.platform.win32.WinDef.LRESULT;
import com.sun.jna.platform.win32.WinDef.WPARAM;
import com.sun.jna.platform.win32.WinUser;
import com.sun.jna.platform.win32.WinUser.HHOOK;
import com.sun.jna.platform.win32.WinUser.KBDLLHOOKSTRUCT;
import com.sun.jna.platform.win32.WinUser.MSG;

import java.util.function.IntConsumer;

public final class NativeKeyboardHook implements AutoCloseable {
    private static final int WH_KEYBOARD_LL = 13;
    private static final int WM_KEYDOWN = 0x0100;
    private static final int WM_SYSKEYDOWN = 0x0104;
    private static final int WM_QUIT = 0x0012;
    private static final int LLKHF_INJECTED = 0x10;

    private final IntConsumer listener;
    private volatile HHOOK hook;
    private volatile int threadId;
    private volatile Thread thread;
    private WinUser.LowLevelKeyboardProc callback;

    public NativeKeyboardHook(IntConsumer listener) { this.listener = listener; }

    public synchronized void start() {
        if (thread != null) return;
        thread = new Thread(this::runLoop, "keyboard-hook");
        thread.setDaemon(false);
        thread.start();
    }

    private void runLoop() {
        threadId = Kernel32.INSTANCE.GetCurrentThreadId();
        callback = (nCode, wParam, info) -> {
            long message = wParam.longValue();
            if (nCode >= 0 && (message == WM_KEYDOWN || message == WM_SYSKEYDOWN)
                    && (info.flags & LLKHF_INJECTED) == 0) {
                int physicalScanCode = info.scanCode + ((info.flags & 1) != 0 ? 256 : 0);
                int physicalKey = KmCounterImporter.scanCodeToVirtualKey(physicalScanCode);
                listener.accept(physicalKey == 0 ? info.vkCode : physicalKey);
            }
            LPARAM pointer = new LPARAM(Pointer.nativeValue(info.getPointer()));
            return User32.INSTANCE.CallNextHookEx(hook, nCode, wParam, pointer);
        };
        hook = User32.INSTANCE.SetWindowsHookEx(WH_KEYBOARD_LL, callback,
                Kernel32.INSTANCE.GetModuleHandle(null), 0);
        if (hook == null) throw new IllegalStateException("无法安装全局键盘监听，Windows 错误码：" + Kernel32.INSTANCE.GetLastError());
        MSG msg = new MSG();
        while (User32.INSTANCE.GetMessage(msg, null, 0, 0) > 0) {
            User32.INSTANCE.TranslateMessage(msg);
            User32.INSTANCE.DispatchMessage(msg);
        }
        HHOOK current = hook;
        hook = null;
        if (current != null) User32.INSTANCE.UnhookWindowsHookEx(current);
    }

    @Override public synchronized void close() {
        if (threadId != 0) User32.INSTANCE.PostThreadMessage(threadId, WM_QUIT, new WPARAM(0), new LPARAM(0));
        Thread current = thread;
        if (current != null) {
            try { current.join(1500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        if (hook != null) User32.INSTANCE.UnhookWindowsHookEx(hook);
        hook = null;
        thread = null;
    }
}
