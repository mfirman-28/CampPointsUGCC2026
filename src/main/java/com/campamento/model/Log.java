package com.campamento.model;

import java.time.LocalDateTime;

public class Log {
    private Long id;
    private Long kidId;
    private Long monitorId;
    private int numPoints;
    private LocalDateTime time;

    private boolean isAnnulled;

    public Log() {
    }

    public Log(Long id, Long kidId, Long monitorId, int numPoints, LocalDateTime time) {
        this.id = id;
        this.kidId = kidId;
        this.monitorId = monitorId;
        this.numPoints = numPoints;
        this.time = time;
        this.isAnnulled = false;
    }
    
    public Log(Long id, Long kidId, Long monitorId, int numPoints, LocalDateTime time, boolean isAnnulled) {
        this.id = id;
        this.kidId = kidId;
        this.monitorId = monitorId;
        this.numPoints = numPoints;
        this.time = time;
        this.isAnnulled = isAnnulled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getKidId() {
        return kidId;
    }

    public void setKidId(Long kidId) {
        this.kidId = kidId;
    }

    public Long getMonitorId() {
        return monitorId;
    }

    public void setMonitorId(Long monitorId) {
        this.monitorId = monitorId;
    }

    public int getNumPoints() {
        return numPoints;
    }

    public void setNumPoints(int numPoints) {
        this.numPoints = numPoints;
    }

    public LocalDateTime getTime() {
        return time;
    }

    public void setTime(LocalDateTime time) {
        this.time = time;
    }

    public boolean isAnnulled() {
        return isAnnulled;
    }

    public void setAnnulled(boolean annulled) {
        isAnnulled = annulled;
    }

    @Override
    public String toString() {
        return "Log{" +
                "id=" + id +
                ", kidId=" + kidId +
                ", monitorId=" + monitorId +
                ", numPoints=" + numPoints +
                ", time=" + time +
                '}';
    }
}
