package com.campamento.model;

public class Config {
    private int id;
    private int dailyLimit;
    private boolean globalPointsEnable;

    public Config() {
    }

    public Config(int id, int dailyLimit, boolean globalPointsEnable) {
        this.id = id;
        this.dailyLimit = dailyLimit;
        this.globalPointsEnable = globalPointsEnable;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(int dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isGlobalPointsEnable() {
        return globalPointsEnable;
    }

    public void setGlobalPointsEnable(boolean globalPointsEnable) {
        this.globalPointsEnable = globalPointsEnable;
    }

    @Override
    public String toString() {
        return "Config{" +
                "id=" + id +
                ", dailyLimit=" + dailyLimit +
                ", globalPointsEnable=" + globalPointsEnable +
                '}';
    }
}
