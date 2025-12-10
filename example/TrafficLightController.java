package org.example;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;

public class TrafficLightController {
    private StringVector trafficLightIds;

    public TrafficLightController() {
        try {
            trafficLightIds = TrafficLight.getIDList();
            if (trafficLightIds.isEmpty()) {
                System.out.println("⚠️ 路网中未检测到红绿灯，请先在netedit中添加");
            } else {
                System.out.println("🚦 已获取交通灯ID列表：" + trafficLightIds);
            }
        } catch (Exception e) {
            System.err.println("❌ 初始化交通灯失败：" + e.getMessage());
            trafficLightIds = new StringVector();
        }
    }

    // 每60步切换一次红绿灯（绿灯30步，红灯30步）
    public void updateState(int currentStep) {
        if (trafficLightIds.isEmpty()) return;
        String tlId = trafficLightIds.get(0);

        try {
            if (currentStep % 60 < 30) {
                TrafficLight.setRedYellowGreenState(tlId, "GGGG"); // 绿灯
            } else {
                TrafficLight.setRedYellowGreenState(tlId, "rrrr"); // 红灯
            }
        } catch (Exception e) {
            System.err.println("❌ 切换交通灯[" + tlId + "]失败：" + e.getMessage());
        }
    }
}