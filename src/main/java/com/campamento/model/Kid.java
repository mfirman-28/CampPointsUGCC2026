package com.campamento.model;

public class Kid {
    private Long id;
    private String name;
    private int points;
    private Long groupId;

    public Kid() {
    }

    public Kid(Long id, String name, int points, Long groupId) {
        this.id = id;
        this.name = name;
        this.points = points;
        this.groupId = groupId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    @Override
    public String toString() {
        return "Kid{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", points=" + points +
                ", groupId=" + groupId +
                '}';
    }
}
