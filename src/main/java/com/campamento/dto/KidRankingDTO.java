package com.campamento.dto;

public class KidRankingDTO {
    private String kidName;
    private String groupName;
    private int points;

    public KidRankingDTO() {
    }

    public KidRankingDTO(String kidName, String groupName, int points) {
        this.kidName = kidName;
        this.groupName = groupName;
        this.points = points;
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

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }
}
