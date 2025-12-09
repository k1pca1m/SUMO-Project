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
    // 静态标签 (可以保持静态，也可以通过构造传递，这里保持您原有的静态风格以便简化)
    static JLabel activeVehiclesLabel = new JLabel("Active Vehicles: 0");
    static JLabel avgWaitLabel        = new JLabel("Avg. Wait Time: 0.0 min");
    static JLabel congestionLabel     = new JLabel("Congestion Index: 0.00");
    static JLabel throughputLabel     = new JLabel("Throughput: 0 v/h");

    // MapPanel (保持不变，只是为了 SimulationManager 能访问，设为 public static)
    public static class MapPanel extends JPanel {
        private final Map<String, Point2D.Double> vehicles = new ConcurrentHashMap<>();

        public void updateVehicles(Map<String, Point2D.Double> newData) {
            vehicles.clear();
            vehicles.putAll(newData);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.RED);
            for (Point2D.Double p : vehicles.values()) {
                int x = (int) (p.x * 2);
                int y = (int) (p.y * 2);
                g.fillOval(x, y, 6, 6);
            }
        }
    }

    public static void main(String[] args) {

        // 1. 初始化 GUI
        JFrame frame = new JFrame("Traffic Grid Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(new BorderLayout());

        MapPanel panel = new MapPanel();
        panel.setBackground(Color.WHITE);
        frame.add(panel, BorderLayout.CENTER);

        // 2. 初始化控制面板
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBorder(BorderFactory.createTitledBorder("Simulation Controls"));

        // --- 现有的按钮 ---
        controls.add(new JLabel("Speed Control"));
        controls.add(new JButton("1x"));
        controls.add(new JButton("10x"));
        controls.add(Box.createVerticalStrut(10));

        // 3. --- 新增：车辆注入面板 ---
        JPanel injectPanel = new JPanel();
        injectPanel.setLayout(new BoxLayout(injectPanel, BoxLayout.Y_AXIS));
        injectPanel.setBorder(BorderFactory.createTitledBorder("Inject Vehicle"));

        JTextField txtId = new JTextField("veh_GUI_01");
        JTextField txtRoute = new JTextField("route_0"); // 确保此路线在 .rou.xml 中存在
        JButton btnInject = new JButton("Inject");

        injectPanel.add(new JLabel("ID:"));
        injectPanel.add(txtId);
        injectPanel.add(new JLabel("Route:"));
        injectPanel.add(txtRoute);
        injectPanel.add(Box.createVerticalStrut(5));
        injectPanel.add(btnInject);

        controls.add(injectPanel);

        frame.add(controls, BorderLayout.WEST);

        // 指标面板
        JPanel metrics = new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        metrics.setBorder(BorderFactory.createTitledBorder("Metrics"));
        metrics.add(activeVehiclesLabel);
        // ... 添加其他标签 ...
        frame.add(metrics, BorderLayout.EAST);

        frame.setVisible(true);

        // 4. 初始化 SimulationManager (核心修改)
        // 将 GUI 组件传给管理器，以便它能更新界面
        SimulationManager simManager = new SimulationManager(panel, activeVehiclesLabel);

        // 5. 绑定按钮事件：连接 GUI 和 Manager
        btnInject.addActionListener(e -> {
            String id = txtId.getText();
            String route = txtRoute.getText();
            if(!id.isEmpty() && !route.isEmpty()) {
                simManager.injectVehicle(id, route); // 调用管理器接口
                txtId.setText("veh_GUI_" + System.currentTimeMillis()%1000); // 随机更新ID方便下次点击
            }
        });

        // 6. 启动 SUMO 和 线程
        Simulation.preloadLibraries();
        StringVector sv = new StringVector();
        sv.add("sumo-gui");
        sv.add("-c");
        // 请替换为您的真实路径
        sv.add("C:\\Users\\17608\\Downloads\\Test\\demo2.sumocfg");

        try {
            Simulation.start(sv);
            // 启动工作线程
            new Thread(simManager).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
/*
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
                                        //Loads the native C++ JNI libraries (libtracijni.dll) required for Java to talk to SUMO.
        StringVector sv = new StringVector();//Constructs the command-line arguments effectively equivalent to running sumo-gui -c config.file in a terminal.
        // yahan ya to "sumo-gui" (agar PATH me hai) ya full path:
        sv.add("sumo-gui");
        sv.add("-c");
        //sv.add("C:\\Users\\jaisw\\Downloads\\Test2\\test2.sumocfg");
        sv.add("C:\\Users\\17608\\Downloads\\Test\\demo2.sumocfg");
        Simulation.start(sv);            // SUMO + TraCI connection [web:2]
                                        //Launches the SUMO process and establishes the TCP/IP connection between your Java program and SUMO.
        // 3) Background loop (simulation + GUI update)
        //new thread
        // SUMO simulation steps and network communication are "blocking" operations. If you ran this while loop on the main thread (the GUI thread), the entire application window would freeze and become unresponsive until the simulation finished.
        //
        //The Loop:
        // while (Simulation.getMinExpectedNumber() > 0) keeps the simulation running as long as vehicles exist or are scheduled to arrive.
        new Thread(() -> {
            try {
                // jab tak simulation me vehicles expected hain tab tak hi loop chale [web:2][web:167]
                while (Simulation.getMinExpectedNumber() > 0) {
                    Simulation.step();   // ek timestep aage badhao
                                        //Commands SUMO to advance time by one step
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

                    Thread.sleep(50);   // ~20 FPSPauses the loop for 50ms, capping the frame rate to approximately 20 FPS to make the visualization readable
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
    }*/
}
