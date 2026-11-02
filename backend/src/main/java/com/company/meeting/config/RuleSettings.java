package com.company.meeting.config;

import com.company.meeting.common.BizException;
import java.time.LocalTime;
import java.util.Set;

/**
 * A complete, immutable set of rules used for one business operation.
 *
 * 完整、不可变的业务规则快照，兼用于参数输入与持久化模型。
 * 构造时复制工作日集合，校验开放时段、权重和提醒范围，避免非法规则参与计算。
 */
public record RuleSettings(Set<Integer> workdays, LocalTime openTime, LocalTime closeTime,
                           Double capacityWeight, Double deviceWeight, Double floorWeight,
                           Integer floorPenalty, Integer draftRetentionDays, Integer reminderMinutes) {
    /** 紧凑构造器同时约束反序列化和手工构造，复制集合防止外部修改。 */
    public RuleSettings {
        if (workdays == null || workdays.isEmpty() || workdays.stream().anyMatch(d -> d == null || d < 1 || d > 7)) {
            throw new BizException("INVALID_CONFIG", "至少选择一个工作日，范围为周一至周日");
        }
        workdays = Set.copyOf(workdays);
        if (openTime == null || closeTime == null || !openTime.isBefore(closeTime)
                || openTime.getSecond() != 0 || closeTime.getSecond() != 0
                || openTime.getNano() != 0 || closeTime.getNano() != 0) {
            throw new BizException("INVALID_CONFIG", "开放时间须精确到分钟，且开始时间早于结束时间");
        }
        if (!weight(capacityWeight) || !weight(deviceWeight) || !weight(floorWeight)
                || Math.abs(capacityWeight + deviceWeight + floorWeight - 1) > 0.000001) {
            throw new BizException("INVALID_CONFIG", "推荐权重须在0到1之间，且合计为1");
        }
        if (floorPenalty == null || floorPenalty < 0 || floorPenalty > 100
                || draftRetentionDays == null || draftRetentionDays < 1 || draftRetentionDays > 365
                || reminderMinutes == null || reminderMinutes < 1 || reminderMinutes > 120) {
            throw new BizException("INVALID_CONFIG", "楼层扣分为0–100，草稿保留1–365天，提醒提前1–120分钟");
        }
    }

    /**
     * 拒绝空值、NaN、无穷及越界权重，防止无法比较的评分。
     */
    private static boolean weight(Double value) { return value != null && Double.isFinite(value) && value >= 0 && value <= 1; }

    /**
     * 把可变启动参数转成验证过的不可变快照。
     */
    public static RuleSettings from(RuleProperties properties) {
        return new RuleSettings(properties.getWorkdays(), properties.getOpenTime(), properties.getCloseTime(),
                properties.getCapacityWeight(), properties.getDeviceWeight(), properties.getFloorWeight(),
                properties.getFloorPenalty(), properties.getDraftRetentionDays(), properties.getReminderMinutes());
    }
}
