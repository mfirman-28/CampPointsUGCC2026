package com.campamento.dto;

public class GroupRankingDTO {
    private String groupName;
    private double averagePoints;

    public GroupRankingDTO() {
    }

    public GroupRankingDTO(String groupName, double averagePoints) {
        this.groupName = groupName;
        this.averagePoints = averagePoints;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public double getAveragePoints() {
        return averagePoints;
    }

    public void setAveragePoints(double averagePoints) {
        this.averagePoints = averagePoints;
    }
}
