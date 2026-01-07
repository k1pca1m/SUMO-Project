// GUI.java
package org.example;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Hashtable;
import java.util.Locale;

public class GUI {

    public static final Color BG_DARK = new Color(0x0F172A);
    public static final Color BG_PANEL = new Color(0x111827);
    public static final Color ACCENT_BLUE = new Color(0x3B82F6);
    public static final Color ACCENT_GREEN = new Color(0x22C55E);
    public static final Color ACCENT_RED = new Color(0xEF4444);
    public static final Color TEXT_MUTED = new Color(0x9CA3AF);
    public static final Color BORDER_COL = new Color(0x1F2937);

    // labels
    private final JLabel activeVehiclesLabel = new JLabel("Active Vehicles (all): 0");
    private final JLabel visibleVehiclesLabel = new JLabel("Visible Vehicles (filtered): 0");
    private final JLabel byTypeLabel = new JLabel("By Type: car=0 truck=0 bus=0");

    private final JLabel avgWaitLabel = new JLabel("Avg Wait Time: 0.0 s");
    private final JLabel congestionLabel = new JLabel("Congestion Index: 0.00");
    private final JLabel throughputLabel = new JLabel("Throughput: 0 v/h");
    private final JLabel meanSpeedLabel = new JLabel("Mean Speed: 0.0 m/s");

    private final JLabel tlStateLabel = new JLabel("TL State: -");

    // components
    private JComboBox<VehicleInjection.RouteDef> routeCombo;
    private JComboBox<TrafficControl.TlsItem> tlCombo;

    private final MapVisualisation.VehicleFilter FILTER = new MapVisualisation.VehicleFilter();

    private LiveConnectionSumo controller;

