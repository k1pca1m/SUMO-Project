package org.example;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TraCIColor;
public class VehicleInjection {
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
}
