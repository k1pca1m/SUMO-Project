package org.example;
import org.eclipse.sumo.libtraci.Simulation;

import java.awt.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.sumo.libtraci.Vehicle;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.eclipse.sumo.libtraci.TraCIColor;

/**
 * SimulationManager implements Runnable to run on a separate "Worker Thread".
 * Responsibility: Manages the simulation loop, synchronizes data between TraCI and GUI.
 *
 * SimulationManager 实现了 Runnable 接口，以便在单独的 "工作线程" 上运行。
 * 职责：管理仿真循环，同步 TraCI 和 GUI 之间的数据。
 */
public class SimulationManager implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(SimulationManager.class.getName());

    // 1. Communication Queue: Asynchronous command buffer.
    // Why: The GUI thread calls 'injectVehicle', but the Simulation thread executes it.
    // ConcurrentLinkedQueue is thread-safe, allowing safe data passing between threads (Producer-Consumer pattern).
    //
    // 1. 通信队列：异步命令缓冲区。
    // 原因：GUI 线程调用 'injectVehicle'，但仿真线程负责执行它。
    // ConcurrentLinkedQueue 是线程安全的，允许在线程之间安全地传递数据（生产者-消费者模式）。
    private final ConcurrentLinkedQueue<InjectionRequest> injectionQueue = new ConcurrentLinkedQueue<>();

    // Data Collector: Aggregates statistics.
    // 数据收集器：聚合统计信息。
    private final StatisticsCollector statsCollector = new StatisticsCollector();

    // 2. TraCI Wrapper Reference
    // Encapsulates low-level SUMO API calls.
    //
    // 2. 底层接口引用 (TraCI Wrapper)
    // 封装低级 SUMO API 调用。
    private final VehicleInjection traCIWrapper = new VehicleInjection();

    // 3. GUI Component References (For updates)
    // 3. GUI 组件引用 (用于更新界面)
    private final MapPanel mapPanel;
    private final JLabel activeVehiclesLabel;
    private final JLabel congestionLabel;

    // Running Flag: Controls the simulation loop.
    // 运行标志：控制仿真循环。
    private volatile boolean running = true;

    // Filter Settings (Thread-Safe via volatile)
    // 过滤设置 (通过 volatile 保证线程安全)
    private volatile boolean filterShowRedOnly = false; // Filter: Show only red cars / 过滤：只显示红车
    private volatile double filterMinSpeed = 0.0;       // Filter: Minimum speed / 过滤：最小速度

    // --- Setters for Filters (Called by GUI Thread) ---
    // --- 过滤器的 Setter 方法 (由 GUI 线程调用) ---
    public void setFilterShowRedOnly(boolean enable) {
        this.filterShowRedOnly = enable;
    }

    // 设置过滤：最小速度
    public void setFilterMinSpeed(double speed) {
        this.filterMinSpeed = speed;
    }

    /**
     * Control Function: Emergency Stop / Resume.
     * Sets the maximum speed of a vehicle in SUMO.
     *
     * 控制功能：紧急停车 / 恢复。
     * 在 SUMO 中设置车辆的最大速度。
     */
    public void setVehicleSpeed(String id, double speed) {
        try {
            traCIWrapper.setVehicleMaxSpeed(id, speed); // 需在 VehicleInjection 中实现此方法
        } catch (Exception e) {
            System.err.println("Failed to set speed: " + e.getMessage());
        }
    }

    /**
     * Constructor: Dependency Injection.
     * We pass the UI components here so the manager knows what to update.
     *
     * 构造函数：依赖注入。
     * 我们将 UI 组件传递进来，这样管理器就知道需要更新哪些控件。
     */
    public SimulationManager(MapPanel mapPanel, JLabel activeVehiclesLabel,JLabel congestionLabel) {
        this.mapPanel = mapPanel;
        this.activeVehiclesLabel = activeVehiclesLabel;
        this.congestionLabel = congestionLabel; // <--- 保存引用
    }

    // --- Public Methods for GUI (Producers) / 供 GUI 调用的公共方法 (生产者) ---

    /**
     * Method A: Inject based on Route ID.
     * Note: This method returns immediately (Non-blocking). It only adds a request to the queue.
     * * 方法 A: 基于 Route ID 注入。
     * 注意：此方法立即返回（非阻塞）。它只是将请求添加到队列中。
     */
    public void injectVehicle(String id, String route) {
        // 这里的颜色仅用于占位(因为 addVehicle 暂时没用它)，或者你可以去修改 addVehicle 也支持颜色
        TraCIColor defaultColor = new TraCIColor(255, 0, 0, 255);
        // useRoute = true
        injectionQueue.add(new InjectionRequest(id, route,true, "car", defaultColor));
    }

    /**
     * Method B: Inject based on Edge ID (Dynamic Routing).
     * * 方法 B: 基于 Edge ID 注入（动态路由）。
     */
    public void injectVehicleOnEdge(String id, String edgeId,String type, Color awtColor) {
        // Convert Java AWT Color to SUMO TraCI Color.
        // 将 Java AWT 颜色 转换为 SUMO TraCI 颜色。
        TraCIColor sumoColor = new TraCIColor(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), 255);

        // Package type and color into the request.
        // 将车型和颜色打包进请求。
        injectionQueue.add(new InjectionRequest(id, edgeId, false, type, sumoColor));

    }

    /**
     * Stops the simulation loop safely.
     * 安全停止仿真循环。
     */
    public void stop() {
        this.running = false;
    }

    // --- Worker Thread Logic (Consumer) / 工作线程逻辑 (消费者) ---

    @Override
    public void run() {
        System.out.println(">>> [线程状态] Simulation Thread 启动! "); // [调试] 确认线程进来了
        long stepCounter = 0; // Step counter for console logging / 用于控制台日志的步数计数器

        try {
            // Main Simulation Loop / 主仿真循环
            while (running) {

                // A. Process Injection Queue (Before stepping)
                // Why: We process user inputs before calculating the physics of the next frame.
                //
                // A. 处理注入队列 (在步进前)
                // 原因：我们在计算下一帧的物理逻辑之前，先处理用户的输入。
                processInjectionQueue();

                // B. Advance Simulation
                // Ask SUMO to calculate the next time step (physics, movement, traffic lights).
                //
                // B. 推进仿真
                // 请求 SUMO 计算下一个时间步（物理、移动、交通灯）。
                Simulation.step();

                // C. Fetch Data
                // Get all active vehicle IDs from SUMO.
                //
                // C. 获取数据
                // 从 SUMO 获取所有活跃的车辆 ID。
                List<String> ids = Vehicle.getIDList();

                // >>> D. Update Statistics <<<
                // Pass raw data to the collector to calculate density/speed history.
                //
                // >>> D. 更新统计数据 <<<
                // 将原始数据传递给收集器，用于计算密度或历史速度。
                statsCollector.update(ids);

                /// >>> E. Calculate Congestion (Heavy Calculation) <<<
                // Why: We do this heavy math here in the background thread, NOT in the GUI thread.
                //
                /// >>> E. 计算拥堵情况 (繁重计算) <<<
                // 原因：我们在后台线程处理这些繁重的数学计算，而不是在 GUI 线程中，以免卡顿。
                Map<String, Integer> density = statsCollector.getEdgeDensities();
                String maxEdge = "None";
                int maxCount = 0;

                // Find the edge with the highest number of vehicles.
                // 找出车辆数最多的路段。
                for (Map.Entry<String, Integer> entry : density.entrySet()) {
                    if (entry.getValue() > maxCount) {
                        maxCount = entry.getValue();
                        maxEdge = entry.getKey();
                    }
                }

                // Prepare "final" variables for lambda expression (variables inside lambda must be final or effectively final).
                // 为 Lambda 表达式准备 "final" 变量（Lambda 内部的变量必须是 final 或 实际上的 final）。
                final String congestionText = "Hottest Edge: " + maxEdge + " (" + maxCount + ")";
                final int activeCount = ids.size();

                // --- Filtering Logic (Clean Code with Java Streams) ---
                // Get a snapshot of all vehicle data.
                //
                // --- 筛选逻辑 (使用 Java Streams 实现整洁代码) ---
                // 获取所有车辆数据的快照。
                List<VehicleData> allData = new ArrayList<>(statsCollector.getAllVehicleData());

                // 将原来的硬编码逻辑替换为：
                List<VehicleData> filteredList = allData.stream()
                        .filter(v -> ids.contains(v.getVehicleId())) // 1. Must be currently on map / 必须在地图上
                        .filter(v -> !filterShowRedOnly || v.getColor().equals(Color.RED)) // 2. Color Filter / 颜色过滤
                        .filter(v -> v.getCurrentSpeed() >= filterMinSpeed) // 3. Speed Filter / 速度过滤
                        .collect(Collectors.toList());

                // Log stats to console every 20 frames (approx. 1 second).
                // 每 20 帧（约 1 秒）向控制台记录一次统计信息。
                stepCounter++;
                if (stepCounter % 20 == 0) {
                    printConsoleStats(ids.size());
                }

                // F. Update GUI (Must be on EDT)
                // Why: Swing is NOT thread-safe. You cannot modify labels/panels directly from this thread.
                // 'SwingUtilities.invokeLater' queues the update task to the Event Dispatch Thread.
                //
                // F. 更新 GUI (必须在 EDT 线程)
                // 原因：Swing 不是线程安全的。你不能直接从当前线程修改 Label 或 Panel。
                // 'SwingUtilities.invokeLater' 将更新任务排队发送到事件分发线程 (EDT)。
                final int currentActiveCount = ids.size(); // 临时变量
                SwingUtilities.invokeLater(() -> {
                    // [Debug] Verify update is happening.
                    // [调试] 验证更新正在发生。
                    System.out.println(">>> GUI 正在尝试更新: Count=" + currentActiveCount);

                    // Update the MapPanel with the FILTERED list of vehicles.
                    // 使用过滤后的车辆列表更新 MapPanel。
                    mapPanel.updateData(filteredList);

                    // Update text labels.
                    // 更新文本标签。
                    activeVehiclesLabel.setText("Active Vehicles: " + activeCount);
                    congestionLabel.setText(congestionText);
                });

                /// Frame Rate Control
                // Why: Sleep 50ms to limit speed to ~20 FPS.
                // Without this, the loop runs as fast as possible, consuming 100% CPU.
                //
                // 帧率控制
                // 原因：休眠 50ms 以将速度限制在约 20 FPS。
                // 如果没有这行，循环会全速运行，占用 100% 的 CPU。
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(">>> [线程崩溃] 模拟循环发生异常退出: " + e.getMessage());
        } finally {
            System.out.println(">>> [线程状态] Simulation Thread 结束运行。");
            Simulation.close();
            System.exit(0);
        }
    }

    /**
     * Helper: Print detailed stats to console.
     * 辅助方法：将详细统计信息打印到控制台。
     */
    private void printConsoleStats(int activeCount) {
        // Get system-wide average speed.
        // 获取全系统平均速度
        double avgSpeed = statsCollector.getSystemAverageSpeed();

        // Get hottest edge info.
        // 获取拥堵路段数据
        Map<String, Integer> density = statsCollector.getEdgeDensities();
        String hottestEdge = "None";
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : density.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                hottestEdge = entry.getKey();
            }
        }

        // 格式化打印
        System.out.println("--------------------------------------------------");
        System.out.println(">> [Step Status] Active Vehicles: " + activeCount);
        System.out.printf(">> [System Avg Speed] %.2f m/s%n", avgSpeed);
        System.out.println(">> [Hottest Edge] " + hottestEdge + " (Load: " + maxCount + ")");
        System.out.println("--------------------------------------------------");
    }

    /**
     * Private Helper: Process the queue
     * Why: Decouples the request (user click) from execution (TraCI call).
     * * 私有助手方法：处理队列
     * 原因：将请求（用户点击）与执行（TraCI 调用）解耦。
     */
    private void processInjectionQueue() {
        InjectionRequest req;
        // Poll removes the head of the queue, returns null if empty.
        // Poll 移除并返回队列头部元素，如果为空则返回 null
        while ((req = injectionQueue.poll()) != null) {
            System.out.println(">>> 3. Manager 收到了请求，正在处理 ID: " + req.id); // [调试]
            try {
                if (req.isRoute) {

                    // Use pre-defined route.
                    // 使用预设路由
                    traCIWrapper.addVehicle(req.id, req.target, "car");
                } else {

                    // Dynamic spawn on edge.
                    // [新功能] 使用指定 Edge 动态生成
                    traCIWrapper.addVehicleOnEdge(req.id, req.target, req.type,req.color);
                }

            } catch (Exception e) {
                // Robustness: If one vehicle fails, catch it so the simulation doesn't crash.
                // 健壮性：如果一辆车生成失败，捕获异常，确保仿真本身不会崩溃。
                System.err.println(">>> [严重错误] 注入失败！原因: " + e.getMessage()); // [调试]
                e.printStackTrace(); // 打印完整堆栈

            }
        }
    }

    // DTO (Data Transfer Object) class
    // Simple container to hold request data in the queue.
    //
    // DTO (数据传输对象) 类
    // 简单的容器，用于在队列中保存请求数据。
    private static class InjectionRequest {
        String id;
        String target;   // RouteID or EdgeID / 路由ID 或 道路ID
        boolean isRoute; // true=Route, false=Edge
        TraCIColor color;
        String type;
        public InjectionRequest(String id, String target,boolean isRoute,String type,TraCIColor color) {
            this.id = id;
            this.target = target;
            this.isRoute = isRoute;
            this.type = type;   // [新增]
            this.color = color; // [新增]
        }
    }

    // Expose stats collector for external access.
    // 暴露给外部 (Main.java) 获取数据
    public StatisticsCollector getStats() {
        return statsCollector;
    }

    /**
     * Change vehicle color command.
     * 改变车辆颜色命令。
     */
    public void changeVehicleColor(String id, Color awtColor) {
        TraCIColor sumoColor = new TraCIColor(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue(), 255);
        traCIWrapper.setVehicleColor(id, sumoColor);
    }

    /**
     * Stop vehicle command (Set max speed to 0).
     * 停止车辆命令 (将最大速度设为 0)。
     */
    public void stopVehicle(String id) {
        traCIWrapper.setVehicleMaxSpeed(id, 0.0); // 速度设为0即停车
    }
}

