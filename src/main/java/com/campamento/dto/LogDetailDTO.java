package com.campamento.dto;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogDetailDTO {
    private Long logId;
    private LocalDateTime time;
    private String monitorName;
    private int numPoints;
    private String kidName;
    private String groupName;

    public LogDetailDTO() {
    }

    public LogDetailDTO(Long logId, LocalDateTime time, String monitorName, int numPoints, String kidName, String groupName) {
        this.logId = logId;
        this.time = time;
        this.monitorName = monitorName;
        this.numPoints = numPoints;
        this.kidName = kidName;
        this.groupName = groupName;
    }

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public String getMonitorName() {
        return monitorName;
    }

    public void setMonitorName(String monitorName) {
        this.monitorName = monitorName;
    }

    public int getNumPoints() {
        return numPoints;
    }

    public void setNumPoints(int numPoints) {
        this.numPoints = numPoints;
    }

    public String getKidName() {
        return kidName;
    }

    public void setKidName(String kidName) {
        this.kidName = kidName;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String toFormattedString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String formattedDate = time != null ? time.format(formatter) : "N/A";
        String ptsStr = (numPoints >= 0 ? "+" : "") + numPoints + " pts";
        return String.format("%-20s | %-25s | %8s | %-25s | %s", formattedDate, monitorName, ptsStr, kidName, groupName);
    }
}
