package org.example;

import org.eclipse.sumo.libtraci.Route;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIPosition;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.Vehicle;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.*;

public class SumoConnector implements Runnable {

    public static final String TYPE_CAR   = "car";
    public static final String TYPE_TRUCK = "truck";
    public static final String TYPE_BUS   = "bus";

    public static final String LANE1 = "west";   // E0
    public static final String LANE2 = "east";   // -E0
    public static final String LANE3 = "south";  // E1
    public static final String LANE4 = "north";  // E2

    public static final String ROUTE_L1_LEFT     = "r_l1_left";
    public static final String ROUTE_L1_STRAIGHT = "r_l1_straight";
    public static final String ROUTE_L1_RIGHT    = "r_l1_right";

    public static final String ROUTE_L2_LEFT     = "r_l2_left";
    public static final String ROUTE_L2_STRAIGHT = "r_l2_straight";
    public static final String ROUTE_L2_RIGHT    = "r_l2_right";

    public static final String ROUTE_L3_LEFT     = "r_l3_left";
    public static final String ROUTE_L3_STRAIGHT = "r_l3_straight";
    public static final String ROUTE_L3_RIGHT    = "r_l3_right";

    public static final String ROUTE_L4_LEFT     = "r_l4_left";
    public static final String ROUTE_L4_STRAIGHT = "r_l4_straight";
    public static final String ROUTE_L4_RIGHT    = "r_l4_right";

    public static final String[] ROUTES_LANE1 = {
            ROUTE_L1_LEFT, ROUTE_L1_STRAIGHT, ROUTE_L1_RIGHT
    };
    public static final String[] ROUTES_LANE2 = {
            ROUTE_L2_LEFT, ROUTE_L2_STRAIGHT, ROUTE_L2_RIGHT
    };
    public static final String[] ROUTES_LANE3 = {
            ROUTE_L3_LEFT, ROUTE_L3_STRAIGHT, ROUTE_L3_RIGHT
    };
    public static final String[] ROUTES_LANE4 = {
            ROUTE_L4_LEFT, ROUTE_L4_STRAIGHT, ROUTE_L4_RIGHT
    };

    public static final String TL_ID = "XY";

    private final MapPanel panel;
    private final JLabel activeLabel;
    private final JLabel avgWaitLabel;
    private final JLabel congestionLabel;
    private final JLabel throughputLabel;
    private final JLabel tlLabel;

    private volatile boolean started = false;
    private volatile int sleepMs = 50;

    private int injectedCounter = 0;
    private double totalWaitingSeconds = 0.0;
    private int waitingSamples = 0;
    private int totalDeparted = 0;

    private final Random rng = new Random();

    public SumoConnector(MapPanel panel,
                         JLabel activeLabel,
                         JLabel avgWaitLabel,
                         JLabel congestionLabel,
                         JLabel throughputLabel,
                         JLabel tlLabel) {

        this.panel = panel;
        this.activeLabel = activeLabel;
        this.avgWaitLabel = avgWaitLabel;
        this.congestionLabel = congestionLabel;
        this.throughputLabel = throughputLabel;
        this.tlLabel = tlLabel;

        Thread t = new Thread(this, "SUMO-Simulation-Thread");
        t.start();
    }

