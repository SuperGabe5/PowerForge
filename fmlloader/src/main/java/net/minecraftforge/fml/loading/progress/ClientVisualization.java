/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

//wow that was such a thing to do to us @MinecraftForge

package net.minecraftforge.fml.loading.progress;

import org.apache.commons.lang3.tuple.Pair;
import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.function.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;
import org.lwjgl.opengl.GL;

class ClientVisualization implements EarlyProgressVisualization.Visualization {
    private final int screenWidth = 854;
    private final int screenHeight = 480;
    private long silentWindow = NULL;
    private JFrame frame;
    private JProgressBar progressBar;

    @Override
    public Runnable start(String mcVersion) {
        // 1. SILENT CONTEXT:Satisfy OptiFine early by creating a hidden GLFW window
        if (glfwInit()) {
            glfwDefaultWindowHints();
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            silentWindow = glfwCreateWindow(screenWidth, screenHeight, "Minecraft", NULL, NULL);
            if (silentWindow != NULL) {
                glfwMakeContextCurrent(silentWindow);
                GL.createCapabilities(); // Initialize GL capabilities for Reflector Forge
                glfwMakeContextCurrent(NULL); // Release to prevent thread locking
            }
        }

        // 2. SWING UI: Create the actual visible interface
        SwingUtilities.invokeLater(() -> {
            frame = new JFrame("FML Early Loading Process");
            frame.setSize(screenWidth, screenHeight);
            frame.setUndecorated(false);
            frame.setResizable(false);
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setLocationRelativeTo(null);

            JPanel panel = new JPanel(null) {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    drawHighDetailRAM((Graphics2D) g);
                    drawScrollingLogs((Graphics2D) g);
                }
            };
            panel.setBackground(new Color(111, 111, 111)); // New light-grey theme

            progressBar = new JProgressBar();
            progressBar.setIndeterminate(true); // Always pulsing Knox mode
            progressBar.setBounds(50, screenHeight - 60, screenWidth - 100, 14);
            progressBar.setForeground(Color.BLACK);
            progressBar.setBackground(new Color(64, 64, 64)); // Contrast for the bar track
            progressBar.setBorderPainted(false);
            progressBar.setStringPainted(true);
            progressBar.setFont(new Font("SansSerif", Font.BOLD, 10));
            progressBar.setString("PowerForge is Working!");

            panel.add(progressBar);
            frame.add(panel);
            frame.setVisible(true);
            frame.setAlwaysOnTop(true);
        });

        return () -> {
            if (frame == null || progressBar == null) return;
            int count = StartupMessageManager.getMessages().size();
            progressBar.setString("Loading Step: " + count);
            frame.repaint();
        };
    }

    private void drawHighDetailRAM(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage offHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        float pct = (float) heap.getUsed() / heap.getMax();

        String memoryStr = String.format("Memory Heap: %d / %d MB (%.1f%%)  OffHeap: %d MB",
            heap.getUsed() >> 20, heap.getMax() >> 20, pct * 100.0, offHeap.getUsed() >> 20);

        g.setFont(new Font("Monospaced", Font.BOLD, 12));
        g.setColor(new Color(40, 40, 40)); // Darker text for the lighter background
        g.drawString(memoryStr, 20, 30);
    }

    private void drawScrollingLogs(Graphics2D g) {
        List<Pair<Integer, StartupMessageManager.Message>> messages = StartupMessageManager.getMessages();
        g.setFont(new Font("Monospaced", Font.PLAIN, 12));
        for (int i = 0; i < messages.size(); i++) {
            StartupMessageManager.Message msg = messages.get(i).getRight();
            float[] c = msg.getTypeColour();
            // Since background is lighter, we ensure the log text is visible
            g.setColor(new Color(Math.min(c[0], 0.2f), Math.min(c[1], 0.2f), Math.min(c[2], 0.2f)));
            int y = 60 + (i * 18);
            if (y < screenHeight - 80) g.drawString(msg.getText(), 20, y);
        }
    }

    @Override
    public long handOffWindow(IntSupplier width, IntSupplier height, Supplier<String> title, LongSupplier monitor) {
        if (frame != null) {
            frame.setVisible(false);
            frame.dispose();
            Toolkit.getDefaultToolkit().sync(); // Crucial for Wayland cleanup
        }

        if (silentWindow != NULL) {
            glfwSetWindowTitle(silentWindow, title.get());
            glfwSetWindowSize(silentWindow, width.getAsInt(), height.getAsInt());
            glfwShowWindow(silentWindow);
            glfwMakeContextCurrent(silentWindow);
            return silentWindow;
        }

        // Fallback if silent window failed
        return NULL;
    }

    @Override public void updateFBSize(IntConsumer w, IntConsumer h) { w.accept(screenWidth); h.accept(screenHeight); }
}
