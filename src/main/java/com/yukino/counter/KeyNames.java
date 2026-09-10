package com.yukino.counter;

import java.awt.event.KeyEvent;

public final class KeyNames {
    public static final int NUMPAD_ENTER = 0x100D;
    private KeyNames() {}

    public static String name(int vk) {
        return switch (vk) {
            case 8 -> "Backspace"; case 9 -> "Tab"; case 13 -> "Enter"; case 16, 160, 161 -> "Shift";
            case 17, 162, 163 -> "Ctrl"; case 18, 164, 165 -> "Alt"; case 20 -> "Caps"; case 27 -> "Esc";
            case 32 -> "Space"; case 33 -> "PgUp"; case 34 -> "PgDn"; case 35 -> "End"; case 36 -> "Home";
            case 37 -> "←"; case 38 -> "↑"; case 39 -> "→"; case 40 -> "↓"; case 45 -> "Ins"; case 46 -> "Del";
            case 91 -> "Win"; case 92 -> "Win"; case 93 -> "Menu";
            case 144 -> "Num"; case 145 -> "Scroll";
            case NUMPAD_ENTER -> "Enter";
            case 186 -> ";"; case 187 -> "="; case 188 -> ","; case 189 -> "-"; case 190 -> ".";
            case 191 -> "/"; case 192 -> "`"; case 219 -> "["; case 220 -> "\\"; case 221 -> "]"; case 222 -> "'";
            default -> KeyEvent.getKeyText(vk);
        };
    }
}
