package org.example;
import java.awt.Color;
import java.awt.geom.Point2D;

/**
 * VehicleData: A Data Transfer Object (DTO) used to store vehicle state.
 * Responsibility: Holds immutable identity info and mutable real-time data.
 *
 * VehicleData: 用于存储车辆状态的数据传输对象 (DTO)。
 * 职责：保存不可变的身份信息和可变的实时数据。
 */
public class VehicleData {

    // Immutable Identity / 不可变的身份标识
    // 'final' ensures the ID can never change once the object is created.
    // 'final' 确保 ID 一旦对象创建后就永远不能被修改。
    private final String vehicleId;

    // Accumulator for Average Calculation / 用于计算平均值的累加器
    // Memory Optimization: Instead of storing a List<Double> of every speed sample (which wastes memory),
    // we only store the 'Sum' and the 'Count'.
    // Average = totalSpeedSum / sampleCount. This takes O(1) memory space.
    //
    // 内存优化：我们不存储包含每个速度样本的 List<Double> (那样会浪费内存)，
    // 而是只存储 '总和 (Sum)' 和 '计数 (Count)'。
    // 平均值 = 总速度 / 采样次数。这只占用 O(1) 的内存空间。
    private final String typeId;    // 新增：车辆类型 (e.g. "car", "truck")
    private final Color color;      // 新增：车辆颜色 (用于绘图和筛选)

    // --- Dynamic Fields (Updated every frame) / 动态字段 (每帧更新) ---
    private Point2D.Double position; // X,Y Coordinates / X,Y 坐标
    private double currentSpeed;     // Instant speed / 瞬时速度

    // --- Statistics Accumulators / 统计累加器 ---
    // Used to calculate average speed over time.
    // 用于计算随时间变化的平均速度
    private double totalSpeedSum = 0.0;
    private long sampleCount = 0;

    // Current Location Tracker / 当前位置追踪器
    private String currentEdge = "";

    /**
     * Constructor / 构造函数
     * @param vehicleId The unique ID from SUMO (e.g., "veh_123") / 来自 SUMO 的唯一 ID
     */
    /**
     * Constructor.
     * @param vehicleId Unique ID from SUMO / 来自 SUMO 的唯一 ID
     * @param typeId Vehicle type / 车辆类型
     * @param color Display color / 显示颜色
     */
    public VehicleData(String vehicleId, String typeId, Color color) {
        this.vehicleId = vehicleId;
        this.typeId = typeId;
        this.color = color;
        this.position = new Point2D.Double(0, 0);
    }

    /**
     * Update State (Called every simulation step) / 更新状态 (每个仿真步调用)
     * Why: This method is called by 'StatisticsCollector' every 50ms to record the latest data.
     * * 原因: 'StatisticsCollector' 每 50ms 调用此方法以记录最新数据。
     * * @param currentSpeed Speed in m/s from SUMO / 来自 SUMO 的速度 (米/秒)
     * @param edge The ID of the road the vehicle is currently on / 车辆当前所在的道路 ID
     */
    public void update(double speed, String edge, Point2D.Double pos) {
        this.currentSpeed = speed;
        this.currentEdge = edge;
        this.position = pos;

        // Accumulate statistics.
        // 累加统计数据。
        this.totalSpeedSum += speed;
        this.sampleCount++;

    }

    /**
     * Calculate Average Speed / 计算平均速度
     * Logic: Returns (Total Speed / Count).
     * * 逻辑: 返回 (总速度 / 次数)。
     */
    public double getAverageSpeed() {
        if (sampleCount == 0) return 0.0;
        return totalSpeedSum / sampleCount;
    }

    /**
     * Getter for Current Edge / 获取当前道路
     */
    // Getters...
    public String getVehicleId() {return vehicleId;}
    public Color getColor() { return color; }
    public String getTypeId() { return typeId; }
    public double getCurrentSpeed() { return currentSpeed; }
    public Point2D.Double getPosition() { return position; }
    public String getCurrentEdge() {return currentEdge;}
    // 计算平均速度

}
