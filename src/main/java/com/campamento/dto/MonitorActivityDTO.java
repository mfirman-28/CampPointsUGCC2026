package com.campamento.dto;

public class MonitorActivityDTO {
    private String monitorName;
    private int totalPointsGivenToday;
    private int totalTransactionsToday;

    public MonitorActivityDTO() {
    }

    public MonitorActivityDTO(String monitorName, int totalPointsGivenToday, int totalTransactionsToday) {
        this.monitorName = monitorName;
        this.totalPointsGivenToday = totalPointsGivenToday;
        this.totalTransactionsToday = totalTransactionsToday;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public int getTotalPointsGivenToday() {
        return totalPointsGivenToday;
    }

    public void setTotalPointsGivenToday(int totalPointsGivenToday) {
        this.totalPointsGivenToday = totalPointsGivenToday;
    }

    public int getTotalTransactionsToday() {
        return totalTransactionsToday;
    }

    public void setTotalTransactionsToday(int totalTransactionsToday) {
        this.totalTransactionsToday = totalTransactionsToday;
    }
}
