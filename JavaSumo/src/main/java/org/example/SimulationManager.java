package org.example;
import org.eclipse.sumo.libtraci.Simulation;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TraCIPosition;
import javax.swing.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

public class SimulationManager implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(SimulationManager.class.getName());
    // 1. 通信队列：接收 GUI 的注入请求
    private final ConcurrentLinkedQueue<InjectionRequest> injectionQueue = new ConcurrentLinkedQueue<>();
    private final StatisticsCollector statsCollector = new StatisticsCollector();
    // 2. 底层接口引用
    private final VehicleInjection traCIWrapper = new VehicleInjection();

    // 3. GUI 组件引用 (用于更新界面)
    private final Main.MapPanel mapPanel;
    private final JLabel activeVehiclesLabel;
    private final JLabel congestionLabel; // <--- 新增这个
    // 运行标志
    private volatile boolean running = true;

    // 构造函数：接收 GUI 组件
    public SimulationManager(Main.MapPanel mapPanel, JLabel activeVehiclesLabel,JLabel congestionLabel) {
        this.mapPanel = mapPanel;
        this.activeVehiclesLabel = activeVehiclesLabel;
        this.congestionLabel = congestionLabel; // <--- 保存引用
    }

    // --- 供 GUI 调用的公共方法 ---

    /**
     * 提交车辆注入请求 (非阻塞)
     */
    /**
     * 方式A: 基于 routeID 注入 (原有一键注入)
     */
    public void injectVehicle(String id, String route) {
        // useRoute = true
        injectionQueue.add(new InjectionRequest(id, route,true));
    }
    /**
     * 方式B: 基于 edgeID 注入 (新的一键批量)
     */
    public void injectVehicleOnEdge(String id, String edgeId) {
        // useRoute = false, 这里的 target 代表 edgeId
        injectionQueue.add(new InjectionRequest(id, edgeId, false));
    }
    public void stop() {
        this.running = false;
    }

    // --- 工作线程逻辑 ---

    @Override
    public void run() {
        System.out.println(">>> [线程状态] Simulation Thread 启动! "); // [调试] 确认线程进来了
        try {
            // 仿真循环
            while (running /*&& Simulation.getMinExpectedNumber() > 0*/) {

                // A. 处理注入队列 (在步进前)
                processInjectionQueue();

                // B. 仿真步进
                Simulation.step();

                // C. 获取数据 (原 Main.java 中的逻辑)
                List<String> ids = Vehicle.getIDList();
                // >>> 关键修复：定义并填充 pos 变量 <<<
                Map<String, Point2D.Double> pos = new HashMap<>();
                for (String id : ids) {
                    try {
                        TraCIPosition p = Vehicle.getPosition(id);
                        // 将 SUMO 坐标 (x, y) 存入 map
                        pos.put(id, new Point2D.Double(p.getX(), p.getY()));
                    } catch (Exception e) {
                        // 忽略瞬间消失的车辆
                    }
                }
                // >>> D. 新增：更新统计数据 <<<
                statsCollector.update(ids);

                /// >>> E. 计算拥堵路段 (在后台线程计算，不卡界面) <<<
                Map<String, Integer> density = statsCollector.getEdgeDensities();
                String maxEdge = "None";
                int maxCount = 0;
                // 找出车辆最多的路段

                for (Map.Entry<String, Integer> entry : density.entrySet()) {
                    if (entry.getValue() > maxCount) {
                        maxCount = entry.getValue();
                        maxEdge = entry.getKey();
                    }
                }
                // 准备好要显示的字符串 (final 变量以便传入 lambda)
                final String congestionText = "Hottest Edge: " + maxEdge + " (" + maxCount + ")";
                final int activeCount = ids.size();
                // F. 更新 GUI (必须在 EDT 中执行)
                int count = ids.size();
                SwingUtilities.invokeLater(() -> {
                    mapPanel.updateVehicles(pos);
                    mapPanel.repaint();
                    activeVehiclesLabel.setText("Active Vehicles: " + activeCount);
                    congestionLabel.setText(congestionText); // <--- 这里更新！
                });

                // 控制帧率
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

    // 内部私有方法：处理队列
    private void processInjectionQueue() {
        InjectionRequest req;
        while ((req = injectionQueue.poll()) != null) {
            System.out.println(">>> 3. Manager 收到了请求，正在处理 ID: " + req.id); // [调试]
            try {
                if (req.isRoute) {
                    // 使用预设路由
                    traCIWrapper.addVehicle(req.id, req.target, "car");
                } else {
                    // [新功能] 使用指定 Edge 动态生成
                    traCIWrapper.addVehicleOnEdge(req.id, req.target, "car");
                }
                /*traCIWrapper.addVehicle(req.id, req.route, "car");
                System.out.println(">>> 4. 注入指令执行成功！"); // [调试]
                LOGGER.info("Injected vehicle: " + req.id);*/
            } catch (Exception e) {
                System.err.println(">>> [严重错误] 注入失败！原因: " + e.getMessage()); // [调试]
                e.printStackTrace(); // 打印完整堆栈
                /*LOGGER.log(Level.SEVERE, "Failed to inject vehicle: " + req.id, e);
                System.err.println("[Error] Failed to inject vehicle '" + req.id + "'. Reason: " + e.getMessage());
                // 3. (高级做法) 通知 GUI 显示错误弹窗
                // 由于我们在 Worker Thread，必须用 invokeLater
                String errorMsg = "Injection failed for " + req.id + ": " + e.getMessage();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JOptionPane.showMessageDialog(null, errorMsg, "Injection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                });*/
            }
        }
    }

    // 数据传输对象 DTO
    private static class InjectionRequest {
        String id;
        String target; // 可以是 routeID 也可以是 edgeID
        boolean isRoute; // true=Route, false=Edge
        //String route;
        public InjectionRequest(String id, String target,boolean isRoute) {
            this.id = id;
            this.target = target;
            this.isRoute = isRoute;
            //this.route = route;
        }
    }

    // 辅助方法：打印到控制台查看效果
    private void printStatsDebug() {
        System.out.println("--- Real-time Stats ---");

        // 1. 打印拥堵路段 (密度 > 0 的路)
        Map<String, Integer> density = statsCollector.getEdgeDensities();
        density.forEach((edge, count) -> {
            System.out.println("Edge " + edge + ": " + count + " vehicles");
        });

        // 2. 打印某辆车的平均速度 (例如第一辆)
        double systemAvg = statsCollector.getSystemAverageSpeed();
        System.out.printf("System Avg Speed: %.2f m/s%n", systemAvg);
        System.out.println("-----------------------");
    }

    // 暴露给外部 (Main.java) 获取数据
    public StatisticsCollector getStats() {
        return statsCollector;
    }
}

