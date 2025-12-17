package org.example;

import java.util.logging.*;

public final class Logging {
    private Logging() {}

    private static volatile boolean inited = false;

    public static void initOnce() {
        if (inited) return;
        synchronized (Logging.class) {
            if (inited) return;
            inited = true;
            try {
                // IntelliJ console: stderr red hota hai, so redirect -> stdout
                System.setErr(System.out);

                System.setProperty("java.util.logging.SimpleFormatter.format",
                        "%1$tF %1$tT | %4$-7s | %2$s | %3$s | %5$s%6$s%n");

                Logger root = Logger.getLogger("");
                for (Handler h : root.getHandlers()) root.removeHandler(h);

                Logger app = Logger.getLogger("TrafficSim");
                app.setUseParentHandlers(false);
                app.setLevel(Level.ALL);

                ConsoleHandler ch = new ConsoleHandler();
                ch.setLevel(Level.INFO);
                ch.setFormatter(new SimpleFormatter());
                app.addHandler(ch);

                FileHandler fh = new FileHandler("traffic_sim.log", 1_000_000, 3, true);
                fh.setLevel(Level.ALL);
                fh.setFormatter(new SimpleFormatter());
                app.addHandler(fh);

                app.info("Logger ready (console + traffic_sim.log).");
            } catch (Exception e) {
                System.err.println("Logging init failed: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static Logger log() {
        initOnce();
        return Logger.getLogger("TrafficSim");
    }
}