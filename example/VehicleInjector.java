package org.example;

import org.eclipse.sumo.libtraci.Vehicle;

public class VehicleInjector {
    // 批量注入车辆（对外暴露的方法）
    public static void batchInject(int count, String routeId) {
        if (count <= 0) {
            System.err.println("❌ 注入数量不能小于等于0");
            return;
        }
        System.out.println("🚗 开始批量注入" + count + "辆车辆到路线：" + routeId);
        for (int i = 1; i <= count; i++) {
            // 车辆ID：veh_路线ID_序号，避免重复
            String vehId = "veh_" + routeId + "_" + i;
            // 出发时间：i*2秒，间隔发车避免扎堆
            String departTime = String.valueOf(i * 2.0);
            injectSingle(vehId, routeId, departTime);
        }
    }

    /**
     * 注入单辆车
     * @param vehId 车辆唯一ID
     * @param routeId 路线ID（rou.xml中定义的route1/route2/route3）
     * @param departTime 出发时间（String类型）
     */
    private static void injectSingle(String vehId, String routeId, String departTime) {
        try {
            Vehicle.add(
                    vehId,
                    routeId,
                    "car",
                    departTime,
                    "0.0",
                    "5.0", // 初始速度5m/s，低速防碰撞
                    "0.0",
                    ""
            );
            System.out.println("✅ 注入成功：" + vehId);
        } catch (Exception e) {
            System.err.println("❌ 注入失败：" + vehId + " → " + e.getMessage());
        }
    }
}