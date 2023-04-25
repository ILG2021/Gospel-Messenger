package org.telegram.extension;

import org.telegram.messenger.LocaleController;

import java.util.ArrayList;
import java.util.List;

public class TimeRecord {
    private transient long userId;
    private transient String name;
    private List<Long> onlines = new ArrayList<>();
    private List<Long> offlines = new ArrayList<>();

    public long getUserId() {
        return userId;
    }

    public void setUserId(long userId) {
        this.userId = userId;
    }

    public String getDuration() {
        List<Long> tempOfflines = new ArrayList<>(offlines);
        if (tempOfflines.size() < onlines.size())
            tempOfflines.add(System.currentTimeMillis());
        if (tempOfflines.size() != onlines.size())
            return "";
        int minitus = 0;
        for (int i = 0; i < onlines.size(); i++) {
            minitus += (tempOfflines.get(i) - onlines.get(i)) / (1000 * 60);
        }

        return LocaleController.formatPluralString("Minutes", minitus);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setOnlines(List<Long> onlines) {
        this.onlines = onlines;
    }

    public void setOfflines(List<Long> offlines) {
        this.offlines = offlines;
    }

    public List<Long> getOnlines() {
        return onlines;
    }

    public List<Long> getOfflines() {
        return offlines;
    }
}