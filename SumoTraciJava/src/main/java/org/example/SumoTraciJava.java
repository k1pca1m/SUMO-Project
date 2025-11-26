package org.example;

import org.eclipse.sumo.libtraci.*;
import org.eclipse.sumo.libtraci.StringVector;
import org.eclipse.sumo.libtraci.TrafficLight;
import org.eclipse.sumo.libtraci.Vehicle;
import static org.eclipse.sumo.libtraci.Simulation.*;

public class SumoTraciJava {
        // 【重要：定义 SUMO 安装根目录】
    // 在 Windows 上，通常是 C:\Program Files (x86)\Eclipse\Sumo
    private static final String SUMO_HOME_PATH = "C:\\Program Files (x86)\\Eclipse\\Sumo";//use the local position,

    // 【重要：定义配置文件的绝对路径】
    // 请将此处路径替换为您 demo.sumocfg 文件实际存放的绝对路径！
    private static final String CONFIG_FILE_PATH = "D:\\GhtDemo\\sumo\\sumo\\SumoTraciJava\\demo2.sumocfg";//same as above

        public static void main(String[] args) {
            try {
                System.load("C:\\Program Files (x86)\\Eclipse\\Sumo\\bin\\libtracijni.dll");//save as above
                System.out.println("✅ DLL 加载成功！(环境配置没问题)");
            } catch (UnsatisfiedLinkError e) {
                System.err.println("❌ DLL 加载失败！核心原因如下：");
                System.err.println(e.getMessage());
                // 如果这里显示 "Can't find dependent libraries"，说明是 PATH 没生效
                return; // 终止程序
            }//judge if DLL works
            // 必须先设置 SUMO_HOME 环境变量，或者在代码里指定路径
            try {
                // Step 4: 启动参数
                String[] sumoArgs = {
                        //"sumo-gui",
                        //"-c", "demo.sumocfg",
                        // 使用绝对路径启动 sumo-gui
                        "\""+SUMO_HOME_PATH + "\\bin\\sumo-gui.exe"+"\"",
                        // 使用绝对路径指定配置文件，避免找不到文件的问题
                        "-c", CONFIG_FILE_PATH,
                        "--step-length", "0.05",
                        "--delay", "500", // 1000ms 太慢了，建议改小一点
                        "--lateral-resolution", "0.1",
                        "--start" // 自动开始，不需要点 Play 按钮
                };//open sumo and set the parameter


                //I'm not sure if the following code works!!!



                // Step 5: 修正 StringVector 的构造
                StringVector argVector = new StringVector();
                for (String s : sumoArgs) {
                    argVector.add(s);
                }

                Simulation.start(argVector);

                double totalSpeed = 0.0;
                int step = 0;
                int veh1PresenceCount = 0; // 新增：记录车辆实际存在的帧数

                // Step 8: 主循环
                while (Simulation.getMinExpectedNumber() > 0) {
                    Simulation.step();
                    step++;

                    // 检查车辆是否存在
                    if (Vehicle.getIDList().contains("veh1")) {
                        double speed = Vehicle.getSpeed("veh1");

                        // 只有速度大于 -1 (无效值) 时才统计
                        if (speed >= 0) {
                            totalSpeed += speed;
                            veh1PresenceCount++;
                            System.out.printf("Step %4d | veh1 speed = %6.2f m/s%n", step, speed);
                        }
                    } else {
                        // 可选：为了控制台清爽，可以注释掉这行
                        // System.out.printf("Step %4d | veh1 not present%n", step);
                    }
                }

                System.out.println("Simulation finished.");

                // Step 9: 修正平均速度算法
                if (veh1PresenceCount > 0) {
                    System.out.printf("Average speed of veh1 (active time) = %.2f m/s%n", totalSpeed / veh1PresenceCount);
                } else {
                    System.out.println("veh1 never appeared in the simulation.");
                }

            } catch (Throwable e) {// 捕获 Throwable，确保能看到所有异常
                System.err.println("❌ TraCI 仿真过程中发生错误！");
                e.printStackTrace();
            } finally {
                // Step 10: 务必关闭
                try {
                    Simulation.close();
                } catch (Throwable t) {
                    System.err.println("关闭仿真连接时出错：" + t.getMessage());
                    //throw new RuntimeException(e);
                }
            }
        }
}