    @Override
    public void run() {
        try {
            Simulation.preloadLibraries();

            StringVector cmd = new StringVector();
            cmd.add("sumo-gui");
            cmd.add("-c");
            cmd.add("test2.sumocfg");

            Simulation.start(cmd);

            createRoutes();

            while (true) {
                if (!started) {
                    Thread.sleep(50);
                    continue;
                }

                Simulation.step();
                Thread.sleep(sleepMs);

                updateUiFromSimulation();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void createRoutes() {
        // LANE 1
        StringVector l1Left = new StringVector();
        l1Left.add("E0");
        l1Left.add("-E2");
        Route.add(ROUTE_L1_LEFT, l1Left);

        StringVector l1Straight = new StringVector();
        l1Straight.add("E0");
        l1Straight.add("E0.45");
        Route.add(ROUTE_L1_STRAIGHT, l1Straight);

        StringVector l1Right = new StringVector();
        l1Right.add("E0");
        l1Right.add("-E1");
        Route.add(ROUTE_L1_RIGHT, l1Right);

        // LANE 2
        StringVector l2Left = new StringVector();
        l2Left.add("-E0");
        l2Left.add("-E1");
        Route.add(ROUTE_L2_LEFT, l2Left);

        StringVector l2Straight = new StringVector();
        l2Straight.add("-E0");
        l2Straight.add("-E0.40");
        Route.add(ROUTE_L2_STRAIGHT, l2Straight);

        StringVector l2Right = new StringVector();
        l2Right.add("-E0");
        l2Right.add("-E2");
        Route.add(ROUTE_L2_RIGHT, l2Right);

        // LANE 3
        StringVector l3Left = new StringVector();
        l3Left.add("E1");
        l3Left.add("-E0.40");
        Route.add(ROUTE_L3_LEFT, l3Left);

        StringVector l3Straight = new StringVector();
        l3Straight.add("E1");
        l3Straight.add("-E2");
        Route.add(ROUTE_L3_STRAIGHT, l3Straight);

        StringVector l3Right = new StringVector();
        l3Right.add("E1");
        l3Right.add("E0.45");
        Route.add(ROUTE_L3_RIGHT, l3Right);

        // LANE 4
        StringVector l4Left = new StringVector();
        l4Left.add("E2");
        l4Left.add("E0.45");
        Route.add(ROUTE_L4_LEFT, l4Left);

        StringVector l4Straight = new StringVector();
        l4Straight.add("E2");
        l4Straight.add("-E1");
        Route.add(ROUTE_L4_STRAIGHT, l4Straight);

        StringVector l4Right = new StringVector();
        l4Right.add("E2");
        l4Right.add("-E0.40");
        Route.add(ROUTE_L4_RIGHT, l4Right);
    }

    private void updateUiFromSimulation() {
        Map<String, Point2D.Double> positions = new HashMap<>();
        Map<String, String> types = new HashMap<>();
        Map<String, String> lanes = new HashMap<>();

        StringVector ids = Vehicle.getIDList();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            TraCIPosition pos = Vehicle.getPosition(id);
            positions.put(id, new Point2D.Double(pos.getX(), pos.getY()));

            try {
                String laneId = Vehicle.getLaneID(id);
                lanes.put(id, laneId);
            } catch (Exception e) {
                // ignore
            }

            if (id.startsWith("car_")) {
                types.put(id, TYPE_CAR);
            } else if (id.startsWith("truck_")) {
                types.put(id, TYPE_TRUCK);
            } else if (id.startsWith("bus_")) {
                types.put(id, TYPE_BUS);
            } else {
                types.put(id, TYPE_CAR);
            }
        }

        int activeCount = ids.size();

        int stopped = 0;
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            double speed = Vehicle.getSpeed(id);
            if (speed < 0.1) {
                stopped++;
            }
        }

        double congestion = activeCount > 0
                ? (double) stopped / activeCount
                : 0.0;

        totalWaitingSeconds += stopped;
        waitingSamples++;

        double avgWaitMinutes = waitingSamples > 0
                ? (totalWaitingSeconds / waitingSamples) / 60.0
                : 0.0;

        double simTime = Simulation.getCurrentTime();
        if (simTime > 0) {
            totalDeparted = injectedCounter;
        }
        double simHours = simTime / 3600.0;
        double throughput = simHours > 0
                ? totalDeparted / simHours
                : 0.0;

        int phase = TrafficLight.getPhase(TL_ID);
        String tlText = "TL State: phase " + phase;

        SwingUtilities.invokeLater(() -> {
            panel.updateVehicles(positions, types, lanes);
            activeLabel.setText("Active Vehicles: " + activeCount);
            avgWaitLabel.setText(String.format("Avg. Wait Time: %.2f min", avgWaitMinutes));
            congestionLabel.setText(String.format("Congestion Index: %.2f", congestion));
            throughputLabel.setText(String.format("Throughput: %.0f v/h", throughput));
            tlLabel.setText(tlText);
        });
    }

    public void startSimulation() {
        this.started = true;
    }

    public void setSpeedDelay(int ms) {
        this.sleepMs = Math.max(1, ms);
    }

    public void injectVehicle(String typeId, String routeId) {
        try {
            String vehId = typeId + "_" + injectedCounter++;
            Vehicle.add(vehId, routeId, typeId);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void setTrafficLightPhase(int phase) {
        try {
            TrafficLight.setPhase(TL_ID, phase);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public String[] getRoutesForLane(String laneKey) {
        switch (laneKey) {
            case LANE1:
                return ROUTES_LANE1;
            case LANE2:
                return ROUTES_LANE2;
            case LANE3:
                return ROUTES_LANE3;
            case LANE4:
            default:
                return ROUTES_LANE4;
        }
    }

    public Random getRng() {
        return rng;
    }
}
