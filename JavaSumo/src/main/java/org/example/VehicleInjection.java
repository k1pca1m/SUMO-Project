package org.example;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.Route; // 必须导入 Route
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIColor;
import java.util.Random;
import org.eclipse.sumo.libtraci.VehicleType; // [新增 ] 导入 VehicleType
import org.eclipse.sumo.libtraci.Edge; // [新增 ] 导入 Edge 类
public class VehicleInjection {
    private final Random random = new Random();
    // [新增 ] 用于记录是否已经定义过 "car" 类型，避免重复定义报错
    private static boolean carTypeInitialized = false;
    // [新增 ] 缓存全图的道路列表，避免每次注入都去问 SUMO 要一遍数据（提高性能）
    private static StringVector cachedEdgeList = null;
    private void ensureVehicleTypeExists(String typeId) {
        if (!carTypeInitialized) {
            try {
                // 尝试获取现有类型列表，看看 typeId 是否存在
                StringVector existingTypes = VehicleType.getIDList();
                boolean exists = false;
                for (String t : existingTypes) {
                    if (t.equals(typeId)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    // 如果不存在，复制 SUMO 的默认类型为 "car"
                    // "DEFAULT_VEHTYPE" 是 SUMO 内置的通用类型
                    VehicleType.copy("DEFAULT_VEHTYPE", typeId);
                    System.out.println("[TraCI] Auto-created vehicle type: " + typeId);
                }
                carTypeInitialized = true;
            } catch (Exception e) {
                // 如果出错（比如类型已存在但我们没检测到），捕获异常防止程序崩溃
                System.err.println("[TraCI Warning] Checking vehicle type failed: " + e.getMessage());
                // 假设它已经存在，继续运行
                carTypeInitialized = true;
            }
        }
    }
    /**
     * 封装底层的 Vehicle.add 方法。
     * * @param vehicleId 新车辆的 ID
     * @param routeId   车辆要行驶的路线 ID (必须在 .rou.xml 中存在)
     * @param typeId    车辆类型 ID (例如 "car")
     * @throws Exception 如果 SUMO 拒绝添加 (例如 ID 重复)
     */
    public void addVehicle(String vehicleId, String routeId, String typeId) throws Exception {
        // 对应 Vehicle.cpp 中的 add 方法签名
        // 我们使用最常用的默认参数：
        // depart="now" (立即出发), departLane="first" (第一条可用车道)
        // departPos="0" (起始位置), departSpeed="0" (起始速度)
        ensureVehicleTypeExists(typeId);// [修改] 先确保类型存在
        try {
            Vehicle.add(
                    vehicleId,
                    routeId,
                    typeId,
                    "now",     // depart time
                    "first",   // depart lane
                    "0",       // depart pos
                    "0"        // depart speed
                     );
            Vehicle.setColor(vehicleId, new TraCIColor(255, 0, 0, 255));
            System.out.println("[TraCI] Vehicle added: " + vehicleId);
        } catch (Exception e) {
            System.err.println("[TraCI Error] Failed to add vehicle: " + e.getMessage());
        }
        // 可选：设置颜色，直观显示这是注入的车辆
        // Vehicle.setColor(vehicleId, new org.eclipse.sumo.libtraci.TraCIColor(255, 0, 0, 255));
    }
    /**
     * [新增功能] 动态在指定 Edge 上生成车辆
     * 逻辑：创建一个以该 Edge 为起点的临时路由，然后生成车辆
     */
    public void addVehicleOnEdge(String vehicleId, String edgeId, String typeId) throws Exception {
        // [修改] 先确保类型存在
        ensureVehicleTypeExists(typeId);
        try {
            // 1. 构造一个唯一的 Route ID (例如: route_veh_01)
            String dynamicRouteId = "route_" + vehicleId;

            // 2. 创建包含该 Edge 的路由列表
            StringVector edges = new StringVector();
            edges.add(edgeId);

            // 3. 通过 TraCI 动态添加路由
            Route.add(dynamicRouteId, edges);

            // 4. 添加车辆并关联这个动态路由
            Vehicle.add(
                    vehicleId,
                    dynamicRouteId,
                    typeId,
                    "now",
                    "free", // 尝试寻找空闲位置，避免重叠冲突
                    "0",
                    "0"
            );

            // 5. 设置随机颜色以区分批量生成的车辆
            Vehicle.setColor(vehicleId, new TraCIColor(
                    random.nextInt(255),
                    random.nextInt(255),
                    random.nextInt(255),
                    255));

            // ==========================================
            // [新增逻辑] 3. 立即为车辆设置一个随机的、遥远的目的地
            // ==========================================

            // 首次运行时，获取地图所有道路 ID
            if (cachedEdgeList == null) {
                cachedEdgeList = Edge.getIDList();
            }

            // 尝试随机找一个合法的目的地
            String targetEdge = edgeId; // 默认如果不成功就保持原样
            long edgeCount = cachedEdgeList.size();

            // 尝试 10 次寻找一个不同的、非内部道路（不以 : 开头）作为终点
            for (int i = 0; i < 10; i++) {
                String candidate = cachedEdgeList.get(random.nextInt((int) edgeCount));

                // 排除路口内部路段(:开头) 和 当前所在的起点路段
                if (!candidate.startsWith(":") && !candidate.equals(edgeId)) {
                    targetEdge = candidate;
                    break;
                }
            }

            // 调用 SUMO 更改目标，SUMO 会自动计算路径
            if (!targetEdge.equals(edgeId)) {
                try {
                    Vehicle.changeTarget(vehicleId, targetEdge);
                    // System.out.println("   -> Rerouted to target: " + targetEdge);
                } catch (Exception ex) {
                    // 如果随机到的路不可达（也就是没有路能通过去），这里可能会报错，忽略即可，车会开到当前路尽头消失
                    // System.err.println("   -> Could not route to " + targetEdge);
                }
            }
            System.out.println("[TraCI] Dynamic Vehicle added on edge " + edgeId + ": " + vehicleId);

        } catch (Exception e) {
            System.err.println("[TraCI Error] Failed to add vehicle on edge " + edgeId + ": " + e.getMessage());
        }
    }
}
