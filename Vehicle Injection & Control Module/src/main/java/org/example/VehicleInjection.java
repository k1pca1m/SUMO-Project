package org.example;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.Route; // 必须导入 Route
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TraCIColor;
import java.util.Random;
import org.eclipse.sumo.libtraci.VehicleType; // [新增 ] 导入 VehicleType
import org.eclipse.sumo.libtraci.Edge; // [新增 ] 导入 Edge 类
import java.util.HashSet; // [新增]
import java.util.Set;     // [新增]

/**
 * VehicleInjection: A helper class to handle TraCI complexity.
 * This class ensures that vehicles are spawned correctly without crashing the simulation.
 * * VehicleInjection: 用于处理 TraCI 复杂性的辅助类。
 * 该类确保车辆能够被正确生成，且不会导致仿真崩溃。
 */
public class VehicleInjection {
    private final Random random = new Random();

    // 1. Initialization Flag Cache
    // Why: Checking if a vehicle type exists involves a network call to SUMO.
    // Doing this for every single car spawn (e.g., 100 times) would slow down the program.
    // We use a Set to cache initialized types.
    //
    // 1. 初始化标志缓存
    // 原因：检查车辆类型是否存在涉及对 SUMO 的网络调用。
    // 如果每次生成车辆（例如 100 次）都做这个检查，会拖慢程序速度。
    // 我们使用 Set 来缓存已初始化的类型。
    private static final Set<String> initializedTypes = new HashSet<>();

    // 2. Edge List Cache
    // Why: 'Edge.getIDList()' asks SUMO to return thousands of strings. Heavy I/O.
    // We cache it to make random route calculation instant.
    //
    // 2. 道路列表缓存
    // 原因：'Edge.getIDList()' 会请求 SUMO 返回数千个字符串。繁重的 I/O。
    // 我们缓存它以使随机路径计算瞬间完成。
    private static StringVector cachedEdgeList = null;

