package org.example;
import org.eclipse.sumo.libtraci.Simulation;
import org.example.VehicleInjection;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TraCIPosition;
import javax.swing.*;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.logging.Level;
public class SimulationManager implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(SimulationManager.class.getName());
    // 1. 通信队列：接收 GUI 的注入请求
    private final ConcurrentLinkedQueue<InjectionRequest> injectionQueue = new ConcurrentLinkedQueue<>();

    // 2. 底层接口引用
    private final VehicleInjection traCIWrapper = new VehicleInjection();

    // 3. GUI 组件引用 (用于更新界面)
    private final Main.MapPanel mapPanel;
    private final JLabel activeVehiclesLabel;

    // 运行标志
    private volatile boolean running = true;

    // 构造函数：接收 GUI 组件
    public SimulationManager(Main.MapPanel mapPanel, JLabel activeVehiclesLabel) {
        this.mapPanel = mapPanel;
        this.activeVehiclesLabel = activeVehiclesLabel;
    }

    // --- 供 GUI 调用的公共方法 ---

    /**
     * 提交车辆注入请求 (非阻塞)
     */
    public void injectVehicle(String id, String route) {
        injectionQueue.add(new InjectionRequest(id, route));
    }

    public void stop() {
        this.running = false;
    }

    // --- 工作线程逻辑 ---

    @Override
    public void run() {
        try {
            // 仿真循环
            while (running && Simulation.getMinExpectedNumber() > 0) {

                // A. 处理注入队列 (在步进前)
                processInjectionQueue();

                // B. 仿真步进
                Simulation.step();

                // C. 获取数据 (原 Main.java 中的逻辑)
                List<String> ids = Vehicle.getIDList();
                Map<String, Point2D.Double> pos = new HashMap<>();

                for (String id : ids) {
                    TraCIPosition p = Vehicle.getPosition(id);
                    pos.put(id, new Point2D.Double(p.getX(), p.getY()));
                }

                // D. 更新 GUI (必须在 EDT 中执行)
                int count = ids.size();
                SwingUtilities.invokeLater(() -> {
                    mapPanel.updateVehicles(pos);
                    mapPanel.repaint();
                    activeVehiclesLabel.setText("Active Vehicles: " + count);
                });

                // 控制帧率
                Thread.sleep(50);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            Simulation.close();
            System.exit(0);
        }
    }

    // 内部私有方法：处理队列
    private void processInjectionQueue() {
        InjectionRequest req;
        while ((req = injectionQueue.poll()) != null) {
            try {
                traCIWrapper.addVehicle(req.id, req.route, "car");
                LOGGER.info("Injected vehicle: " + req.id);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Failed to inject vehicle: " + req.id, e);
                System.err.println("[Error] Failed to inject vehicle '" + req.id + "'. Reason: " + e.getMessage());
                // 3. (高级做法) 通知 GUI 显示错误弹窗
                // 由于我们在 Worker Thread，必须用 invokeLater
                String errorMsg = "Injection failed for " + req.id + ": " + e.getMessage();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    javax.swing.JOptionPane.showMessageDialog(null, errorMsg, "Injection Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                });
            }
        }
    }

    // 数据传输对象 DTO
    private static class InjectionRequest {
        String id;
        String route;
        public InjectionRequest(String id, String route) {
            this.id = id;
            this.route = route;
        }
    }
}
