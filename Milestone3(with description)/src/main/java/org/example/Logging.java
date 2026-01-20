// ===================== Logging.java =====================
package org.example;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

public final class Logging {

    public static final Logger LOG = Logger.getLogger("TrafficSim");

    static {
        setupLogging();
    }

    private Logging() {}

    private static void setupLogging() {
        try {
            // Redirect System.err to System.out so all logs appear in the same console stream.
            // 将 System.err 重定向到 System.out，以便所有日志出现在同一个控制台流中。
            System.setErr(System.out);

            // Define a concise, one-line format for logs. Standard Java XML logs are too verbose for simulation output.
            // 定义简洁的单行日志格式。标准的 Java XML 日志对于仿真输出过于冗长。
            System.setProperty("java.util.logging.SimpleFormatter.format",
                    "%1$tF %1$tT | %4$-7s | %2$s | %3$s | %5$s%6$s%n");

            LOG.setUseParentHandlers(false);// Disable default handlers to avoid duplicate logs. / 禁用默认处理程序以避免重复日志。
            LOG.setLevel(Level.ALL);

            // Add Console Handler
            // 添加控制台处理程序
            ConsoleHandler ch = new ConsoleHandler();
            ch.setLevel(Level.INFO);
            ch.setFormatter(new SimpleFormatter());
            LOG.addHandler(ch);

            // Add File Handler (logs to "traffic_sim.log").
            // Allows post-mortem debugging if the app crashes.
            // 添加文件处理程序（记录到 "traffic_sim.log"）。
            // 允许在应用程序崩溃时进行事后调试。
            FileHandler fh = new FileHandler("traffic_sim.log", 1_000_000, 3, true);
            fh.setLevel(Level.ALL);
            fh.setFormatter(new SimpleFormatter());
            LOG.addHandler(fh);

            LOG.info("Logger ready (console + traffic_sim.log).");
        } catch (Exception e) {
            System.err.println("Logging init failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static String nowTag() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}