package org.example;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Hashtable;

public class TrafficManager {

    public static final double NET_MIN_X = -60.98;
    public static final double NET_MIN_Y = -9.38;
    public static final double NET_MAX_X =  32.32;
    public static final double NET_MAX_Y =  41.31;

    public static final double VIEW_MIN_X = NET_MIN_X;
    public static final double VIEW_MAX_X = NET_MAX_X;
    public static final double VIEW_MIN_Y = -9.38;
    public static final double VIEW_MAX_Y = NET_MAX_Y;

    public static final Color BG_DARK      = new Color(0x0F172A);
    public static final Color BG_PANEL     = new Color(0x111827);
    public static final Color BG_MAP       = new Color(0x020617);
    public static final Color ROAD_DARK    = new Color(0x111827);
    public static final Color ROAD_EDGE    = new Color(0x1F2933);
    public static final Color ROAD_LINE    = new Color(0xFACC15);

    public static final Color ACCENT_BLUE   = new Color(0x3B82F6);
    public static final Color ACCENT_GREEN  = new Color(0x22C55E);
    public static final Color ACCENT_RED    = new Color(0xEF4444);
    public static final Color ACCENT_INDIGO = new Color(0x6366F1);

    public static final Color TEXT_PRIMARY = Color.BLACK;
    public static final Color TEXT_MUTED   = new Color(0x9CA3AF);
    public static final Color BORDER_COL   = new Color(0x1F2937);

    public static JLabel activeVehiclesLabel = new JLabel("Active Vehicles: 0");
    public static JLabel avgWaitLabel        = new JLabel("Avg. Wait Time: 0.0 min");
    public static JLabel congestionLabel     = new JLabel("Congestion Index: 0.00");
    public static JLabel throughputLabel     = new JLabel("Throughput: 0 v/h");
    public static JLabel tlStateLabel        = new JLabel("TL State: ?");

