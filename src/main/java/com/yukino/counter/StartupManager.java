package com.yukino.counter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class StartupManager {
    private static final String REGISTRY_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "YukinoCounter";
    private StartupManager() {}

    public static boolean isEnabled() {
        try {
            Process p = new ProcessBuilder("reg.exe", "query", REGISTRY_KEY, "/v", VALUE_NAME).redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    public static void setEnabled(boolean enabled) throws IOException, InterruptedException {
        ProcessBuilder builder = enabled
                ? new ProcessBuilder("reg.exe", "add", REGISTRY_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d", launchCommand(), "/f")
                : new ProcessBuilder("reg.exe", "delete", REGISTRY_KEY, "/v", VALUE_NAME, "/f");
        Process process = builder.redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IOException("修改开机启动失败：" + output.strip());
    }

    private static String launchCommand() {
        String command = ProcessHandle.current().info().command().orElseThrow();
        String file = Path.of(command).getFileName().toString().toLowerCase();
        if (!file.equals("java.exe") && !file.equals("javaw.exe")) return quote(command) + " --minimized";
        String javaw = Path.of(command).resolveSibling("javaw.exe").toString();
        String classPath = System.getProperty("java.class.path");
        return quote(javaw) + " -cp " + quote(classPath) + " com.yukino.counter.Main --minimized";
    }

    private static String quote(String value) { return "\"" + value.replace("\"", "\\\"") + "\""; }
}
