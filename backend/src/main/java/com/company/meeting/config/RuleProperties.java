package com.company.meeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import java.time.LocalTime;
import java.util.Set;

/**
 * meeting.rules 启动默认值，数据库未保存覆盖参数时使用。
 * 运行期规则由参数服务管理，启动时构造 RuleSettings 做完整校验。
 */
@Component
@ConfigurationProperties(prefix = "meeting.rules")
public class RuleProperties {
    /** ISO 星期 1=周一、7=周日，不接入法定节假日。 */
    private Set<Integer> workdays = Set.of(1, 2, 3, 4, 5);
    /** 每日最早开始，精确到分钟。 */
    private LocalTime openTime = LocalTime.of(9, 0);
    /** 每日最晚结束，结束等于此值允许。 */
    private LocalTime closeTime = LocalTime.of(18, 0);
    /** 三项权重均在 [0,1] 且和为 1。 */
    private double capacityWeight = 0.5;
    private double deviceWeight = 0.3;
    private double floorWeight = 0.2;
    /** 每相差一层扣分，楼层分最低为 0。 */
    private int floorPenalty = 20;
    /** 按最后更新时间计算的草稿保留天数。 */
    private int draftRetentionDays = 7;
    /** 会前窗口分钟数，重复扫描由消息键去重。 */
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
    /**
     * 绑定后立即校验整套规则，错误配置阻止启动。
     */
    @jakarta.annotation.PostConstruct
    public void validate() {
        RuleSettings.from(this);
    }
}
