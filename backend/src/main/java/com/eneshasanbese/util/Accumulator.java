package com.eneshasanbese.util;

public class Accumulator {
    private double totalSpeedWeighted = 0;
    private long totalVehicles = 0;

    public void add(double speed, int vehicleCount) {
        totalSpeedWeighted += speed * vehicleCount;
        totalVehicles += vehicleCount;
    }

    public double average() {
        return totalVehicles == 0 ? 0 : totalSpeedWeighted / totalVehicles;
    }
}