    public void launch() {

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        JFrame frame = new JFrame("Traffic Grid Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1050, 600);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BG_DARK);

        MapPanel panel = new MapPanel();
        panel.setBackground(BG_MAP);
        frame.add(panel, BorderLayout.CENTER);

        Font titleFont  = new Font("SansSerif", Font.BOLD, 14);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(BG_PANEL);
        styleTitledBorder(controls, "Simulation Controls");

        JButton startBtn      = new JButton("Start Simulation");
        JLabel  speedLabel    = new JLabel("Speed Control");
        JSlider speedSlider   = new JSlider(1, 10, 1);
        JButton exportBtn     = new JButton("Export Data");

        Hashtable<Integer, JLabel> speedLabelTable = new Hashtable<>();
        JLabel minL = new JLabel("1x");
        JLabel maxL = new JLabel("10x");
        minL.setForeground(Color.WHITE);
        maxL.setForeground(Color.WHITE);
        speedLabelTable.put(1, minL);
        speedLabelTable.put(10, maxL);
        speedSlider.setLabelTable(speedLabelTable);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(false);
        speedSlider.setBackground(BG_PANEL);
        speedSlider.setForeground(Color.WHITE);

        JLabel laneLabel = new JLabel("Select incoming lane");
        JToggleButton lane1Toggle = new JToggleButton("Lane 1 (from West)");
        JToggleButton lane2Toggle = new JToggleButton("Lane 2 (from East)");
        JToggleButton lane3Toggle = new JToggleButton("Lane 3 (from South)");
        JToggleButton lane4Toggle = new JToggleButton("Lane 4 (from North)");

        ButtonGroup laneGroup = new ButtonGroup();
        laneGroup.add(lane1Toggle);
        laneGroup.add(lane2Toggle);
        laneGroup.add(lane3Toggle);
        laneGroup.add(lane4Toggle);
        lane1Toggle.setSelected(true);

        JLabel vehicleTypeLabel = new JLabel("Vehicle Type");
        JToggleButton carToggle   = new JToggleButton("Car");
        JToggleButton truckToggle = new JToggleButton("Truck");
        JToggleButton busToggle   = new JToggleButton("Bus");
        ButtonGroup typeGroup = new ButtonGroup();
        typeGroup.add(carToggle);
        typeGroup.add(truckToggle);
        typeGroup.add(busToggle);
        carToggle.setSelected(true);

        JLabel  tlLabel        = new JLabel("Traffic Light");
        JButton tlGreenBtn     = new JButton("TL: Green");
        JButton tlRedBtn       = new JButton("TL: Red");

        speedLabel.setForeground(Color.WHITE);
        speedLabel.setFont(titleFont);
        laneLabel.setForeground(Color.WHITE);
        laneLabel.setFont(titleFont);
        vehicleTypeLabel.setForeground(Color.WHITE);
        vehicleTypeLabel.setFont(titleFont);
        tlLabel.setForeground(Color.WHITE);
        tlLabel.setFont(titleFont);

        stylePrimaryButton(startBtn, ACCENT_GREEN);
        stylePrimaryButton(exportBtn, ACCENT_INDIGO);

        stylePrimaryButton(lane1Toggle, ACCENT_BLUE);
        stylePrimaryButton(lane2Toggle, ACCENT_BLUE);
        stylePrimaryButton(lane3Toggle, ACCENT_BLUE);
        stylePrimaryButton(lane4Toggle, ACCENT_BLUE);

        stylePrimaryButton(carToggle,   ACCENT_BLUE);
        stylePrimaryButton(truckToggle, ACCENT_BLUE);
        stylePrimaryButton(busToggle,   ACCENT_BLUE);

        stylePrimaryButton(tlGreenBtn, ACCENT_GREEN);
        stylePrimaryButton(tlRedBtn,   ACCENT_RED);

        controls.add(Box.createVerticalStrut(8));
        controls.add(startBtn);
        controls.add(Box.createVerticalStrut(12));

        controls.add(speedLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(speedSlider);
        controls.add(Box.createVerticalStrut(12));
        controls.add(exportBtn);
        controls.add(Box.createVerticalStrut(12));

        controls.add(laneLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(lane1Toggle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(lane2Toggle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(lane3Toggle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(lane4Toggle);
        controls.add(Box.createVerticalStrut(12));

        JLabel numVehiclesLabel = new JLabel("Number of vehicles");
        JTextField numVehiclesField = new JTextField("1");

        numVehiclesLabel.setForeground(Color.WHITE);
        numVehiclesLabel.setFont(titleFont);
        numVehiclesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        numVehiclesField.setFont(new Font("SansSerif", Font.BOLD, 16));
        numVehiclesField.setMaximumSize(new Dimension(200, 35));
        numVehiclesField.setPreferredSize(new Dimension(200, 35));
        numVehiclesField.setMinimumSize(new Dimension(200, 30));
        numVehiclesField.setAlignmentX(Component.LEFT_ALIGNMENT);

        controls.add(numVehiclesLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(numVehiclesField);
        controls.add(Box.createVerticalStrut(12));

        controls.add(vehicleTypeLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(carToggle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(truckToggle);
        controls.add(Box.createVerticalStrut(4));
        controls.add(busToggle);
        controls.add(Box.createVerticalStrut(12));

        controls.add(tlLabel);
        controls.add(Box.createVerticalStrut(4));
        controls.add(tlGreenBtn);
        controls.add(Box.createVerticalStrut(4));
        controls.add(tlRedBtn);
        controls.add(Box.createVerticalGlue());

        frame.add(controls, BorderLayout.WEST);

        JPanel metrics = new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        metrics.setBackground(BG_PANEL);
        styleTitledBorder(metrics, "Metrics");

        Font metricsFont = new Font("SansSerif", Font.BOLD, 12);

        activeVehiclesLabel.setForeground(Color.WHITE);
        avgWaitLabel.setForeground(Color.WHITE);
        congestionLabel.setForeground(Color.WHITE);
        throughputLabel.setForeground(Color.WHITE);
        tlStateLabel.setForeground(Color.WHITE);

        activeVehiclesLabel.setFont(metricsFont);
        avgWaitLabel.setFont(metricsFont);
        congestionLabel.setFont(metricsFont);
        throughputLabel.setFont(metricsFont);
        tlStateLabel.setFont(metricsFont);

        metrics.add(Box.createVerticalStrut(8));
        metrics.add(activeVehiclesLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(avgWaitLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(congestionLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(throughputLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(tlStateLabel);
        metrics.add(Box.createVerticalGlue());

        frame.add(metrics, BorderLayout.EAST);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        SumoConnector connector = new SumoConnector(
                panel,
                activeVehiclesLabel,
                avgWaitLabel,
                congestionLabel,
                throughputLabel,
                tlStateLabel
        );

        VehicleManager vehicleManager = new VehicleManager(connector);
        TrafficLightController tlController = new TrafficLightController(connector);

        startBtn.addActionListener(e -> {
            connector.startSimulation();
            startBtn.setEnabled(false);
        });

        speedSlider.addChangeListener(e -> {
            int factor = speedSlider.getValue();
            int delayMs = 110 - 10 * factor;
            connector.setSpeedDelay(delayMs);
        });

        java.util.function.Supplier<String> laneKeySupplier = () -> {
            if (lane1Toggle.isSelected()) {
                return SumoConnector.LANE1;
            } else if (lane2Toggle.isSelected()) {
                return SumoConnector.LANE2;
            } else if (lane3Toggle.isSelected()) {
                return SumoConnector.LANE3;
            } else {
                return SumoConnector.LANE4;
            }
        };

        java.util.function.Supplier<Integer> numVehiclesSupplier = () -> {
            try {
                String txt = numVehiclesField.getText().trim();
                int n = Integer.parseInt(txt);
                return Math.max(1, n);
            } catch (Exception ex) {
                return 1;
            }
        };

        carToggle.addActionListener(e -> {
            String laneKey = laneKeySupplier.get();
            int n = numVehiclesSupplier.get();
            vehicleManager.spawnVehicles(laneKey, SumoConnector.TYPE_CAR, n);
        });

        truckToggle.addActionListener(e -> {
            String laneKey = laneKeySupplier.get();
            int n = numVehiclesSupplier.get();
            vehicleManager.spawnVehicles(laneKey, SumoConnector.TYPE_TRUCK, n);
        });

        busToggle.addActionListener(e -> {
            String laneKey = laneKeySupplier.get();
            int n = numVehiclesSupplier.get();
            vehicleManager.spawnVehicles(laneKey, SumoConnector.TYPE_BUS, n);
        });

        tlGreenBtn.addActionListener(e -> {
            tlController.setGreen();
            tlStateLabel.setText("TL State: phase 0 (green)");
        });

        tlRedBtn.addActionListener(e -> {
            tlController.setRed();
            tlStateLabel.setText("TL State: phase 2 (red)");
        });

        exportBtn.addActionListener(e -> JOptionPane.showMessageDialog(
                frame,
                "Export not implemented yet.\n(Use this later for CSV/PDF reports.)",
                "Info",
                JOptionPane.INFORMATION_MESSAGE
        ));
    }

    private static void stylePrimaryButton(AbstractButton b, Color bg) {
        b.setBackground(bg);
        b.setForeground(TEXT_PRIMARY);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
    }

    private static void styleTitledBorder(JPanel panel, String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COL),
                title,
                TitledBorder.LEADING,
                TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13),
                TEXT_MUTED
        );
        panel.setBorder(tb);
    }
}
