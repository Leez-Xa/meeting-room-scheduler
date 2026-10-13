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
    @jakarta.annotation.PostConstruct
    public void validate() {
        if (workdays.isEmpty() || workdays.stream().anyMatch(day->day<1 || day>7) || !openTime.isBefore(closeTime)) throw new IllegalArgumentException("预约工作日及开放时间配置不合法");
        if (capacityWeight<0 || deviceWeight<0 || floorWeight<0 || !Double.isFinite(capacityWeight+deviceWeight+floorWeight) || Math.abs(capacityWeight+deviceWeight+floorWeight-1)>0.0001 || floorPenalty<0 || draftRetentionDays<1) throw new IllegalArgumentException("推荐权重须非负且合计为1，草稿保留天数须大于0");
    }
}