    private static void stylePrimaryButton(AbstractButton b, Color bg) {
        b.setBackground(bg);
        b.setForeground(Color.BLACK);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static void styleSectionLabel(JLabel l, Font f) {
        l.setForeground(Color.WHITE);
        l.setFont(f);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private static void styleTitledBorder(JPanel panel, String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COL),
                title, TitledBorder.LEADING, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13), TEXT_MUTED
        );
        panel.setBorder(BorderFactory.createCompoundBorder(
                tb, BorderFactory.createEmptyBorder(10, 18, 14, 18)));
    }

    private static JCheckBox styleCheckBox(JCheckBox cb) {
        cb.setForeground(Color.WHITE);
        cb.setBackground(BG_PANEL);
        cb.setFocusPainted(false);
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
        cb.setFont(new Font("SansSerif", Font.BOLD, 12));
        return cb;
    }

    public void show(String sumocfgPath) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception e) { Logging.LOG.warning("LookAndFeel set failed: " + e.getMessage()); }

        JFrame frame = new JFrame("Traffic Grid Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1220, 680);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(BG_DARK);

        MapVisualisation.MapPanel mapPanel = new MapVisualisation.MapPanel(FILTER);
        mapPanel.setBackground(Color.WHITE);
        frame.add(mapPanel, BorderLayout.CENTER);

        Font titleFont = new Font("SansSerif", Font.BOLD, 14);

        // ===== Left controls =====
        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(BG_PANEL);
        styleTitledBorder(controls, "Simulation Controls");
        controls.setPreferredSize(new Dimension(430, 0));

        JButton startBtn = new JButton("Start Simulation");
        JButton stopBtn = new JButton("Stop Simulation");

        JLabel speedLabel = new JLabel("Speed Control");
        JSlider simSpeedSlider = new JSlider(1, 10, 1);
        simSpeedSlider.setMajorTickSpacing(1);
        simSpeedSlider.setPaintTicks(true);

        Hashtable<Integer, JLabel> speedLabelTable = new Hashtable<>();
        JLabel minL = new JLabel("1x"), maxL = new JLabel("10x");
        minL.setForeground(Color.WHITE); maxL.setForeground(Color.WHITE);
        speedLabelTable.put(1, minL); speedLabelTable.put(10, maxL);
        simSpeedSlider.setLabelTable(speedLabelTable);
        simSpeedSlider.setPaintLabels(true);
        simSpeedSlider.setBackground(BG_PANEL);
        simSpeedSlider.setForeground(Color.WHITE);
        simSpeedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        simSpeedSlider.setMaximumSize(new Dimension(300, 40));

        JButton exportBtn = new JButton("Export Data");
        JButton exportPdfBtn = new JButton("Export PDF");

        JLabel routeLabel = new JLabel("Select route");
        routeCombo = new JComboBox<>();
        routeCombo.setEnabled(false);
        routeCombo.setMaximumSize(new Dimension(350, 34));
        routeCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel numVehiclesLabel = new JLabel("Number of vehicles");
        JTextField numVehiclesField = new JTextField("10");
        numVehiclesField.setFont(new Font("SansSerif", Font.BOLD, 16));
        numVehiclesField.setAlignmentX(Component.LEFT_ALIGNMENT);
        numVehiclesField.setMaximumSize(new Dimension(190, 30));

        JLabel vehicleTypeLabel = new JLabel("Vehicle Type (Spawn)");
        JButton carBtn = new JButton("Car");
        JButton truckBtn = new JButton("Truck");
        JButton busBtn = new JButton("Bus");

        JPanel vehicleTypePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        vehicleTypePanel.setBackground(BG_PANEL);
        vehicleTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        vehicleTypePanel.add(carBtn);
        vehicleTypePanel.add(truckBtn);
        vehicleTypePanel.add(busBtn);

        // ===== Filter UI =====
        JLabel filterTitle = new JLabel("View Filters (Map)");
        JCheckBox showCars = styleCheckBox(new JCheckBox("Show Cars", true));
        JCheckBox showTrucks = styleCheckBox(new JCheckBox("Show Trucks", true));
        JCheckBox showBuses = styleCheckBox(new JCheckBox("Show Buses", true));

        JLabel minSpeedTitle = new JLabel("Min Vehicle Speed Filter");
        JLabel minSpeedValue = new JLabel(">= 0.0 m/s (0 km/h)");
        minSpeedValue.setForeground(new Color(220,220,220));
        minSpeedValue.setFont(new Font("SansSerif", Font.PLAIN, 12));
        minSpeedValue.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSlider minSpeedSlider = new JSlider(0, 35, 0);
        minSpeedSlider.setMajorTickSpacing(5);
        minSpeedSlider.setPaintTicks(true);
        minSpeedSlider.setBackground(BG_PANEL);
        minSpeedSlider.setForeground(Color.WHITE);
        minSpeedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        minSpeedSlider.setMaximumSize(new Dimension(300, 42));

        // ===== TLS UI =====
        JLabel tlLabel = new JLabel("Traffic Light");
        tlCombo = new JComboBox<>();
        tlCombo.setEnabled(false);
        tlCombo.setMaximumSize(new Dimension(350, 34));
        tlCombo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton tlRedBtn = new JButton("Red");
        JButton tlGreenBtn = new JButton("Green");
        JButton tlResetBtn = new JButton("Reset");

        JPanel tlButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        tlButtonPanel.setBackground(BG_PANEL);
        tlButtonPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tlButtonPanel.add(tlRedBtn);
        tlButtonPanel.add(tlGreenBtn);
        tlButtonPanel.add(tlResetBtn);

        styleSectionLabel(speedLabel, titleFont);
        styleSectionLabel(routeLabel, titleFont);
        styleSectionLabel(numVehiclesLabel, titleFont);
        styleSectionLabel(vehicleTypeLabel, titleFont);
        styleSectionLabel(filterTitle, titleFont);
        styleSectionLabel(minSpeedTitle, new Font("SansSerif", Font.BOLD, 13));
        styleSectionLabel(tlLabel, titleFont);

        stylePrimaryButton(startBtn, ACCENT_GREEN);
        stylePrimaryButton(stopBtn, ACCENT_RED);
        stylePrimaryButton(exportBtn, ACCENT_BLUE);
        stylePrimaryButton(carBtn, ACCENT_BLUE);
        stylePrimaryButton(truckBtn, ACCENT_BLUE);
        stylePrimaryButton(busBtn, ACCENT_BLUE);

        stylePrimaryButton(tlRedBtn, ACCENT_RED);
        stylePrimaryButton(tlGreenBtn, ACCENT_GREEN);
        stylePrimaryButton(tlResetBtn, ACCENT_BLUE);

        controls.add(Box.createVerticalStrut(8));  controls.add(startBtn);
        controls.add(Box.createVerticalStrut(8));  controls.add(stopBtn);

        controls.add(Box.createVerticalStrut(12)); controls.add(speedLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(simSpeedSlider);

        controls.add(Box.createVerticalStrut(10)); controls.add(exportBtn);
        controls.add(Box.createVerticalStrut(6));  controls.add(exportPdfBtn);

        controls.add(Box.createVerticalStrut(14)); controls.add(routeLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(routeCombo);

        controls.add(Box.createVerticalStrut(14)); controls.add(numVehiclesLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(numVehiclesField);

        controls.add(Box.createVerticalStrut(14)); controls.add(vehicleTypeLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(vehicleTypePanel);

        controls.add(Box.createVerticalStrut(16)); controls.add(filterTitle);
        controls.add(Box.createVerticalStrut(6));  controls.add(showCars);
        controls.add(Box.createVerticalStrut(2));  controls.add(showTrucks);
        controls.add(Box.createVerticalStrut(2));  controls.add(showBuses);

        controls.add(Box.createVerticalStrut(10)); controls.add(minSpeedTitle);
        controls.add(Box.createVerticalStrut(4));  controls.add(minSpeedValue);
        controls.add(Box.createVerticalStrut(4));  controls.add(minSpeedSlider);

        controls.add(Box.createVerticalStrut(16)); controls.add(tlLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlCombo);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlButtonPanel);
        controls.add(Box.createVerticalGlue());

        frame.add(controls, BorderLayout.WEST);

        // ===== Right metrics + chart =====
        JPanel metrics = new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        metrics.setBackground(BG_PANEL);
        styleTitledBorder(metrics, "Metrics");

        Font metricsFont = new Font("SansSerif", Font.BOLD, 12);
        for (JLabel l : new JLabel[]{
                activeVehiclesLabel, visibleVehiclesLabel, byTypeLabel,
                avgWaitLabel, congestionLabel, throughputLabel, meanSpeedLabel, tlStateLabel
        }) { l.setForeground(Color.WHITE); l.setFont(metricsFont); }

        MapVisualisation.TrendChartPanel trendChart = new MapVisualisation.TrendChartPanel(120);
        trendChart.setAlignmentX(Component.LEFT_ALIGNMENT);

        metrics.add(Box.createVerticalStrut(6));
        metrics.add(trendChart);
        metrics.add(Box.createVerticalStrut(10));

        metrics.add(activeVehiclesLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(visibleVehiclesLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(byTypeLabel);

        metrics.add(Box.createVerticalStrut(10));
        metrics.add(avgWaitLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(congestionLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(throughputLabel);
        metrics.add(Box.createVerticalStrut(6));
        metrics.add(meanSpeedLabel);

        metrics.add(Box.createVerticalStrut(12));
        metrics.add(tlStateLabel);
        metrics.add(Box.createVerticalGlue());

        frame.add(metrics, BorderLayout.EAST);

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        Runnable onStopped = () -> {
            frame.dispose();
            System.exit(0);
        };

        controller = new LiveConnectionSumo(
                sumocfgPath,
                frame,
                mapPanel,
                trendChart,
                FILTER,
                activeVehiclesLabel, visibleVehiclesLabel, byTypeLabel,
                avgWaitLabel, congestionLabel, throughputLabel, meanSpeedLabel,
                tlStateLabel,
                routeCombo,
                tlCombo,
                onStopped
        );

        new Thread(controller, "SUMO-Simulation-Thread").start();

        // ===== UI actions =====
        startBtn.addActionListener(e -> { controller.startSimulation(); startBtn.setEnabled(false); });
        stopBtn.addActionListener(e -> controller.stopSimulation());

        simSpeedSlider.addChangeListener(e -> {
            int factor = simSpeedSlider.getValue();
            int delayMs = 110 - 10 * factor;
            controller.setSpeedDelay(delayMs, factor);
            Logging.LOG.info("Sim speed set: " + factor + "x (delay=" + delayMs + "ms)");
        });

        exportBtn.addActionListener(e -> {
            Object sel = routeCombo.getSelectedItem();
            String routeName = (sel instanceof VehicleInjection.RouteDef) ? ((VehicleInjection.RouteDef) sel).name : "";
            controller.exportMetricsCsv(frame, routeName);
        });

        exportPdfBtn.addActionListener(e -> {
            Object sel = routeCombo.getSelectedItem();
            String routeName = (sel instanceof VehicleInjection.RouteDef) ? ((VehicleInjection.RouteDef) sel).name : "";
            BufferedImage chartImg = MapVisualisation.renderComponentToImage(trendChart, 900, 320);
            controller.exportSummaryPdf(frame, routeName, chartImg);
        });

        java.util.function.Supplier<Integer> numVehiclesSupplier = () -> {
            try { return Math.max(1, Integer.parseInt(numVehiclesField.getText().trim())); }
            catch (Exception ex) { return 1; }
        };

        carBtn.addActionListener(e -> controller.getVehicleInjection().injectVehicles(
                frame, controller.isReady(),
                (VehicleInjection.RouteDef) routeCombo.getSelectedItem(),
                LiveConnectionSumo.TYPE_CAR, numVehiclesSupplier.get()
        ));
        truckBtn.addActionListener(e -> controller.getVehicleInjection().injectVehicles(
                frame, controller.isReady(),
                (VehicleInjection.RouteDef) routeCombo.getSelectedItem(),
                LiveConnectionSumo.TYPE_TRUCK, numVehiclesSupplier.get()
        ));
        busBtn.addActionListener(e -> controller.getVehicleInjection().injectVehicles(
                frame, controller.isReady(),
                (VehicleInjection.RouteDef) routeCombo.getSelectedItem(),
                LiveConnectionSumo.TYPE_BUS, numVehiclesSupplier.get()
        ));

        tlCombo.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) tlCombo.getSelectedItem();
            controller.getTrafficControl().setSelectedTls(item == null ? null : item.id);
        });

        tlRedBtn.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) tlCombo.getSelectedItem();
            String tlsId = (item == null) ? null : item.id;
            if (tlsId != null) controller.getTrafficControl().forceTrafficLightRed(tlsId, tlStateLabel);
        });

        tlGreenBtn.addActionListener(e -> {
            TrafficControl.TlsItem item = (TrafficControl.TlsItem) tlCombo.getSelectedItem();
            String tlsId = (item == null) ? null : item.id;
            if (tlsId != null) controller.getTrafficControl().forceTrafficLightGreen(tlsId, tlStateLabel);
        });

        tlResetBtn.addActionListener(e -> controller.getTrafficControl().resetAllForcedTrafficLights(tlStateLabel));

        showCars.addActionListener(e -> FILTER.showCars = showCars.isSelected());
        showTrucks.addActionListener(e -> FILTER.showTrucks = showTrucks.isSelected());
        showBuses.addActionListener(e -> FILTER.showBuses = showBuses.isSelected());

        minSpeedSlider.addChangeListener(e -> {
            int v = minSpeedSlider.getValue();
            FILTER.minSpeedMps = v;
            double kmh = v * 3.6;
            minSpeedValue.setText(String.format(Locale.US, ">= %.1f m/s (%.0f km/h)", (double)v, kmh));
        });

        Logging.LOG.info("UI ready. Dropdown will populate after SUMO starts.");
    }
}
