package com.yukino.counter;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicBoolean;

public final class Main {
    private CounterRepository repository;
    private CounterService counter;
    private NativeKeyboardHook hook;
    private MouseDistanceTracker mouseTracker;
    private SingleInstance singleInstance;
    private TrayIcon trayIcon;
    private DashboardFrame dashboard;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();
    private boolean requestExit;

    public static void main(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            JOptionPane.showMessageDialog(null, "Yukino Counter 当前仅支持 Windows。", "无法启动", JOptionPane.ERROR_MESSAGE);
            return;
        }
        Main app = new Main();
        app.requestExit = java.util.Arrays.asList(args).contains("--shutdown");
        SwingUtilities.invokeLater(app::start);
    }

    private void start() {
        try {
            FlatLightLaf.setup();
            UIManager.put("defaultFont", new Font("Microsoft YaHei UI", Font.PLAIN, 14));
            UIManager.put("Component.arc", 12);
            UIManager.put("Button.arc", 12);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.width", 12);
            singleInstance = new SingleInstance();
            if (!singleInstance.acquire(this::showDashboard, this::shutdown, requestExit)) { System.exit(0); return; }
            if (requestExit) { closeResources(); System.exit(0); return; }
            repository = new CounterRepository();
            counter = new CounterService(repository);
            counter.setErrorHandler(e -> SwingUtilities.invokeLater(() -> showError("保存统计数据失败", e)));
            hook = new NativeKeyboardHook(counter::record);
            hook.start();
            mouseTracker = new MouseDistanceTracker(counter::recordMouseDistance);
            mouseTracker.start();
            installTray();
            Runtime.getRuntime().addShutdownHook(new Thread(this::closeResources, "shutdown"));
        } catch (Throwable e) {
            showError("Yukino Counter 启动失败", e);
            closeResources();
            System.exit(1);
        }
    }

    private void installTray() throws AWTException {
        if (!SystemTray.isSupported()) {
            showDashboard();
            return;
        }
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("打开统计"); open.addActionListener(e -> showDashboard());
        MenuItem exit = new MenuItem("完全退出"); exit.addActionListener(e -> shutdown());
        Font trayFont = new Font("Microsoft YaHei UI", Font.PLAIN, 13);
        menu.setFont(trayFont); open.setFont(trayFont); exit.setFont(trayFont);
        menu.add(open); menu.addSeparator(); menu.add(exit);
        trayIcon = new TrayIcon(AppIcon.image(32), "Yukino Counter · 正在统计", menu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 1) showDashboard();
            }
        });
        SystemTray.getSystemTray().add(trayIcon);
        trayIcon.displayMessage("Yukino Counter", "已在后台开始统计，单击托盘图标查看。", TrayIcon.MessageType.INFO);
    }

    private void showDashboard() {
        SwingUtilities.invokeLater(() -> {
            if (dashboard == null) dashboard = new DashboardFrame(counter);
            dashboard.setVisible(true);
        });
    }

    private void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) return;
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
        closeResources();
        System.exit(0);
    }

    private synchronized void closeResources() {
        if (mouseTracker != null) { mouseTracker.close(); mouseTracker = null; }
        if (hook != null) { hook.close(); hook = null; }
        if (counter != null) {
            try { counter.close(); } catch (Exception e) { e.printStackTrace(); }
            counter = null;
        }
        if (singleInstance != null) {
            try { singleInstance.close(); } catch (Exception ignored) {}
            singleInstance = null;
        }
    }

    private static void showError(String title, Throwable error) {
        error.printStackTrace();
        JOptionPane.showMessageDialog(null, error.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
}
