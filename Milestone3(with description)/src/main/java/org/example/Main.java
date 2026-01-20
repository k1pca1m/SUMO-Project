// ===================== Main.java =====================
package org.example;

import javax.swing.*;
import java.io.File;

public class Main {

    // ===================== CONFIG / 配置 =====================
    // Defining constants ensures type safety and easy refactoring later.
    // 定义常量以确保类型安全，并便于后续重构。
    public static final String TYPE_CAR = "car";
    public static final String TYPE_TRUCK = "truck";
    public static final String TYPE_BUS = "bus";

    // Path to the main SUMO configuration file. This ties the Java app to the specific simulation scenario.
    // SUMO 主配置文件的路径。这将 Java 应用程序与特定的仿真场景绑定。
    public static final String SUMOCFG_PATH = "final.sumocfg";

    // IMPORTANT: previously you forced dropping 2nd route. Turn it OFF.
    // Control flag: If true, it would limit the user to fewer routes. Set to false to allow full flexibility.
    // 控制标志：如果为 true，将限制用户使用较少的路线。设置为 false 以允许完全的灵活性。
    public static final boolean DROP_SECOND_ROUTE = false;

    // Window size for calculating throughput (rolling average window).
    // 用于计算吞吐量的时间窗口（滚动平均窗口）。
    public static final double THROUGHPUT_WINDOW_SEC = 300.0; // 5 minutes

    // ===================== VALIDATION / 验证 =====================
    // Custom exception allows us to catch specific setup errors distinct from generic RuntimeExceptions.
    // 自定义异常允许我们要捕获特定的设置错误，以便与通用的运行时异常区分开来。
    public static class Milestone3Exception extends Exception {
        public Milestone3Exception(String message) { super(message); }
        public Milestone3Exception(String message, Throwable cause) { super(message, cause); }
    }

    /**
     * Checks if the required SUMO configuration file exists before starting.
     * checking this early prevents the application from crashing halfway through initialization.
     * 在启动前检查所需的 SUMO 配置文件是否存在。
     * 尽早检查可以防止应用程序在初始化中途崩溃。
     */
    private static void validateProjectSetup() throws Milestone3Exception {
        File cfg = new File(SUMOCFG_PATH);
        if (!cfg.exists()) {
            // Provide a helpful error message indicating where the file should be.
            // 提供有用的错误消息，指示文件应该在哪。
            throw new Milestone3Exception(
                    "Missing SUMO config file: '" + SUMOCFG_PATH + "'. " +
                            "Place it next to the program (working directory: " + new File(".").getAbsolutePath() + ")");
        }
        if (!cfg.isFile() || !cfg.canRead()) {
            throw new Milestone3Exception("SUMO config file is not readable: " + cfg.getAbsolutePath());
        }
    }

    // ===================== MAIN (tiny) =====================
    public static void main(String[] args) {
        try {
            validateProjectSetup();
        } catch (Milestone3Exception ex) {
            Logging.LOG.severe("Project setup error: " + ex.getMessage());

            // Use a Dialog box because the GUI hasn't started yet; console might not be visible to non-dev users.
            // 使用对话框，因为 GUI 尚未启动；非开发用户可能看不到控制台。
            JOptionPane.showMessageDialog(null, ex.getMessage(), "Project setup error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Logging.LOG.info("App boot @ " + Logging.nowTag());

        // Init map + trips (file parsing part)
        // Parsing XML files (Routes and Net) *before* GUI launch ensures data is ready when the window opens.
        // Also separates data loading logic from UI rendering logic.
        // 在 GUI 启动*之前*解析 XML 文件（路线和网络），确保窗口打开时数据已准备就绪。
        // 同时将数据加载逻辑与 UI 渲染逻辑分离。
        VehicleInjection.loadTripRoutesFromRou();
        MapVisualisation.initBoundsFromFiles();

        // Launch the Swing GUI on the Event Dispatch Thread (handled inside GUI.launch).
        // 在事件分发线程（EDT）上启动 Swing GUI（在 GUI.launch 内部处理）。
        GUI.launch();
    }
}