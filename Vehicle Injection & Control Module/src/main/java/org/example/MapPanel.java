package org.example;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.ArrayList;

/**
 * MapPanel: Custom Swing Component for visualization.
 * Responsibility: Draws the vehicles based on coordinates provided by the SimulationManager.
 *
 * MapPanel: 用于可视化的自定义 Swing 组件。
 * 职责：根据 SimulationManager 提供的坐标绘制车辆。
 */
public class MapPanel extends JPanel{

    // The list of vehicles to be drawn in the current frame.
    // 当前帧需要绘制的车辆列表。
    private List<VehicleData> displayList = new ArrayList<>();

    public MapPanel() {
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, Color.LIGHT_GRAY));
    }

    /**
     * Updates the data model and requests a repaint.
     * Called by SimulationManager (on EDT).
     *
     * 更新数据模型并请求重绘。
     * 由 SimulationManager (在 EDT 上) 调用。
     */
    public void updateData(List<VehicleData> parsedData) {

        // Create a local copy to avoid concurrency issues during painting.
        // 创建副本，防止并发修改异常
        this.displayList = new ArrayList<>(parsedData);

        // Trigger the paintComponent method.
        // 触发 paintComponent 方法。
        repaint(); // 告诉 Java 重新调用 paintComponent
    }

    /**
     * The Drawing Routine.
     * 绘画例程。
     */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g); // 清除背景
        Graphics2D g2d = (Graphics2D) g;

        // Enable Anti-Aliasing for smooth circles.
        // 开启抗锯齿以获得平滑的圆形。
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Watermark /左上角水印
        g2d.setColor(Color.LIGHT_GRAY);
        g2d.drawString("Map Visualization", 10, 20);

        // Iterate and Draw Vehicles
        // 遍历列表绘制每一辆车
        for (VehicleData v : displayList) {
            Point2D.Double p = v.getPosition();

            // Coordinate Scaling: Scale up SUMO coordinates to fit the panel better.
            // 坐标缩放：放大 SUMO 坐标以更好地适应面板。
            int x = (int) (p.x * 1.5);
            int y = (int) (p.y * 1.5);

            // 1. Set Color (From VehicleData)
            // 1. 设置颜色 (使用 VehicleData 里的颜色)
            g2d.setColor(v.getColor());
            g2d.fillOval(x, y, 8, 8); // 画圆点

            // 2. Special shape for Trucks
            // 2. 绘制特殊车型 (如果是卡车，画个框)
            if ("truck".equalsIgnoreCase(v.getTypeId())) {
                g2d.drawRect(x - 1, y - 1, 10, 10);
            }

            // 3. Draw ID Text
            // 3. 绘制 ID (方便您调试控制功能)
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.PLAIN, 10));
            g2d.drawString(v.getVehicleId(), x + 10, y);
        }

        // Stats Overlay / 统计覆盖层
        g2d.setColor(Color.GRAY);
        g2d.drawString("Count: " + displayList.size(), 10, getHeight() - 10);
    }
}
