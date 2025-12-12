package org.example;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

public class Main {
    private static final String SUMO_CONFIG_PATH = "C:\\Users\\17608\\Desktop\\SUMO-Project-main\\JavaSumo\\map3.sumocfg";
    // --- 从 map3.net.xml 提取的有效 Edge ID 列表 ---
    // 这些是主要的 residential 和 living_street 道路
    private static final String[] MAP_EDGES = {
            "E0",
            "-1157218234#2",
            "438898636#1",
            "371146251",
            "-371146251",
            "4824697",
            "132963888#0",
            "-4824704#7",
            "4824702#0",
            "149539968",
            "96136748",
            "-96136748",
            "4824711"
    };
    // --- 样式常量 (Color Palette) ---
    private static final Color BG_DARK = new Color(45, 52, 54);       // 深灰色背景
    //private static final Color BG_LIGHT = new Color(245, 246, 250);   // 浅灰色背景
    private static final Color ACCENT_COLOR = new Color(9, 132, 227); // 科技蓝按钮
    //private static final Color TEXT_WHITE = Color.WHITE;
    private static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 14);
    private static final Font FONT_NORMAL = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font FONT_METRIC = new Font("Consolas", Font.BOLD, 16);

    /**
     * MapPanel: 地图绘制区域
     */
    public static class MapPanel extends JPanel {
        private final Map<String, Point2D.Double> vehicles = new ConcurrentHashMap<>();

        public MapPanel() {
            setBackground(Color.WHITE);
            // 给地图加一个微妙的内阴影边框
            setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, Color.LIGHT_GRAY));
        }

        public void updateVehicles(Map<String, Point2D.Double> newData) {
            vehicles.clear();
            vehicles.putAll(newData);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;

            // 开启抗锯齿，画质更细腻
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            //g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 1. (未来) 在这里绘制路网线条...
            // g2d.setColor(Color.LIGHT_GRAY);
            // g2d.draw(new Line2D.Double(...));

            // 2. 绘制车辆
            for (Point2D.Double p : vehicles.values()) {
                // 坐标转换 (简单放大)
                int x = (int) (p.x * 1.5);
                int y = (int) (p.y * 1.5);

                // 绘制带边框的车辆圆点
                g2d.setColor(new Color(214, 48, 49)); // 鲜艳的红色
                g2d.fillOval(x, y, 8, 8);

                g2d.setColor(Color.WHITE); // 白色描边，增加立体感
                //g2d.setStroke(new BasicStroke(1));
                g2d.drawOval(x, y, 10, 10);
            }

            // 绘制水印或图例
            g2d.setColor(Color.GRAY);
            g2d.drawString("Map View: 2D Projection", 10, getHeight() - 10);
        }
    }

    public static void main(String[] args) {
        // 1. 设置系统原生外观 (Windows风格)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // 2. 初始化主窗口
        JFrame frame = new JFrame("Map3 Traffic Dashboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 700); //稍微大一点
        frame.setLayout(new BorderLayout());

        // --- 中心：地图 ---
        MapPanel mapPanel = new MapPanel();
        frame.add(mapPanel, BorderLayout.CENTER);

        // --- 右侧：指标面板 (Metrics) ---
        JPanel rightPanel = createSidebarPanel("Real-time Analytics");

        // 创建漂亮的指标卡片
        JLabel activeVehiclesLabel = createMetricLabel("Active Vehicles", "0");
        JLabel avgWaitLabel = createMetricLabel("Avg. Wait Time", "0.0 min");
        JLabel congestionLabel = createMetricLabel("Congestion Idx", "0.00");

        rightPanel.add(activeVehiclesLabel);
        rightPanel.add(Box.createVerticalStrut(15));
        rightPanel.add(avgWaitLabel);
        //rightPanel.add(Box.createVerticalStrut(15));
        rightPanel.add(congestionLabel);
        rightPanel.add(Box.createVerticalGlue()); // 占位，把内容顶上去

        frame.add(rightPanel, BorderLayout.EAST);

        // --- 核心逻辑初始化 ---
        // 注意：我们传递的是 MetricLabel 中的值部分，或者你可以改造 Manager 直接更新这些 Label
        // 这里为了兼容旧代码，我们先传 activeVehiclesLabel (你需要确保 Manager 更新的是这个 Label 的 Text)
        // *更好的做法是将 activeVehiclesLabel 设为 "Active Vehicles: 0"*
        activeVehiclesLabel.setText("Active Vehicles: 0");

        SimulationManager simManager = new SimulationManager(mapPanel, activeVehiclesLabel,congestionLabel);


        // --- 左侧：控制面板 (Controls) ---
        JPanel leftPanel = createSidebarPanel("Control Center");

        // 1. 单车注入面板
        JPanel injectPanel = createStyledSubPanel("Single Injection (Edge)");
        JTextField txtId = new JTextField("veh_01");
        // 默认值改为一个真实存在的 Edge ID，例如 E0
        JTextField txtEdge = new JTextField("E0");
        JButton btnInject = createStyledButton("Inject on Edge", ACCENT_COLOR);

        // 布局表单
        layoutForm(injectPanel,
                new String[]{"Vehicle ID:", "Edge ID:"},
                new JComponent[]{txtId, txtEdge}
        );
        injectPanel.add(Box.createVerticalStrut(10));
        injectPanel.add(btnInject);

        // 绑定事件
        btnInject.addActionListener(e -> {
            String id = txtId.getText().trim();
            String edge = txtEdge.getText().trim();
            if (!id.isEmpty() && !edge.isEmpty()) {
                simManager.injectVehicleOnEdge(id, edge);
                txtId.setText("veh_" + System.currentTimeMillis() % 10000);
            }
        });

        // 2. 批量注入面板 (新功能)
        JPanel batchPanel = createStyledSubPanel("Batch Edge Injection");
        JSpinner spinnerCount = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        // 美化 Spinner 字体
        ((JSpinner.DefaultEditor) spinnerCount.getEditor()).getTextField().setFont(FONT_NORMAL);
        JButton btnBatch = createStyledButton("Spawn on Random Roads", new Color(0, 184, 148)); // 绿色

        //String[] availableRoutes = {"route_0"}; // 你的 XML 里的路线

        btnBatch.addActionListener(e -> {
            int count = (Integer) spinnerCount.getValue();
            Random rng = new Random();

            // 在后台线程执行，防止卡顿界面
            new Thread(() -> {
                for(int i=0; i<count; i++) {
                    String uid = "rand_" + System.currentTimeMillis() + "_" + i;

                    // 2. 从 MAP_EDGES 中随机选一条路
                    String randomEdge = MAP_EDGES[rng.nextInt(MAP_EDGES.length)];

                    // 3. 调用 Manager 的新方法：基于 Edge 注入
                    simManager.injectVehicleOnEdge(uid, randomEdge);

                    try { Thread.sleep(150); } catch (Exception ex) {} // 稍微错开时间
                    //try { Thread.sleep(200); } catch (Exception ex){}
                }
                System.out.println("Batch injection of " + count + " vehicles completed.");
            }).start();
        });

        // 布局批量面板
        batchPanel.add(new JLabel("Count:"));
        batchPanel.add(spinnerCount);
        batchPanel.add(Box.createVerticalStrut(10));
        batchPanel.add(btnBatch);
        batchPanel.add(Box.createVerticalStrut(5));
        JLabel lblHint = new JLabel("<html><small>Spawns vehicles on random<br>edges from map3.net.xml</small></html>");
        lblHint.setForeground(Color.GRAY);
        batchPanel.add(lblHint);

        // 3. 停止按钮
        JButton btnStop = createStyledButton("STOP SIMULATION", new Color(214, 48, 49)); // 红色
        btnStop.addActionListener(e -> {
            simManager.stop();
            btnInject.setEnabled(false);
            btnBatch.setEnabled(false);
            btnStop.setText("STOPPED");
        });

        // 组装左侧
        leftPanel.add(injectPanel);
        leftPanel.add(Box.createVerticalStrut(20));
        leftPanel.add(batchPanel);
        leftPanel.add(Box.createVerticalGlue()); // 弹簧
        leftPanel.add(btnStop);

        frame.add(leftPanel, BorderLayout.WEST);

        // 3. 显示窗口
        frame.setLocationRelativeTo(null); // 居中显示
        frame.setVisible(true);

        // 4. 启动 SUMO 线程
        startSumoThread(simManager, frame);
    }

    // --- 辅助方法：用于创建美观的 UI 组件 ---

    private static JPanel createSidebarPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK); // 深色背景
        p.setPreferredSize(new Dimension(220, 0));
        p.setBorder(new EmptyBorder(15, 15, 15, 15)); // 内边距

        JLabel lblTitle = new JLabel(title);
        lblTitle.setForeground(Color.LIGHT_GRAY);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(lblTitle);
        p.add(Box.createVerticalStrut(20));
        return p;
    }

    private static JPanel createStyledSubPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK); // 保持和侧边栏一致
        // 带标题的边框，白色字体
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title);
        border.setTitleColor(Color.WHITE);
        border.setTitleFont(FONT_TITLE);
        p.setBorder(CompoundBorder(border, new EmptyBorder(10, 10, 10, 10)));
        return p;
    }

    // 自定义组合边框辅助方法
    private static javax.swing.border.Border CompoundBorder(javax.swing.border.Border outside, javax.swing.border.Border inside) {
        return BorderFactory.createCompoundBorder(outside, inside);
    }

    private static JLabel createMetricLabel(String title, String initialValue) {
        JLabel lbl = new JLabel(title + ": " + initialValue);
        lbl.setFont(FONT_METRIC);
        lbl.setForeground(new Color(129, 236, 236)); // 青色字体
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private static JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false); // 扁平化
        btn.setOpaque(true);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35)); // 撑满宽度
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // 辅助布局：简单的 Label + Field 垂直排列
    private static void layoutForm(JPanel panel, String[] labels, JComponent[] fields) {
        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setForeground(Color.LIGHT_GRAY);
            lbl.setFont(FONT_NORMAL);
            lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

            JComponent field = fields[i];
            field.setFont(FONT_NORMAL);
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);

            panel.add(lbl);
            panel.add(Box.createVerticalStrut(3));
            panel.add(field);
            panel.add(Box.createVerticalStrut(8));
        }
    }

    private static void startSumoThread(SimulationManager simManager, JFrame frame) {
        new Thread(() -> {
            try {
                Simulation.preloadLibraries();
                StringVector sv = new StringVector();
                sv.add("sumo-gui");
                sv.add("-c");
                sv.add(SUMO_CONFIG_PATH);
                Simulation.start(sv);

                new Thread(simManager).start();

            } catch (Exception e) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame, "SUMO Startup Error:\n" + e.getMessage())
                );
            }
        }).start();
    }
}
/*
    /**
     * MapPanel: 负责绘制车辆的画布
     * 保持 public static 以便 SimulationManager 可以引用它

    public static class MapPanel extends JPanel {
        // 使用 ConcurrentHashMap 确保多线程读写安全
        private final Map<String, Point2D.Double> vehicles = new ConcurrentHashMap<>();

        /**
         * 更新车辆位置数据
         * @param newData 新的车辆位置映射

        public void updateVehicles(Map<String, Point2D.Double> newData) {
            vehicles.clear();
            vehicles.putAll(newData);
            // 注意：这里不需要调用 repaint()，因为通常由调用者在 EDT 中统一调用，
            // 或者在这里调用 repaint() 也可以，但在 Manager 里统一控制重绘频率更好。
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // 简单的抗锯齿设置，让圆点更圆滑
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2d.setColor(Color.RED);
            for (Point2D.Double p : vehicles.values()) {
                // 简单的坐标缩放 (x2)，实际项目中可能需要更复杂的视口变换
                int x = (int) (p.x * 2);
                int y = (int) (p.y * 2);
                g2d.fillOval(x, y, 8, 8); // 稍微调大一点点
            }
        }
    }

    public static void main(String[] args) {
        // 1. 初始化主窗口
        JFrame frame = new JFrame("Traffic Grid Simulation (Refactored)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1000, 600);
        frame.setLayout(new BorderLayout());

        // 2. 初始化地图面板 (Center)
        MapPanel mapPanel = new MapPanel();
        mapPanel.setBackground(Color.WHITE);
        frame.add(mapPanel, BorderLayout.CENTER);

        // 3. 初始化指标面板 (Right)
        // 这些 Label 现在是局部变量，传递给 Manager 进行更新
        JLabel activeVehiclesLabel = new JLabel("Active Vehicles: 0");
        JLabel avgWaitLabel        = new JLabel("Avg. Wait Time: 0.0 min"); // 预留
        JLabel congestionLabel     = new JLabel("Congestion Index: 0.00");  // 预留

        JPanel metricsPanel = new JPanel();
        metricsPanel.setLayout(new BoxLayout(metricsPanel, BoxLayout.Y_AXIS));
        metricsPanel.setBorder(BorderFactory.createTitledBorder("Metrics"));
        metricsPanel.add(activeVehiclesLabel);
        metricsPanel.add(Box.createVerticalStrut(10));
        metricsPanel.add(avgWaitLabel);
        metricsPanel.add(congestionLabel);

        frame.add(metricsPanel, BorderLayout.EAST);

        // 4. 初始化 SimulationManager (核心逻辑)
        // 将 GUI 组件传给管理器，建立连接
        SimulationManager simManager = new SimulationManager(mapPanel, activeVehiclesLabel);

        // 5. 初始化控制面板 (Left)
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(BorderFactory.createTitledBorder("Controls"));

        // --- 车辆注入区域 ---
        JPanel injectPanel = new JPanel();
        injectPanel.setLayout(new BoxLayout(injectPanel, BoxLayout.Y_AXIS));
        injectPanel.setBorder(BorderFactory.createTitledBorder("Inject Vehicle"));

        JTextField txtId = new JTextField("veh_GUI_01");
        JTextField txtRoute = new JTextField("route_0");
        JButton btnInject = new JButton("Inject Vehicle");

        // 绑定按钮事件 -> 调用 Manager 的方法
        btnInject.addActionListener(e -> {
            System.out.println(">>> 1. 按钮被点击了！准备请求注入..."); // [调试]
            String id = txtId.getText().trim();
            String route = txtRoute.getText().trim();
            if (!id.isEmpty() && !route.isEmpty()) {
                simManager.injectVehicle(id, route);
                // 自动更新 ID 避免重复，方便连续点击测试
                System.out.println(">>> 2. 请求已发送给 Manager"); // [调试]
                txtId.setText("veh_GUI_" + System.currentTimeMillis() % 10000);
            }
        });

        injectPanel.add(new JLabel("Vehicle ID:"));
        injectPanel.add(txtId);
        injectPanel.add(Box.createVerticalStrut(5));
        injectPanel.add(new JLabel("Route ID:"));
        injectPanel.add(txtRoute);
        injectPanel.add(Box.createVerticalStrut(10));
        injectPanel.add(btnInject);

        controlsPanel.add(injectPanel);

        // --- 添加一个停止按钮 (可选) ---
        controlsPanel.add(Box.createVerticalStrut(20));
        JButton btnStop = new JButton("Stop Simulation");
        btnStop.addActionListener(e -> {
            simManager.stop();
            JOptionPane.showMessageDialog(frame, "Simulation stopping...");
        });
        controlsPanel.add(btnStop);

        frame.add(controlsPanel, BorderLayout.WEST);

        // 6. 显示窗口
        frame.setVisible(true);

        // 7. 启动 SUMO 和 模拟线程
        try {
            System.out.println("Loading SUMO libraries...");
            Simulation.preloadLibraries();

            StringVector sv = new StringVector();
            sv.add("sumo-gui");
            sv.add("-c");
            sv.add(SUMO_CONFIG_PATH);

            System.out.println("Starting SUMO process...");
            Simulation.start(sv);

            // 启动负责循环逻辑的工作线程
            System.out.println("Starting Simulation Thread...");
            new Thread(simManager).start();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame,
                    "Failed to start SUMO.\nCheck console for details.\n" + e.getMessage(),
                    "Startup Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }*/
    /*
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
    }
}*/
