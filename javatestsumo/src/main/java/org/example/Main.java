package org.example;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TraCIPosition;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Main {

    // Metrics labels (right side panel)
    static JLabel activeVehiclesLabel = new JLabel("Active Vehicles: 0");
    static JLabel avgWaitLabel        = new JLabel("Avg. Wait Time: 0.0 min");
    static JLabel congestionLabel     = new JLabel("Congestion Index: 0.00");
    static JLabel throughputLabel     = new JLabel("Throughput: 0 v/h");

    // Panel jo vehicles draw karega
    static class MapPanel extends JPanel {
        private final Map<String, Point2D.Double> vehicles = new ConcurrentHashMap<>();

        void updateVehicles(Map<String, Point2D.Double> newData) {
            vehicles.clear();
            vehicles.putAll(newData);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.RED);
            for (Point2D.Double p : vehicles.values()) {
                int x = (int) (p.x * 2);  // simple scale
                int y = (int) (p.y * 2);
                g.fillOval(x, y, 6, 6);
            }
        }
    }

    public static void main(String[] args) {

        // 1) Swing window + layout
        JFrame frame = new JFrame("Traffic Grid Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(new BorderLayout());

        // center: map panel
        MapPanel panel = new MapPanel();
        panel.setBackground(Color.WHITE);
        frame.add(panel, BorderLayout.CENTER);

        // left: controls panel
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createTitledBorder("Simulation Controls"));

        JLabel speedLabel = new JLabel("Speed Control");
        JButton speed1x   = new JButton("1x");
        JButton speed10x  = new JButton("10x");

        JLabel paramsLabel   = new JLabel("Parameters");
        JCheckBox carsBox    = new JCheckBox("Cars", true);
        JCheckBox trucksBox  = new JCheckBox("Trucks", true);
        JCheckBox busesBox   = new JCheckBox("Buses", true);

        JButton exportBtn = new JButton("Export Data");

        controls.add(speedLabel);
        controls.add(speed1x);
        controls.add(speed10x);
        controls.add(Box.createVerticalStrut(10));
        controls.add(paramsLabel);
        controls.add(carsBox);
        controls.add(trucksBox);
        controls.add(busesBox);
        controls.add(Box.createVerticalStrut(10));
        controls.add(exportBtn);

        frame.add(controls, BorderLayout.WEST);

        // right: metrics panel
        JPanel metrics = new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        metrics.setBorder(BorderFactory.createTitledBorder("Metrics"));
        metrics.add(activeVehiclesLabel);
        metrics.add(avgWaitLabel);
        metrics.add(congestionLabel);
        metrics.add(throughputLabel);

        frame.add(metrics, BorderLayout.EAST);

        frame.setVisible(true);

        // 2) SUMO + libtraci start
        Simulation.preloadLibraries();   // native libs load karega [web:1]

        StringVector sv = new StringVector();
        // yahan ya to "sumo-gui" (agar PATH me hai) ya full path:
        sv.add("sumo-gui");
        sv.add("-c");
        //sv.add("C:\\Users\\jaisw\\Downloads\\Test2\\test2.sumocfg");
        sv.add("C:\\Users\\jaisw\\Documents\\bsdk\\javatestsumo\\test2.sumocfg");
        Simulation.start(sv);            // SUMO + TraCI connection [web:2]

        // 3) Background loop (simulation + GUI update)
        new Thread(() -> {
            try {
                // jab tak simulation me vehicles expected hain tab tak hi loop chale [web:2][web:167]
                while (Simulation.getMinExpectedNumber() > 0) {
                    Simulation.step();   // ek timestep aage badhao

                    List<String> ids = Vehicle.getIDList();
                    Map<String, Point2D.Double> pos = new HashMap<>();

                    for (String id : ids) {
                        TraCIPosition p = Vehicle.getPosition(id);
                        double x = p.getX();
                        double y = p.getY();
                        pos.put(id, new Point2D.Double(x, y));
                    }

                    int active = ids.size();
                    // abhi ke liye dummy values; baad me real metrics nikal sakte ho
                    double avgWait = 0.0;
                    double congestion = 0.0;
                    int throughput = 0;

                    SwingUtilities.invokeLater(() -> {
                        panel.updateVehicles(pos);
                        panel.repaint();

                        activeVehiclesLabel.setText("Active Vehicles: " + active);
                        avgWaitLabel.setText("Avg. Wait Time: " + String.format("%.1f min", avgWait));
                        congestionLabel.setText("Congestion Index: " + String.format("%.2f", congestion));
                        throughputLabel.setText("Throughput: " + throughput + " v/h");
                    });

                    Thread.sleep(50);   // ~20 FPS
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // TraCI + SUMO band
                Simulation.close();                // [web:2]
                // Swing window band
                SwingUtilities.invokeLater(frame::dispose);
                // pura Java process khatam (optional)
                System.exit(0);
            }
        }).start();

        // speed buttons ke liye tu baad me shared variable bana ke sleep/step adjust kar sakta hai
    }
}
