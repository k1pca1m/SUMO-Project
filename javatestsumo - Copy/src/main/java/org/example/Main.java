package org.example;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.util.Hashtable;
import java.util.logging.Logger;

public class Main {

    static final Logger LOG = Logging.log();

    static JLabel activeVehiclesLabel=new JLabel("Active Vehicles: 0");
    static JLabel avgWaitLabel=new JLabel("Avg. Wait Time: 0.0 min");
    static JLabel congestionLabel=new JLabel("Congestion Index: 0.00");
    static JLabel throughputLabel=new JLabel("Throughput: 0 v/h");
    static JLabel tlStateLabel=new JLabel("TL State: ?");

    private static void stylePrimaryButton(AbstractButton b, Color bg) {
        b.setBackground(bg); b.setForeground(CommonConfig.TEXT_PRIMARY);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6,14,6,14));
        b.setFont(new Font("SansSerif", Font.BOLD, 13));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true); b.setContentAreaFilled(true);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
    private static void fixButtonSize(AbstractButton b,int w,int h){
        Dimension d=new Dimension(w,h);
        b.setPreferredSize(d); b.setMinimumSize(d); b.setMaximumSize(d);
    }
    private static void styleSectionLabel(JLabel l, Font f){
        l.setForeground(Color.WHITE); l.setFont(f);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(0,0,0,0));
    }
    private static void styleTitledBorder(JPanel panel, String title){
        TitledBorder tb=BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(CommonConfig.BORDER_COL),
                title, TitledBorder.LEADING, TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13), CommonConfig.TEXT_MUTED
        );
        panel.setBorder(BorderFactory.createCompoundBorder(tb, BorderFactory.createEmptyBorder(10,18,14,18)));
    }

    public static void main(String[] args) {

        Logging.initOnce();
        LOG.info("App boot.");

        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception e) { LOG.warning("LookAndFeel set failed: "+e.getMessage()); }

        JFrame frame=new JFrame("Traffic Grid Simulation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200,650);
        frame.setLayout(new BorderLayout());
        frame.getContentPane().setBackground(CommonConfig.BG_DARK);

        MapPanel panel=new MapPanel();
        panel.setBackground(CommonConfig.BG_MAP);
        frame.add(panel, BorderLayout.CENTER);

        Font titleFont=new Font("SansSerif", Font.BOLD, 14);

        JPanel controls=new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.setBackground(CommonConfig.BG_PANEL);
        styleTitledBorder(controls, "Simulation Controls");
        controls.setPreferredSize(new Dimension(320,0));

        JButton startBtn=new JButton("Start Simulation");
        JButton stopBtn=new JButton("Stop Simulation");
        JLabel speedLabel=new JLabel("Speed Control");
        JSlider speedSlider=new JSlider(1,10,1);
        JButton exportBtn=new JButton("Export Data");

        Hashtable<Integer,JLabel> speedLabelTable=new Hashtable<>();
        JLabel minL=new JLabel("1x"), maxL=new JLabel("10x");
        minL.setForeground(Color.WHITE); maxL.setForeground(Color.WHITE);
        speedLabelTable.put(1,minL); speedLabelTable.put(10,maxL);
        speedSlider.setLabelTable(speedLabelTable);
        speedSlider.setPaintLabels(true);
        speedSlider.setPaintTicks(false);
        speedSlider.setBackground(CommonConfig.BG_PANEL);
        speedSlider.setForeground(Color.WHITE);
        speedSlider.setAlignmentX(Component.LEFT_ALIGNMENT);
        speedSlider.setMaximumSize(new Dimension(220,40));

        JLabel laneLabel=new JLabel("Select incoming lane");
        JToggleButton lane1Toggle=new JToggleButton("Lane 1 (from West)");
        JToggleButton lane2Toggle=new JToggleButton("Lane 2 (from East)");
        JToggleButton lane3Toggle=new JToggleButton("Lane 3 (from South)");
        JToggleButton lane4Toggle=new JToggleButton("Lane 4 (from North)");
        ButtonGroup laneGroup=new ButtonGroup();
        for(JToggleButton b: new JToggleButton[]{lane1Toggle,lane2Toggle,lane3Toggle,lane4Toggle}) laneGroup.add(b);
        lane1Toggle.setSelected(true);

        JLabel numVehiclesLabel=new JLabel("Number of vehicles");
        JTextField numVehiclesField=new JTextField("1");
        numVehiclesField.setFont(new Font("SansSerif", Font.BOLD, 16));
        numVehiclesField.setAlignmentX(Component.LEFT_ALIGNMENT);
        numVehiclesField.setMaximumSize(new Dimension(220,30));

        JLabel vehicleTypeLabel=new JLabel("Vehicle Type");
        JToggleButton carToggle=new JToggleButton("Car");
        JToggleButton truckToggle=new JToggleButton("Truck");
        JToggleButton busToggle=new JToggleButton("Bus");
        ButtonGroup typeGroup=new ButtonGroup();
        for(JToggleButton b: new JToggleButton[]{carToggle,truckToggle,busToggle}) typeGroup.add(b);
        carToggle.setSelected(true);

        JPanel vehicleTypePanel=new JPanel(new FlowLayout(FlowLayout.LEFT,8,0));
        vehicleTypePanel.setBackground(CommonConfig.BG_PANEL);
        vehicleTypePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tlLabel=new JLabel("Traffic Light Control");
        JPanel tlPickPanel=new JPanel(new GridLayout(1,4,8,0));
        tlPickPanel.setBackground(CommonConfig.BG_PANEL);
        tlPickPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JToggleButton t1Btn=new JToggleButton("T1");
        JToggleButton t2Btn=new JToggleButton("T2");
        JToggleButton t3Btn=new JToggleButton("T3");
        JToggleButton t4Btn=new JToggleButton("T4");
        ButtonGroup tlPickGroup=new ButtonGroup();
        for(JToggleButton b: new JToggleButton[]{t1Btn,t2Btn,t3Btn,t4Btn}) tlPickGroup.add(b);
        t1Btn.setSelected(true);

        JButton tlGreenBtn=new JButton("Force Green");
        JButton tlRedBtn=new JButton("Force Red");
        JButton tlResetBtn=new JButton("Reset (Normal)");

        styleSectionLabel(speedLabel, titleFont);
        styleSectionLabel(laneLabel, titleFont);
        styleSectionLabel(numVehiclesLabel, titleFont);
        styleSectionLabel(vehicleTypeLabel, titleFont);
        styleSectionLabel(tlLabel, titleFont);

        stylePrimaryButton(startBtn, CommonConfig.ACCENT_GREEN);
        stylePrimaryButton(stopBtn, CommonConfig.ACCENT_RED);
        stylePrimaryButton(exportBtn, CommonConfig.ACCENT_INDIGO);

        for(JToggleButton b: new JToggleButton[]{lane1Toggle,lane2Toggle,lane3Toggle,lane4Toggle}) stylePrimaryButton(b, CommonConfig.ACCENT_BLUE);
        int laneW=220, laneH=30;
        for(JToggleButton b: new JToggleButton[]{lane1Toggle,lane2Toggle,lane3Toggle,lane4Toggle}) fixButtonSize(b, laneW, laneH);

        for(JToggleButton b: new JToggleButton[]{carToggle,truckToggle,busToggle}) stylePrimaryButton(b, CommonConfig.ACCENT_BLUE);
        for(JToggleButton b: new JToggleButton[]{t1Btn,t2Btn,t3Btn,t4Btn}) stylePrimaryButton(b, CommonConfig.ACCENT_BLUE);

        stylePrimaryButton(tlGreenBtn, CommonConfig.ACCENT_GREEN);
        stylePrimaryButton(tlRedBtn, CommonConfig.ACCENT_RED);
        stylePrimaryButton(tlResetBtn, CommonConfig.ACCENT_INDIGO);

        for(JToggleButton b: new JToggleButton[]{carToggle,truckToggle,busToggle}) vehicleTypePanel.add(b);
        for(JToggleButton b: new JToggleButton[]{t1Btn,t2Btn,t3Btn,t4Btn}) tlPickPanel.add(b);

        controls.add(Box.createVerticalStrut(8));  controls.add(startBtn);
        controls.add(Box.createVerticalStrut(8));  controls.add(stopBtn);
        controls.add(Box.createVerticalStrut(12)); controls.add(speedLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(speedSlider);
        controls.add(Box.createVerticalStrut(12)); controls.add(exportBtn);
        controls.add(Box.createVerticalStrut(14)); controls.add(laneLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(lane1Toggle);
        controls.add(Box.createVerticalStrut(6));  controls.add(lane2Toggle);
        controls.add(Box.createVerticalStrut(6));  controls.add(lane3Toggle);
        controls.add(Box.createVerticalStrut(6));  controls.add(lane4Toggle);
        controls.add(Box.createVerticalStrut(14)); controls.add(numVehiclesLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(numVehiclesField);
        controls.add(Box.createVerticalStrut(14)); controls.add(vehicleTypeLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(vehicleTypePanel);
        controls.add(Box.createVerticalStrut(14)); controls.add(tlLabel);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlPickPanel);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlGreenBtn);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlRedBtn);
        controls.add(Box.createVerticalStrut(6));  controls.add(tlResetBtn);
        controls.add(Box.createVerticalStrut(24)); controls.add(Box.createVerticalGlue());

        frame.add(controls, BorderLayout.WEST);

        JPanel metrics=new JPanel();
        metrics.setLayout(new BoxLayout(metrics, BoxLayout.Y_AXIS));
        metrics.setBackground(CommonConfig.BG_PANEL);
        styleTitledBorder(metrics, "Metrics");

        Font metricsFont=new Font("SansSerif", Font.BOLD, 12);
        for(JLabel l: new JLabel[]{activeVehiclesLabel,avgWaitLabel,congestionLabel,throughputLabel,tlStateLabel}){
            l.setForeground(Color.WHITE); l.setFont(metricsFont);
        }

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

        Runnable onStopped = () -> {
            LOG.info("UI closing; exit.");
            frame.dispose();
            System.exit(0);
        };

        TrafficManager tm=new TrafficManager(panel,
                activeVehiclesLabel, avgWaitLabel, congestionLabel, throughputLabel, tlStateLabel,
                onStopped
        );

        startBtn.addActionListener(e -> { tm.startSimulation(); startBtn.setEnabled(false); });
        stopBtn.addActionListener(e -> tm.stopSimulation());

        speedSlider.addChangeListener(e -> {
            int factor=speedSlider.getValue();
            int delayMs=110-10*factor;
            tm.setSpeedDelay(delayMs);
            LOG.info("Speed set: " + factor + "x (delay=" + delayMs + "ms)");
        });

        java.util.function.Supplier<String[]> selectedRoutesSupplier = () -> {
            if(lane1Toggle.isSelected()) return CommonConfig.ROUTES_LANE1;
            if(lane2Toggle.isSelected()) return CommonConfig.ROUTES_LANE2;
            if(lane3Toggle.isSelected()) return CommonConfig.ROUTES_LANE3;
            return CommonConfig.ROUTES_LANE4;
        };

        java.util.function.Supplier<Integer> numVehiclesSupplier = () -> {
            try { return Math.max(1, Integer.parseInt(numVehiclesField.getText().trim())); }
            catch (Exception ex) { LOG.warning("Invalid vehicle count: " + numVehiclesField.getText()); return 1; }
        };

        carToggle.addActionListener(e -> tm.injectN(CommonConfig.TYPE_CAR, selectedRoutesSupplier.get(), numVehiclesSupplier.get()));
        truckToggle.addActionListener(e -> tm.injectN(CommonConfig.TYPE_TRUCK, selectedRoutesSupplier.get(), numVehiclesSupplier.get()));
        busToggle.addActionListener(e -> tm.injectN(CommonConfig.TYPE_BUS, selectedRoutesSupplier.get(), numVehiclesSupplier.get()));

        java.util.function.Supplier<TrafficLightControl.TlTarget> selectedTlTargetSupplier = () -> {
            if(t1Btn.isSelected()) return TrafficLightControl.TlTarget.T1_WEST;
            if(t2Btn.isSelected()) return TrafficLightControl.TlTarget.T2_EAST;
            if(t3Btn.isSelected()) return TrafficLightControl.TlTarget.T3_SOUTH;
            if(t4Btn.isSelected()) return TrafficLightControl.TlTarget.T4_NORTH;
            return TrafficLightControl.TlTarget.T1_WEST;
        };

        tlGreenBtn.addActionListener(e -> tm.getTrafficLightControl()
                .forceSelected(selectedTlTargetSupplier.get(), TrafficLightControl.OverrideMode.FORCE_GREEN));
        tlRedBtn.addActionListener(e -> tm.getTrafficLightControl()
                .forceSelected(selectedTlTargetSupplier.get(), TrafficLightControl.OverrideMode.FORCE_RED));
        tlResetBtn.addActionListener(e -> tm.getTrafficLightControl().resetToNormal(new SumoConnector())); // safe reset call

        exportBtn.addActionListener(e -> {
            if(tm.getMetricsLog().isEmpty()){
                LOG.warning("Export requested but no data yet.");
                JOptionPane.showMessageDialog(frame, "No data recorded yet.\nStart the simulation first.",
                        "Export Data", JOptionPane.WARNING_MESSAGE);
                return;
            }

            JFileChooser chooser=new JFileChooser();
            chooser.setDialogTitle("Save simulation_export.csv");
            chooser.setSelectedFile(new File("simulation_export.csv"));
            int result=chooser.showSaveDialog(frame);

            if(result==JFileChooser.APPROVE_OPTION){
                File file=chooser.getSelectedFile();
                if(!file.getName().toLowerCase().endsWith(".csv")) file=new File(file.getParentFile(), file.getName()+".csv");

                LOG.info("Exporting CSV -> " + file.getAbsolutePath());
                try{
                    tm.exportCSV(file);
                    JOptionPane.showMessageDialog(frame, "Data exported to:\n"+file.getAbsolutePath(),
                            "Export Successful", JOptionPane.INFORMATION_MESSAGE);
                }catch(Exception ex){
                    LOG.severe("Export failed: "+ex.getMessage());
                    JOptionPane.showMessageDialog(frame, "Failed to export data:\n"+ex.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE);
                }
            } else LOG.info("Export canceled.");
        });

        LOG.info("UI ready.");
    }
}