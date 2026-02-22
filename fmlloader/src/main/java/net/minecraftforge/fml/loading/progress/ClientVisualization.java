/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

//wow that was such a thing to do to us @MinecraftForge

package net.minecraftforge.fml.loading.progress;

import com.sun.management.OperatingSystemMXBean;
import org.apache.commons.lang3.tuple.Pair;
import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.function.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.opengl.GL;

class ClientVisualization implements EarlyProgressVisualization.Visualization {
    private final int screenWidth = 854, screenHeight = 480;
    private long silentWindow = NULL;
    private JFrame frame;
    private JProgressBar progressBar;

    // Disk R/W Tracking
    private long lastDiskCheck = System.currentTimeMillis();
    private long lastMessageCount = 0;

    @Override
    public Runnable start(String mcVersion) {
        // 1. ORIGINAL GL CONTEXT: The stable way to satisfy OptiFine/Reflector Forge
        if (glfwInit()) {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            silentWindow = glfwCreateWindow(screenWidth, screenHeight, "Minecraft", NULL, NULL);
            if (silentWindow != NULL) {
                glfwMakeContextCurrent(silentWindow);
                GL.createCapabilities();
                glfwMakeContextCurrent(NULL); // Release to main thread
            }
        }

        // 2. SWING UI
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("X-ModLoader");
            frame.setSize(screenWidth, screenHeight);
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    drawHighDetailHeaps((Graphics2D) g);
                    drawScrollingLogs((Graphics2D) g);
                }
            };
            panel.setBackground(new Color(111, 111, 111));

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true); // Permanent pulse mode
            progressBar.setBounds(50, screenHeight - 60, screenWidth - 100, 14);
            progressBar.setForeground(Color.BLACK);
            progressBar.setBackground(new Color(64, 64, 64));
            progressBar.setBorderPainted(false);
            progressBar.setStringPainted(true);
            progressBar.setFont(new Font("SansSerif", Font.BOLD, 10));
            progressBar.setString("PLEASE WAIT...");

            panel.add(progressBar);
            frame.add(panel);
            frame.setVisible(true);
        });

        return () -> { if (frame != null) frame.repaint(); };
    }

    private void drawHighDetailHeaps(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(new Color(40, 40, 40));

        // RAM & CPU Stats
        OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long ram = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() >> 20;
        double cpu = os.getProcessCpuLoad() * 100.0;

        // DISK R/W Simulation: Tracking message delta as a proxy for activity
        long now = System.currentTimeMillis();
        long currentMsgs = StartupMessageManager.getMessages().size();
        String rwStatus = (currentMsgs > lastMessageCount) ? "READ/WRITE" : "IDLE";
        if (now - lastDiskCheck > 1000) { // Update tracking every second
            lastMessageCount = currentMsgs;
            lastDiskCheck = now;
        }

        String stats = String.format("RAM: %d MB | CPU: %.1f%% | DISK: %s", ram, cpu < 0 ? 0 : cpu, rwStatus);
        g.drawString(stats, 20, 30);
    }

    private void drawScrollingLogs(Graphics2D g) {
        List<Pair<Integer, StartupMessageManager.Message>> messages = StartupMessageManager.getMessages();
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        for (int i = 0; i < messages.size(); i++) {
            StartupMessageManager.Message msg = messages.get(i).getRight();
            float[] c = msg.getTypeColour();
            g.setColor(new Color(Math.min(c[0], 0.2f), Math.min(c[1], 0.2f), Math.min(c[2], 0.2f)));
            int y = 60 + (i * 18);
            if (y < screenHeight - 80) g.drawString(msg.getText(), 20, y);
        }
    }

    @Override
    public long handOffWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        if (frame != null) { frame.setVisible(false); frame.dispose(); Toolkit.getDefaultToolkit().sync(); }
        if (silentWindow != NULL) {
            glfwSetWindowTitle(silentWindow, title.get());
            glfwSetWindowSize(silentWindow, width.getAsInt(), height.getAsInt());
            glfwShowWindow(silentWindow);
            glfwMakeContextCurrent(silentWindow);
            return silentWindow;
        }
        return NULL;
    }

    @Override public void updateFBSize(IntConsumer w, IntConsumer h) { w.accept(screenWidth); h.accept(screenHeight); }
}
