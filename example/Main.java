package org.example;

import org.eclipse.sumo.libtraci.Simulation;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.TraCIColor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    // 基础路径（保持你的原始配置）
    private static final String SUMO_HOME_PATH = "D:\\Program Files (x86)\\Eclipse\\Sumo";
    private static final String CONFIG_FILE_PATH = "C:\\Users\\lenovo\\Desktop\\map\\final_map.sumocfg";
    private static final String GUI_SETTINGS_FILE = "C:\\Users\\lenovo\\IdeaProjects\\SUMO\\SumoTraciJava\\gui_settings_full.xml";
    private static Process sumoProcess = null;
    // 新增：红绿灯控制器实例
    private static TrafficLightController tlController;

    public static void main(String[] args) {
        // 生成GUI配置文件（统一用这个方法，删除SumoTraciJava中的重复逻辑）
        createFixedGuiSettingsFile();

        // 2. 加载TraCI的DLL文件
        try {
            System.load(SUMO_HOME_PATH + "\\bin\\libtracijni.dll");
            System.out.println("✅ DLL 加载成功");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("❌ DLL 加载失败：" + e.getMessage());
            return;
        }

        // 3. SUMO启动参数（简化，保留关键配置）
        String[] sumoArgs = {
                SUMO_HOME_PATH + "\\bin\\sumo-gui.exe",
                "-c", CONFIG_FILE_PATH,
                "--step-length", "0.1",
                "--delay", "200",
                "--start",
                "--quit-on-end",
                "--gui-settings-file", GUI_SETTINGS_FILE
        };

        try {
            // 4. 启动SUMO（延长等待时间，确保路网加载完成）
            System.out.println("📌 SUMO启动命令：" + String.join(" ", sumoArgs));
            ProcessBuilder pb = new ProcessBuilder(sumoArgs);
            pb.inheritIO();
            sumoProcess = pb.start();
            Thread.sleep(8000); // 等待8秒，确保路网完全加载

            // 5. 建立TraCI连接
            StringVector traciArgs = new StringVector();
            for (String arg : sumoArgs) {
                traciArgs.add(arg);
            }
            Simulation.start(traciArgs);
            System.out.println("✅ TraCI连接成功，仿真正式开始");

            // ========== 关键修改1：初始化红绿灯控制器 ==========
            tlController = new TrafficLightController();

            // ========== 关键修改2：批量注入多辆车（多条路线） ==========
            // 路线1：原有效路线 -1279853327（对应rou.xml的route1）
            VehicleInjector.batchInject(5, "route1");
            // 路线2：对应rou.xml的route2（需确保rou.xml中已定义）
            VehicleInjector.batchInject(5, "route2");
            // 路线3：对应rou.xml的route3（需确保rou.xml中已定义）
            VehicleInjector.batchInject(5, "route3");

            // ========== 关键修改3：保留veh1并设置颜色 ==========
            if (!Vehicle.getIDList().contains("veh1")) {
                Vehicle.add("veh1", "route1", "car", "0.0");
                TraCIColor red = new TraCIColor(255, 0, 0, 255);
                Vehicle.setColor("veh1", red);
                Vehicle.setSpeed("veh1", 5.0); // 低速，避免碰撞
                System.out.println("✅ 成功创建车辆veh1（路线：route1）");
            }

            // 8. 仿真循环（延长步数到2000，确保多车跑完）
            double totalSpeed = 0.0;
            int step = 0;
            int veh1Count = 0;
            while (Simulation.getMinExpectedNumber() > 0 && step < 2000) {
                Simulation.step();
                step++;

                // ========== 关键修改4：每步更新红绿灯状态（替代原200步切换） ==========
                tlController.updateState(step);

                // 统计veh1的速度
                if (Vehicle.getIDList().contains("veh1")) {
                    double speed = Vehicle.getSpeed("veh1");
                    if (speed >= 0) {
                        totalSpeed += speed;
                        veh1Count++;
                        System.out.printf("Step %d | veh1 速度：%.2f m/s (%.2f km/h)%n",
                                step, speed, speed * 3.6);
                    }
                }
            }

            // 9. 仿真结束统计
            System.out.println("\n📊 仿真结束统计：");
            System.out.println("当前所有车辆ID：" + Vehicle.getIDList());
            if (veh1Count > 0) {
                System.out.printf("veh1 平均速度：%.2f m/s (%.2f km/h)%n",
                        totalSpeed / veh1Count, (totalSpeed / veh1Count) * 3.6);
            } else {
                System.out.println("❌ veh1 未被检测到（检查路线ID或路网加载）");
            }

        } catch (Exception e) {
            System.err.println("❌ 仿真异常：" + e.getMessage());
            e.printStackTrace();
        } finally {
            // 10. 关闭资源
            try {
                Simulation.close();
                if (sumoProcess != null && sumoProcess.isAlive()) {
                    sumoProcess.destroy();
                }
                System.out.println("✅ SUMO 已正常关闭");
            } catch (Throwable t) {
                System.err.println("⚠️ SUMO 关闭失败：" + t.getMessage());
            }
        }
    }

    /**
     * 修复后的GUI配置文件生成方法（统一用这个，删除SumoTraciJava中的重复方法）
     */
    private static void createFixedGuiSettingsFile() {
        String validGuiConfig = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gui_settings>
                <window>
                    <size x="1440" y="900"/>
                    <pos x="200" y="100"/>
                    <title value="多车多路线SUMO仿真"/>
                </window>
                <view>
                    <zoom value="300"/> <!-- 放大视角，能看到多条道路 -->
                    <follow value="veh1"/>
                    <follow.offset x="0" y="-80"/>
                    <rotate value="true"/>
                    <smooth value="true"/>
                </view>
                <color>
                    <background r="0" g="0" b="0"/>
                    <text r="255" g="255" b="255"/>
                    <highlight r="255" g="215" b="0"/>
                </color>
                <draw>
                    <vehicles value="true">
                        <color r="0" g="255" b="127"/>
                        <size value="1.5"/>
                        <label value="true"/>
                        <speed value="true"/>
                        <label.font.size value="14"/>
                    </vehicles>
                    <lanes value="true">
                        <color r="0" g="191" b="255"/>
                        <width value="4.0"/>
                        <edge.label value="true"/>
                        <edge.label.font.size value="12"/>
                    </lanes>
                    <trafficlights value="true">
                        <size value="3.0"/>
                        <color.red r="255" g="0" b="0"/>
                        <color.green r="0" g="255" b="0"/>
                        <color.yellow r="255" g="255" b="0"/>
                    </trafficlights>
                    <nodes value="false"/>
                    <sidewalks value="false"/>
                    <crossings value="true"/>
                </draw>
                <panel>
                    <bottom value="true"/>
                    <bottom.height value="80"/>
                    <statusbar value="true"/>
                </panel>
            </gui_settings>
            """;

        try {
            Files.createDirectories(Paths.get(GUI_SETTINGS_FILE).getParent());
            Files.write(Paths.get(GUI_SETTINGS_FILE), validGuiConfig.getBytes("UTF-8"));
            System.out.println("✅ GUI配置文件生成成功：" + GUI_SETTINGS_FILE);
        } catch (IOException e) {
            System.err.println("⚠️ GUI配置文件生成失败：" + e.getMessage());
        }
    }
}