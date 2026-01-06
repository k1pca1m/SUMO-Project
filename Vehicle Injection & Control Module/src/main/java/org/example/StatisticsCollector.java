package org.example;
import org.eclipse.sumo.libtraci.Vehicle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.awt.geom.Point2D;

/**
 * StatisticsCollector: The Central Data Aggregator
 * Responsible for storing vehicle history and calculating real-time metrics.
 * * StatisticsCollector: 中央数据聚合器
 * 负责存储车辆历史记录并计算实时指标。
 */
public class StatisticsCollector {

    // 1. Long-term History Storage / 长期历史存储
    // Why ConcurrentHashMap?
    // The Simulation Thread writes to this continuously. The GUI Thread (potentially) reads from it.
    // Standard HashMap would throw ConcurrentModificationException.
    //
    // 为什么使用 ConcurrentHashMap?
    // 仿真线程会不断写入此 Map。GUI 线程（可能）会从中读取。
    // 标准的 HashMap 在这种并发读写下会抛出 ConcurrentModificationException 异常。
    private final Map<String, VehicleData> vehicleHistory = new ConcurrentHashMap<>();

    // 2. Real-time Snapshot Storage / 实时快照存储
    // Stores: Edge ID -> Number of Cars (Density)
    // Used to determine the "Hottest Edge".
    //
    // 存储：Edge ID -> 车辆数量 (密度)
    // 用于确定“最拥堵的路段”。
    private final Map<String, Integer> currentEdgeDensity = new ConcurrentHashMap<>();

    /**
     * Core Update Logic
     * Called by SimulationManager at every single simulation step.
     * * 核心更新逻辑
     * 由 SimulationManager 在每一个仿真步进中调用。
     *
     * @param activeVehicleIds List of all vehicles currently in the simulation / 当前仿真中所有活跃的车辆 ID 列表
     */
    public void update(List<String> activeVehicleIds) {
        // A. Clear previous frame data
        // We only care about density *right now*, so we wipe the slate clean every 50ms.
        //
        // A. 清空上一帧数据
        // 我们只关心*此时此刻*的密度，所以每 50ms 都要将数据清零。
        currentEdgeDensity.clear();

        for (String id : activeVehicleIds) {
            try {
                // B. Fetch raw data from SUMO (TraCI calls)
                // These involve C++ Native Interface calls.
                //
                // B. 从 SUMO 获取原始数据 (TraCI 调用)
                // 这些涉及 C++ 本地接口调用。
                double speed = Vehicle.getSpeed(id);
                String edge = Vehicle.getRoadID(id);
                var traciPos = Vehicle.getPosition(id);
                Point2D.Double pos = new Point2D.Double(traciPos.getX(), traciPos.getY());

                // C. Update History (Persistent Data)
                // Logic: If vehicle is new, create data; otherwise, update existing.
                // 'putIfAbsent' is atomic and thread-safe.
                //
                // C. 更新历史 (持久化数据)
                // 逻辑：如果是新车，创建数据；否则，更新现有数据。
                // 'putIfAbsent' 是原子操作且线程安全的。
                vehicleHistory.computeIfAbsent(id, k->{
                    try {
                        var c = Vehicle.getColor(id);
                        String t = Vehicle.getTypeID(id);
                        java.awt.Color awtColor = new java.awt.Color(c.getR(), c.getG(), c.getB());
                        return new VehicleData(id, t, awtColor);
                    } catch (Exception e) {
                        return new VehicleData(id, "unknown", java.awt.Color.GRAY);
                    }
                        });

                // Update dynamic data (Speed, Position).
                // 更新动态数据 (速度, 位置)。
                VehicleData data = vehicleHistory.get(id);
                if(data != null) {data.update(speed, edge,pos);}

                // D. Update Density (Snapshot Data)
                // Filter: Ignore internal junctions (edges starting with ":").
                // In SUMO, moving inside an intersection is technically an edge,
                // but for analytics, we only care about actual streets.
                //
                // D. 更新密度 (快照数据)
                // 过滤：忽略内部路口 (以 ":" 开头的 Edge)。
                // 在 SUMO 中，在路口内部移动在技术上也算作在 Edge 上，
                // 但对于统计分析，我们只关心实际的街道
                if (!edge.startsWith(":")) {
                    // Increment count for this edge / 该路段计数 +1
                    currentEdgeDensity.put(edge, currentEdgeDensity.getOrDefault(edge, 0) + 1);
                }

            } catch (Exception e) {

                // Fail-safe: A vehicle might exit the map *after* getIDList() but *before* getSpeed().
                // This catch block prevents the whole simulation from crashing due to one missing car.
                //
                // 故障保护：车辆可能在调用 getIDList() *之后* 但在 getSpeed() *之前* 离开了地图。
                // 这个捕获块防止整个仿真因为一辆车的丢失而崩溃。
                System.err.println("Error updating stats for " + id);
            }
        }
    }

    // --- Data Access Methods for GUI (View) / 供 GUI (视图) 使用的数据访问方法 ---
    /**
     * Get average speed for a specific car.
     * 获取特定车辆的平均速度。
     */
    public double getVehicleAverageSpeed(String vehicleId) {
        if (vehicleHistory.containsKey(vehicleId)) {
            return vehicleHistory.get(vehicleId).getAverageSpeed();
        }
        return 0.0;
    }

    /**
     * Get the density map safely.
     * Returns a Defensive Copy to prevent ConcurrentModificationException in GUI.
     *
     * 安全地获取密度地图。
     * 返回防御性副本以防止 GUI 中出现 ConcurrentModificationException。
     */
    public Map<String, Integer> getEdgeDensities() {
        // [CRITICAL] Defensive Copying
        // Why: The 'currentEdgeDensity' is cleared by the Simulation Thread every 50ms.
        // If the GUI Thread tries to iterate over the *original* map while it's being cleared,
        // it will crash or show empty data.
        // Returning a 'new HashMap' creates a snapshot safe for the GUI to render slowly.
        //
        // [关键] 防御性复制
        // 原因：'currentEdgeDensity' 每 50ms 就会被仿真线程清空。
        // 如果 GUI 线程在它被清空的同时尝试遍历 *原始* Map，程序会崩溃或显示空数据。
        // 返回一个 'new HashMap' 创建了一个快照，供 GUI 慢慢渲染而互不干扰。
        return new HashMap<>(currentEdgeDensity); // 返回副本保护数据
    }

    /**
     * Calculate system-wide average speed.
     * 计算全系统的平均速度。
     */
    public double getSystemAverageSpeed() {
        if (vehicleHistory.isEmpty()) return 0.0;
        double sumAvg = 0.0;

        // Iterate over all tracked vehicles
        // 遍历所有被追踪的车辆
        for (VehicleData v : vehicleHistory.values()) {
            sumAvg += v.getAverageSpeed();
        }
        // Return the global average
        // 返回全局平均值
        return sumAvg / vehicleHistory.size();
    }

    /**
     * Get snapshot of all vehicle data for GUI rendering/filtering.
     * 获取所有车辆数据的快照用于 GUI 渲染/筛选。
     */
    public List<VehicleData> getAllVehicleData() {
        return new ArrayList<>(vehicleHistory.values());
    }
}
