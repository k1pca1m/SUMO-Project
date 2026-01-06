package org.example;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.Random;

/**
 * VehicleControlPanel: The side panel for User Interaction.
 * Responsibility: Collects user inputs (Injection, Filtering, Control) and sends them to the SimulationManager.
 *
 * VehicleControlPanel: 用于用户交互的侧面板。
 * 职责：收集用户输入（注入、过滤、控制）并将它们发送给 SimulationManager。
 */
public class VehicleControlPanel extends JPanel{
    private final SimulationManager simManager; // Logic Controller / 逻辑控制器
    private final Random rng = new Random();

    // Style Constants / 样式常量
    private static final Color BG_DARK = new Color(45, 52, 54);
    private static final Color TEXT_COLOR = new Color(223, 230, 233);
    private static final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 12);

    public VehicleControlPanel(SimulationManager manager) {
        this.simManager = manager;
        initUI();
    }

    private void initUI() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(BG_DARK);

        // Padding / 内边距
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Fixed Width / 固定宽度
        setPreferredSize(new Dimension(260, 0));

        // 标题
        JLabel title = new JLabel("Vehicle Ops Module");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(title);
        add(Box.createVerticalStrut(15));

        // Add Sub-Panels / 添加子面板

        // --- 模块 1: 注入 (Injection) ---
        add(createInjectionPanel());
        add(Box.createVerticalStrut(15));

        // --- 模块 2: 筛选 (Filtering) ---
        add(createFilterPanel());
        add(Box.createVerticalStrut(15));

        // --- 模块 3: 控制 (Control) ---
        add(createControlPanel());

        // // Push to top / 推至顶部
        add(Box.createVerticalGlue());
    }

    // ==========================================
    // 1. Injection Panel (Single & Batch) / 1. 注入面板 (单车 & 批量)
    // ==========================================
    private JPanel createInjectionPanel() {
        JPanel p = createStyledPanel("Injection Center");

        // UI Components
        JTextField txtId = new JTextField("veh_" + System.currentTimeMillis() % 1000);
        String[] edges = {"E0", "-1157218234#2", "4824697"}; // 常用 Edge 预设
        JComboBox<String> comboEdge = new JComboBox<>(edges);
        JComboBox<String> comboType = new JComboBox<>(new String[]{"car", "truck"});
        JButton btnInject = createStyledButton("Inject Single", new Color(9, 132, 227));

        JSpinner spinCount = new JSpinner(new SpinnerNumberModel(5, 1, 50, 1));
        JButton btnBatch = createStyledButton("Batch Spawn", new Color(0, 184, 148));

        // Layout / 布局

        //single
        p.add(createLabel("ID / Edge / Type:"));
        p.add(txtId);
        p.add(Box.createVerticalStrut(2));
        p.add(comboEdge);
        p.add(Box.createVerticalStrut(2));
        p.add(comboType);
        p.add(Box.createVerticalStrut(5));
        p.add(btnInject);

        // 分割线
        p.add(Box.createVerticalStrut(10));
        JSeparator sep = new JSeparator();
        sep.setBackground(Color.GRAY);
        p.add(sep);
        p.add(Box.createVerticalStrut(10));

        // batch
        JPanel batchRow = new JPanel(new BorderLayout());
        batchRow.setBackground(BG_DARK);
        batchRow.add(createLabel("Count: "), BorderLayout.WEST);
        batchRow.add(spinCount, BorderLayout.CENTER);
        p.add(batchRow);
        p.add(Box.createVerticalStrut(5));
        p.add(btnBatch);

        // Logic: Single Inject
        // 事件 - 单车注入
        btnInject.addActionListener(e -> {
            Color c = new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
            simManager.injectVehicleOnEdge(txtId.getText(), (String)comboEdge.getSelectedItem(), (String)comboType.getSelectedItem(), c);
            txtId.setText("veh_" + System.currentTimeMillis() % 1000);
        });

        // Logic: Batch Inject
        // 逻辑：批量注入
        btnBatch.addActionListener(e -> {
            int count = (Integer) spinCount.getValue();

            // Threading: Run batch loop in a new thread to avoid freezing the UI.
            // 线程：在新线程中运行批量循环以避免冻结 UI。
            new Thread(() -> {
                for(int i=0; i<count; i++) {
                    String uid = "batch_" + System.currentTimeMillis() + "_" + i;
                    String edge = edges[rng.nextInt(edges.length)];
                    String type = rng.nextBoolean() ? "car" : "truck";
                    Color c = new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                    simManager.injectVehicleOnEdge(uid, edge, type, c);
                    try { Thread.sleep(100); } catch(Exception ex){}
                }
            }).start();
        });

        return p;
    }

    // ==========================================
    // 2. [新增] 筛选面板 (Filtering)
    // ==========================================
    private JPanel createFilterPanel() {
        JPanel p = createStyledPanel("View Filters");

        // 2.1 红色车过滤/ Red Cars Only
        JCheckBox chkRedOnly = new JCheckBox("Show Red Cars Only");
        chkRedOnly.setBackground(BG_DARK);
        chkRedOnly.setForeground(TEXT_COLOR);
        chkRedOnly.setFocusPainted(false);

        // 2.2 速度过滤滑动条 /Speed
        JLabel lblSpeed = createLabel("Min Speed: 0 m/s");
        JSlider sliderSpeed = new JSlider(0, 20, 0);
        sliderSpeed.setBackground(BG_DARK);
        sliderSpeed.setPreferredSize(new Dimension(180, 20));

        // Listeners: Update Manager State directly.
        // 监听器：直接更新管理器状态。
        chkRedOnly.addActionListener(e -> {
            simManager.setFilterShowRedOnly(chkRedOnly.isSelected());
        });

        sliderSpeed.addChangeListener(e -> {
            int val = sliderSpeed.getValue();
            lblSpeed.setText("Min Speed: " + val + " m/s");
            simManager.setFilterMinSpeed((double) val);
        });

        p.add(chkRedOnly);
        p.add(Box.createVerticalStrut(5));
        p.add(lblSpeed);
        p.add(sliderSpeed);

        return p;
    }

    // ==========================================
    // 3. [新增] 实时控制面板 (Live Control)
    // ==========================================
    private JPanel createControlPanel() {
        JPanel p = createStyledPanel("Live Control");

        JTextField txtTargetId = new JTextField();
        txtTargetId.setToolTipText("Enter Vehicle ID");

        JButton btnColor = createStyledButton("Random Color", new Color(108, 92, 231));
        JButton btnStop = createStyledButton("STOP", new Color(214, 48, 49));
        JButton btnResume = createStyledButton("Resume", new Color(46, 204, 113));

        // 布局
        p.add(createLabel("Target Vehicle ID:"));
        p.add(txtTargetId);
        p.add(Box.createVerticalStrut(5));
        p.add(btnColor);
        p.add(Box.createVerticalStrut(5));

        JPanel speedPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        speedPanel.setBackground(BG_DARK);
        speedPanel.add(btnStop);
        speedPanel.add(btnResume);
        p.add(speedPanel);

        // Logic: Commands
        // 逻辑：命令
        // 事件监听
        btnColor.addActionListener(e -> {
            String id = txtTargetId.getText().trim();
            if(!id.isEmpty()) {
                Color randomColor = new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
                // Run in thread (good practice for TraCI calls).
                // 在线程中运行（TraCI 调用的良好实践）。
                new Thread(() -> simManager.changeVehicleColor(id, randomColor)).start();
            }
        });

        btnStop.addActionListener(e -> {
            String id = txtTargetId.getText().trim();
            if(!id.isEmpty()) new Thread(() -> simManager.setVehicleSpeed(id, 0.0)).start();
        });

        btnResume.addActionListener(e -> {
            String id = txtTargetId.getText().trim();
            if(!id.isEmpty()) new Thread(() -> simManager.setVehicleSpeed(id, -1.0)).start(); // -1 代表恢复默认限速
        });

        return p;
    }

    // --- Helper Methods (Styling) / 辅助方法 (样式) ---

    private JPanel createStyledPanel(String title) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(BG_DARK);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(Color.GRAY), title);
        border.setTitleColor(Color.WHITE);
        border.setTitleFont(FONT_BOLD);
        p.setBorder(border);
        return p;
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_COLOR);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JButton createStyledButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setFont(FONT_BOLD);
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return btn;
    }
}
