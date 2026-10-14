package com.company.meeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "meeting.rules")
public class RuleProperties {
    private Set<Integer> workdays = Set.of(1, 2, 3, 4, 5);
    private LocalTime openTime = LocalTime.of(9, 0);
    private LocalTime closeTime = LocalTime.of(18, 0);
    private double capacityWeight = 0.5;
    private double deviceWeight = 0.3;
    private double floorWeight = 0.2;
    private int floorPenalty = 20;
    private int draftRetentionDays = 7;
    private int reminderMinutes = 15;
    public Set<Integer> getWorkdays() { return workdays; }
    public void setWorkdays(Set<Integer> value) { workdays = value; }
    public LocalTime getOpenTime() { return openTime; }
    public void setOpenTime(LocalTime value) { openTime = value; }
    public LocalTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalTime value) { closeTime = value; }
    public double getCapacityWeight() { return capacityWeight; }
    public void setCapacityWeight(double value) { capacityWeight = value; }
    public double getDeviceWeight() { return deviceWeight; }
    public void setDeviceWeight(double value) { deviceWeight = value; }
    public double getFloorWeight() { return floorWeight; }
    public void setFloorWeight(double value) { floorWeight = value; }
    public int getFloorPenalty() { return floorPenalty; }
    public void setFloorPenalty(int value) { floorPenalty = value; }
    public int getDraftRetentionDays() { return draftRetentionDays; }
    public void setDraftRetentionDays(int value) { draftRetentionDays = value; }
    public int getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(int value) { reminderMinutes = value; }
    @jakarta.annotation.PostConstruct
    public void validate() {
        RuleSettings.from(this);
    }
}
