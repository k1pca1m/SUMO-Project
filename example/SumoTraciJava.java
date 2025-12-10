package org.example;

import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.Vehicle;
import org.eclipse.sumo.libtraci.Simulation;
import java.io.IOException;

public class SumoTraciJava {
    // 基础路径（保持不变）
    private static final String SUMO_HOME_PATH = "D:\\Program Files (x86)\\Eclipse\\Sumo";
    private static final String CONFIG_FILE_PATH = "C:\\Users\\lenovo\\Desktop\\map\\final_map.sumocfg";
    public static final String GUI_SETTINGS_FILE = "C:\\Users\\lenovo\\IdeaProjects\\SUMO\\SumoTraciJava\\gui_settings_full.xml";

    private static Process sumoProcess = null;

    public static void main(String[] args) {
        // 不再重复生成GUI配置，直接使用Main生成的文件

        // 加载DLL
        try {
            System.load(SUMO_HOME_PATH + "\\bin\\libtracijni.dll");
            System.out.println("✅ DLL 加载成功");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("❌ DLL 加载失败：" + e.getMessage());
            return;
        }

        // SUMO启动参数
        String[] sumoArgs = {
                SUMO_HOME_PATH + "\\bin\\sumo-gui.exe",
                "-c", CONFIG_FILE_PATH,
                "--start",
                "--quit-on-end",
                "--delay", "500"
        };

        try {
            System.out.println("📌 SUMO启动命令：" + String.join(" ", sumoArgs));
            ProcessBuilder pb = new ProcessBuilder(sumoArgs);
            pb.inheritIO();
            sumoProcess = pb.start();
            Thread.sleep(5000);

            StringVector argVector = new StringVector();
            for (String s : sumoArgs) {
                argVector.add(s);
            }
            Simulation.start(argVector);
            System.out.println("✅ 仿真开始");

            // 仿真循环
            double totalSpeed = 0.0;
            int step = 0;
            int veh1Count = 0;
            while (Simulation.getMinExpectedNumber() > 0 && step < 1000) {
                Simulation.step();
                step++;
                if (Vehicle.getIDList().contains("veh1")) {
                    double speed = Vehicle.getSpeed("veh1");
                    if (speed >= 0) {
                        totalSpeed += speed;
                        veh1Count++;
                        System.out.printf("Step %d | veh1速度：%.2f m/s%n", step, speed);
                    }
                }
            }

            System.out.println("\n仿真结束");
            if (veh1Count > 0) {
                System.out.printf("veh1平均速度：%.2f m/s%n", totalSpeed / veh1Count);
            } else {
                System.out.println("veh1未出现");
            }

        } catch (Exception e) {
            System.err.println("❌ 错误：" + e.getMessage());
        } finally {
            try {
                Simulation.close();
                if (sumoProcess != null && sumoProcess.isAlive()) {
                    sumoProcess.destroy();
                }
            } catch (Throwable t) {
                System.err.println("⚠️ 关闭失败：" + t.getMessage());
            }
        }
    }
}