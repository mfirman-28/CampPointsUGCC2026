package com.campamento.model;

public class Monitor {
    private Long id;
    private String name;
    private Long telegramId;
    private Long groupId;
    private boolean isAdmin;
    private boolean isSoloMonitor;

    public Monitor() {
    }

    public Monitor(Long id, String name, Long telegramId, Long groupId, boolean isAdmin, boolean isSoloMonitor) {
        this.id = id;
        this.name = name;
        this.telegramId = telegramId;
        this.groupId = groupId;
        this.isAdmin = isAdmin;
        this.isSoloMonitor = isSoloMonitor;
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

    public Long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(Long telegramId) {
        this.telegramId = telegramId;
    }

    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public boolean isSoloMonitor() {
        return isSoloMonitor;
    }

    public void setSoloMonitor(boolean soloMonitor) {
        isSoloMonitor = soloMonitor;
    }

    @Override
    public String toString() {
        return "Monitor{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", telegramId=" + telegramId +
                ", groupId=" + groupId +
                ", isAdmin=" + isAdmin +
                ", isSoloMonitor=" + isSoloMonitor +
                '}';
    }
}