    /**
     * Helper: Ensure the vehicle type "car" exists.
     * Why: If you try to spawn a vehicle with type "car" but SUMO only knows "DEFAULT_VEHTYPE",
     * the simulation will crash immediately. This method prevents that.
     * * 辅助方法：确保车辆类型 "car" 存在。
     * 原因：如果你尝试生成一个类型为 "car" 的车辆，但 SUMO 只知道 "DEFAULT_VEHTYPE"，
     * 仿真会立即崩溃。此方法用于防止这种情况。
     */
    private void ensureVehicleTypeExists(String typeId) {

        // Optimization: Check cache first.
        // 优化：首先检查缓存。
        if (initializedTypes.contains(typeId)) {return;}
            try {

                // Expensive Call: Fetch all known types from SUMO.
                // 昂贵的调用：从 SUMO 获取所有已知类型。
                StringVector existingTypes = VehicleType.getIDList();
                boolean exists = false;
                for (String t : existingTypes) {
                    if (t.equals(typeId)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    // Critical: Type missing. Clone default.
                    // 关键：类型缺失。克隆默认类型。
                    VehicleType.copy("DEFAULT_VEHTYPE", typeId);

                    // Custom logic for "truck": Make it longer.
                    // "truck" 的自定义逻辑：使其更长。
                    if (typeId.equalsIgnoreCase("truck")) {
                        VehicleType.setLength(typeId, 7.0); // 卡车长 7米
                    }

                    // Add to cache so we don't check again.
                    // 加入缓存，以便不再检查。
                    initializedTypes.add(typeId);
                    System.out.println("[TraCI] Auto-created vehicle type: " + typeId);
                }

            } catch (Exception e) {
                // Error handling: prevent crash even if check fails
                // 错误处理：即使检查失败也防止崩溃
                System.err.println("[TraCI Warning] Checking vehicle type failed: " + e.getMessage());
            }
    }

    /**
     * Method A: Spawn using a pre-defined Route ID (Static).
     * Used when your .rou.xml file already has routes like "route_0".
     * * 方法 A: 使用预定义的路 ID 生成车辆 (静态)。
     * 当你的 .rou.xml 文件中已经有像 "route_0" 这样的路线时使用。
     * * @param vehicleId Unique ID / 唯一 ID
     * @param routeId   Existing route ID / 现有的路线 ID
     * @param typeId    Vehicle type (e.g. "car") / 车辆类型
     */
    public void addVehicle(String vehicleId, String routeId, String typeId) throws Exception {
        ensureVehicleTypeExists(typeId);// Safety check / 安全检查
        try {
            Vehicle.add(
                    vehicleId,
                    routeId,
                    typeId,
                    "now",     // Insert immediately / 立即插入
                    "first",          // First available lane / 第一条可用车道
                    "0",              // Start at position 0 / 起始位置 0
                    "0"               // Start at speed 0 / 起始速度 0
                     );
            Vehicle.setColor(vehicleId, new TraCIColor(255, 0, 0, 255));
            System.out.println("[TraCI] Vehicle added: " + vehicleId);
        } catch (Exception e) {
            System.err.println("[TraCI Error] Failed to add vehicle: " + e.getMessage());
        }
    }

    /**
     * Method B: Spawn on a specific Edge (Dynamic).
     * This is the complex Logic: "Spawn here, then go somewhere random."
     * * 方法 B: 在指定道路上生成车辆 (动态)。
     * 这是复杂的逻辑：“在这里生成，然后去一个随机的地方。”
     */
    public void addVehicleOnEdge(String vehicleId, String edgeId, String typeId,TraCIColor color) throws Exception {
        // Safety check / 安全检查
        ensureVehicleTypeExists(typeId);
        try {
            // Step 1: Create a temporary route for this specific vehicle.
            // Why: SUMO requires a vehicle to have a route to spawn. We create a route
            // containing ONLY the start edge.
            //
            // 第一步：为这辆特定的车创建一个临时路由。
            // 原因：SUMO 要求车辆必须有路由才能生成。我们创建一个只包含起点道路的路由。
            String dynamicRouteId = "route_" + vehicleId;

            try {
                StringVector edges = new StringVector();
                edges.add(edgeId);
                Route.add(dynamicRouteId, edges);
            } catch (Exception e) {
                // Route might already exist if ID recycled. Ignore.
                // 如果 ID 被回收，路由可能已存在。忽略。
            }

            // Step 2: Spawn the vehicle.
            // "free": Look for empty space to avoid collision.
            //
            // 第二步：生成车辆。
            // "free": 寻找空隙以避免碰撞。
            Vehicle.add(
                    vehicleId,
                    dynamicRouteId,
                    typeId,
                    "now",
                    "free", // "free": Find a gap to avoid collision (don't stack cars) / 寻找空隙避免碰撞 (不要重叠车辆)
                    "0",
                    "0"
            );
            Vehicle.setColor(vehicleId, color);


            // ==========================================
            // Step 3: Dynamic Re-routing (The Brains)
            // 第三步：动态重路由 (核心智能)
            // ==========================================

            // Initialize cache if empty / 如果缓存为空则初始化
            if (cachedEdgeList == null) {
                cachedEdgeList = Edge.getIDList();
            }

            String targetEdge = edgeId;
            long edgeCount = cachedEdgeList.size();

            // Algorithm: Find a valid destination
            // We try 10 times to pick a random edge.
            // Filter: Must not be an internal junction (starts with ":") and not the start edge.
            //
            // 算法：寻找合法的目的地
            // 我们尝试 10 次随机选择一条道路。
            // 过滤条件：不能是内部路口 (以 ":" 开头) 且不能是起点道路。
            for (int i = 0; i < 10; i++) {
                String candidate = cachedEdgeList.get(random.nextInt((int) edgeCount));
                if (!candidate.startsWith(":") && !candidate.equals(edgeId)) {
                    targetEdge = candidate;
                    break;
                }
            }

            // Step 4: Instruct SUMO to calculate the path
            // Why: 'changeTarget' triggers SUMO's internal routing engine (Dijkstra/A*).
            // The car will now automatically drive from 'edgeId' to 'targetEdge'.
            //
            // 第四步：指示 SUMO 计算路径
            // 原因：'changeTarget' 会触发 SUMO 内部的路由引擎 (Dijkstra/A*)。
            // 车辆现在会自动从 'edgeId' 驾驶到 'targetEdge'。
            if (!targetEdge.equals(edgeId)) {
                try {
                    Vehicle.changeTarget(vehicleId, targetEdge);
                } catch (Exception ex) {
                    // Logic: If random target is unreachable (e.g. disconnected island), ignore.
                    // 逻辑：如果随机目标不可达（例如断开的孤岛区域），忽略。
                }
            }
            System.out.println("[TraCI] Dynamic Vehicle added on edge " + edgeId + ": " + vehicleId);

        } catch (Exception e) {
            System.err.println("[TraCI Error] Failed to add vehicle on edge " + edgeId + ": " + e.getMessage());
        }
    }
    public void setVehicleColor(String id, TraCIColor color) {
        try {
            Vehicle.setColor(id, color);
        } catch (Exception e) {
            System.err.println("[TraCI Error] Could not set color for " + id);
        }
    }

    public void setVehicleMaxSpeed(String id, double speed) {
        try {
            Vehicle.setMaxSpeed(id, speed);
        } catch (Exception e) {
            System.err.println("[TraCI Error] Could not set speed for " + id);
        }
    }
}
