package org.example;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main Class: The entry point of the application.
 * Responsibility: Sets up the main GUI window, initializes the simulation manager, and handles the application lifecycle.
 *
 * 主类：应用程序的入口点。
 * 职责：设置主 GUI 窗口，初始化仿真管理器，并处理应用程序的生命周期。
 */
public class Main {
    // Configuration path for the SUMO simulation file (.sumocfg).
    // SUMO 仿真配置文件 (.sumocfg) 的路径。
    private static final String SUMO_CONFIG_PATH = "C:\\Users\\17608\\Desktop\\SUMO-Project-main\\JavaSumo\\map3.sumocfg";

    /**
     * Main method: The standard entry point for Java applications.
     * 主方法：Java 应用程序的标准入口点。
     */
    public static void main(String[] args) {
        // 1. Create the main application window (Frame).
        // JFrame corresponds to the main window on your desktop.
        //
        // 1. 创建主应用程序窗口 (Frame)。
        // JFrame 对应于桌面上的主窗口。
        JFrame frame = new JFrame("Vehicle Module Test Bench");

        // Set the operation when the user clicks the "X" button: Exit the application.
        // 设置用户点击 "X" 按钮时的操作：退出应用程序。
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Set the window size to 1000 pixels wide and 700 pixels high.
        // 将窗口大小设置为宽 1000 像素，高 700 像素。
        frame.setSize(1000, 700);

        // Use BorderLayout manager.
        // BorderLayout divides the window into 5 regions: NORTH, SOUTH, EAST, WEST, CENTER.
        //
        // 使用 BorderLayout 布局管理器。
        // BorderLayout 将窗口分为 5 个区域：北、南、东、西、中。
        frame.setLayout(new BorderLayout());

        // 2. Prepare dependency components (Simulation Environment).
        // Create the MapPanel which visualizes the traffic.
        //
        // 2. 准备依赖组件 (模拟环境)。
        // 创建用于可视化交通状况的 MapPanel。
        MapPanel mapPanel = new MapPanel();

        // Create dummy labels to satisfy the SimulationManager constructor.
        // These labels display real-time metrics (e.g., vehicle count, congestion).
        //
        // 创建虚拟标签以满足 SimulationManager 构造函数的要求。
        // 这些标签用于显示实时指标（例如，车辆数量，拥堵情况）。
        JLabel dummyCountLabel = new JLabel("Active: 0");
        JLabel dummyCongestionLabel = new JLabel("Congestion: None");

        // 3. Initialize the Controller (SimulationManager).
        // Dependency Injection: We pass the View (MapPanel, Labels) into the Controller.
        // This allows the Controller to update the View when data changes.
        //
        // 3. 初始化控制器 (SimulationManager)。
        // 依赖注入：我们将视图 (MapPanel, Labels) 传递给控制器。
        // 这允许控制器在数据发生变化时更新视图。
        SimulationManager simManager = new SimulationManager(mapPanel, dummyCountLabel, dummyCongestionLabel);

        // ============================================================
        // [CORE MODULE LOAD] Load the Vehicle Control Module
        // This 'VehicleControlPanel' is the specific component developed for this milestone.
        // It provides the user interface for injecting and controlling vehicles.
        //
        // [核心模块加载] 加载车辆控制模块
        // 这个 'VehicleControlPanel' 是为本次里程碑开发的特定组件。
        // 它提供了用于注入和控制车辆的用户界面。
        // ============================================================
        VehicleControlPanel myControlPanel = new VehicleControlPanel(simManager);

        // Place the control panel on the LEFT side of the window.
        // 将控制面板放置在窗口的左侧。
        frame.add(myControlPanel, BorderLayout.WEST);

        // Place the map visualization in the CENTER (it will take up remaining space).
        // 将地图可视化放置在中间（它将占据剩余空间）。
        frame.add(mapPanel, BorderLayout.CENTER);

        // (Optional) Create a bottom status panel for debug labels.
        // (可选) 创建底部状态面板以显示调试标签
        JPanel statusPanel = new JPanel();
        statusPanel.add(dummyCountLabel);
        statusPanel.add(dummyCongestionLabel);
        frame.add(statusPanel, BorderLayout.SOUTH);

        // 4. Finalize and Show Window.
        // Center the window on the screen.
        //
        // 4. 完成并显示窗口。
        // 将窗口居中显示在屏幕上。
        frame.setLocationRelativeTo(null);

        // Make the window visible.
        // 使窗口可见。
        frame.setVisible(true);

        // Start the SUMO simulation thread.
        // 启动 SUMO 仿真线程。
        startSumoThread(simManager, frame);
    }

    /**
     * [CORE STARTUP LOGIC] Start SUMO and Simulation Loop.
     * This method handles the complex threading required to keep the GUI responsive while running a heavy simulation.
     *
     * [核心启动逻辑] 启动 SUMO 和 仿真循环。
     * 此方法处理保持 GUI 响应同时运行繁重仿真所需的复杂线程逻辑。
     */
    private static void startSumoThread(SimulationManager simManager, JFrame frame) {
        // [IMPORTANT] Must use a new Thread.
        // 1. Simulation.start() is blocking: It waits until SUMO connects via TCP/IP.
        // 2. SimulationManager.run() is an infinite loop (while(true)).
        // If these ran on the main thread (which becomes the GUI Event Dispatch Thread), the UI would freeze immediately.
        //
        // [重要] 必须开启新线程。
        // 1. Simulation.start() 是阻塞操作：它会等待 SUMO 通过 TCP/IP 连接。
        // 2. SimulationManager.run() 是一个死循环 (while(true))。
        // 如果这两个直接在 main 线程运行（它会成为 GUI 事件分发线程），UI 界面会立即卡死。
        new Thread(() -> {
            try {
                // Load native C++ libraries required by LibTraCI.
                // 加载 LibTraCI 所需的本地 C++ 库。
                Simulation.preloadLibraries();

                // Prepare arguments for SUMO startup.
                // 准备 SUMO 启动参数。
                StringVector sv = new StringVector();
                sv.add("sumo-gui");         // Launch the GUI version of SUMO / 启动带 GUI 的 SUMO
                sv.add("-c");               // Config flag / 配置标志
                sv.add(SUMO_CONFIG_PATH);   // Path to .sumocfg / .sumocfg 文件路径

                // Connect to SUMO. This might take a few seconds.
                // 连接到 SUMO。这可能需要几秒钟。
                Simulation.start(sv);

                // Once connected, start the SimulationManager in its own thread.
                // 连接成功后，在自己的线程中启动 SimulationManager。
                new Thread(simManager).start();

            } catch (Exception e) {
                // If startup fails, show an error dialog.
                // Use SwingUtilities.invokeLater because we are on a background thread and touching UI components.
                //
                // 如果启动失败，显示错误对话框。
                // 使用 SwingUtilities.invokeLater，因为我们在后台线程，正在操作 UI 组件。
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(frame, "SUMO Startup Error:\n" + e.getMessage())
                );
            }
        }).start();
    }
}
