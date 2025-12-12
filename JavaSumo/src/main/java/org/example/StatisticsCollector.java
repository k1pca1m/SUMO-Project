package org.example;
import org.eclipse.sumo.libtraci.Vehicle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
public class StatisticsCollector {
    private final Map<String, VehicleData> vehicleHistory = new ConcurrentHashMap<>();

    // 存储当前帧的道路密度 (EdgeID -> Count)
    private final Map<String, Integer> currentEdgeDensity = new ConcurrentHashMap<>();

    /**
     * 在 SimulationManager 的每一步调用此方法
     * @param activeVehicleIds 当前活跃的所有车辆 ID 列表
     */
    public void update(List<String> activeVehicleIds) {
        // 1. 清空上一帧的密度数据
        currentEdgeDensity.clear();

        for (String id : activeVehicleIds) {
            try {
                // 获取车辆实时数据
                double speed = Vehicle.getSpeed(id);
                String edge = Vehicle.getRoadID(id);

                // A. 更新/创建车辆历史数据
                vehicleHistory.putIfAbsent(id, new VehicleData(id));
                VehicleData data = vehicleHistory.get(id);
                data.update(speed, edge);

                // B. 更新道路密度
                // 忽略路口内部连接段 (通常以 : 开头)
                if (!edge.startsWith(":")) {
                    currentEdgeDensity.put(edge, currentEdgeDensity.getOrDefault(edge, 0) + 1);
                }

            } catch (Exception e) {
                // 车辆可能在这一瞬间刚离开，捕获异常防止崩溃
                System.err.println("Error updating stats for " + id);
            }
        }
    }

    // --- 获取统计结果的方法 ---

    public double getVehicleAverageSpeed(String vehicleId) {
        if (vehicleHistory.containsKey(vehicleId)) {
            return vehicleHistory.get(vehicleId).getAverageSpeed();
        }
        return 0.0;
    }

    public Map<String, Integer> getEdgeDensities() {
        return new HashMap<>(currentEdgeDensity); // 返回副本保护数据
    }

    // 获取全系统所有车辆的平均速度
    public double getSystemAverageSpeed() {
        if (vehicleHistory.isEmpty()) return 0.0;
        double sumAvg = 0.0;
        for (VehicleData v : vehicleHistory.values()) {
            sumAvg += v.getAverageSpeed();
        }
        return sumAvg / vehicleHistory.size();
    }
}
