package com.yukino.counter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KmCounterImporter {
    private static final Pattern SECTION = Pattern.compile("^\\[(\\d{8})]$");
    private static final Pattern SCAN_COUNT = Pattern.compile("^sc(\\d+)=(\\d+)$");
    private static final Pattern KEYSTROKES = Pattern.compile("^keystrokes=(\\d+)$");
    private static final Pattern MOUSE_MOVE = Pattern.compile("^move=([0-9]+(?:\\.[0-9]+)?)$");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    public static final int UNASSIGNED_KEY = 0xFFFF;

    public record Parsed(String sha256, Map<CounterService.DayKey, Long> counts,
                         Map<LocalDate, Double> mouseDistances,
                         int dayCount, long pressCount, long unassignedCount) {}

    private KmCounterImporter() {}

    public static Parsed parse(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        String text = decode(bytes);
        Map<CounterService.DayKey, Long> result = new HashMap<>();
        Map<LocalDate, Long> scannedTotals = new HashMap<>();
        Map<LocalDate, Long> declaredTotals = new HashMap<>();
        Map<LocalDate, Double> mouseDistances = new HashMap<>();
        LocalDate day = null;
        int dayCount = 0;
        long presses = 0;
        long unassigned = 0;
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            Matcher section = SECTION.matcher(line);
            if (section.matches()) {
                try {
                    day = LocalDate.parse(section.group(1), DATE);
                    dayCount++;
                } catch (DateTimeException e) {
                    day = null;
                }
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                day = null; // total/history/layout 均不导入，避免总计重复
                continue;
            }
            if (day == null) continue;
            Matcher mouseMove = MOUSE_MOVE.matcher(line);
            if (mouseMove.matches()) {
                mouseDistances.put(day, Double.parseDouble(mouseMove.group(1)));
                continue;
            }
            Matcher declared = KEYSTROKES.matcher(line);
            if (declared.matches()) {
                declaredTotals.put(day, Long.parseLong(declared.group(1)));
                continue;
            }
            Matcher value = SCAN_COUNT.matcher(line);
            if (!value.matches()) continue;
            int scanCode = Integer.parseInt(value.group(1));
            long count = Long.parseLong(value.group(2));
            if (count == 0) continue;
            scannedTotals.merge(day, count, Long::sum);
            int vk = scanCodeToVirtualKey(scanCode);
            if (vk == 0) {
                result.merge(new CounterService.DayKey(day, UNASSIGNED_KEY), count, Long::sum);
                unassigned += count;
                presses += count;
            } else {
                result.merge(new CounterService.DayKey(day, vk), count, Long::sum);
                presses += count;
            }
        }
        for (var item : declaredTotals.entrySet()) {
            long missing = item.getValue() - scannedTotals.getOrDefault(item.getKey(), 0L);
            if (missing > 0) {
                result.merge(new CounterService.DayKey(item.getKey(), UNASSIGNED_KEY), missing, Long::sum);
                presses += missing;
                unassigned += missing;
            }
        }
        if (dayCount == 0 || result.isEmpty()) throw new IOException("没有找到可导入的 KM Counter 每日键盘数据");
        return new Parsed(sha256(bytes), result, mouseDistances, dayCount, presses, unassigned);
    }

    private static String decode(byte[] bytes) throws CharacterCodingException {
        int offset = bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE ? 2 : 0;
        var decoder = (offset == 2 ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_8).newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static int scanCodeToVirtualKey(int sc) {
        if (sc >= 2 && sc <= 10) return '1' + sc - 2;
        if (sc == 11) return '0';
        if (sc >= 16 && sc <= 25) return "QWERTYUIOP".charAt(sc - 16);
        if (sc >= 30 && sc <= 38) return "ASDFGHJKL".charAt(sc - 30);
        if (sc >= 44 && sc <= 50) return "ZXCVBNM".charAt(sc - 44);
        if (sc >= 59 && sc <= 68) return 112 + sc - 59;
        if (sc >= 100 && sc <= 110) return 124 + sc - 100;
        return switch (sc) {
            case 1 -> 27; case 12 -> 189; case 13 -> 187; case 14 -> 8; case 15 -> 9;
            case 26 -> 219; case 27 -> 221; case 28 -> 13; case 284 -> KeyNames.NUMPAD_ENTER; case 29 -> 162;
            case 39 -> 186; case 40 -> 222; case 41 -> 192; case 42 -> 160; case 43 -> 220;
            case 51 -> 188; case 52 -> 190; case 53 -> 191; case 54, 310 -> 161;
            case 55 -> 106; case 56 -> 164; case 57 -> 32; case 58 -> 20;
            case 69, 325 -> 144; case 70 -> 145;
            case 71 -> 103; case 72 -> 104; case 73 -> 105; case 74 -> 109;
            case 75 -> 100; case 76 -> 101; case 77 -> 102; case 78 -> 107;
            case 79 -> 97; case 80 -> 98; case 81 -> 99; case 82 -> 96; case 83 -> 110;
            case 87 -> 122; case 88 -> 123;
            case 285 -> 163; case 309 -> 111; case 312 -> 165;
            case 327 -> 36; case 328 -> 38; case 329 -> 33; case 331 -> 37;
            case 333 -> 39; case 335 -> 35; case 336 -> 40; case 337 -> 34;
            case 338 -> 45; case 339 -> 46; case 347 -> 91; case 348 -> 92; case 349 -> 93;
            default -> 0;
        };
    }
}
