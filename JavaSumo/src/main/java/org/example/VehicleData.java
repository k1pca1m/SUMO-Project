package org.example;

public class VehicleData {
    private final String vehicleId;
    private double totalSpeedSum = 0.0;
    private long sampleCount = 0;
    private String currentEdge = "";

    public VehicleData(String vehicleId) {
        this.vehicleId = vehicleId;
    }

    public void update(double currentSpeed, String edge) {
        this.totalSpeedSum += currentSpeed;
        this.sampleCount++;
        this.currentEdge = edge;
    }

    public double getAverageSpeed() {
        if (sampleCount == 0) return 0.0;
        return totalSpeedSum / sampleCount;
    }

    public String getCurrentEdge() {
        return currentEdge;
    }
}
