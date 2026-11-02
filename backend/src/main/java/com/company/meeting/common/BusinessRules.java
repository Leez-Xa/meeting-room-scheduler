package com.company.meeting.common;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import com.company.meeting.config.RuleProperties;
import com.company.meeting.config.RuleSettings;
import com.company.meeting.config.SystemSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 预约时间与资源有效状态的公共规则。
 * 通过 Clock 读取北京时间，测试可固定时钟；提交在房间加锁后再读取锁定配置，
 * 协调管理员参数修改，避免以过期规则完成提交。
 */
@Component
public final class BusinessRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final java.util.function.Supplier<RuleSettings> settings;
    private final java.util.function.Supplier<RuleSettings> lockedSettings;
    private final Clock clock;
    /** 供无数据库的规则单测构造，直接从启动属性读取并验证快照。 */
    public BusinessRules(RuleProperties settings, Clock clock) {
        this.settings = () -> RuleSettings.from(settings);
        this.lockedSettings = this.settings;
        this.clock = clock;
    }
    /** 运行期使用参数服务；普通读与提交锁内读使用不同 Supplier。 */
    @Autowired
    public BusinessRules(SystemSettingsService settings, Clock clock) {
        this.settings = () -> settings.current().rules();
        this.lockedSettings = settings::lockForSubmission;
        this.clock = clock;
    }
    /**
     * 读取当前参数快照，可在一次计算中复用。
     */
    public RuleSettings settings() { return settings.get(); }
    /**
     * 注入时钟按北京时间求值，测试可固定。
     */
    public LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZONE)); }

    /**
     * 草稿和终态不占资源，仅三类有效状态占用。
     */
    public static boolean activeResourceStatus(String status) {
        return switch (status) {
            case "PENDING", "APPROVED", "IN_PROGRESS" -> true;
            default -> false;
        };
    }

    /**
     * 半开区间判断，首尾相接允许连续预约。
     */
    public static boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd, LocalDateTime rightStart, LocalDateTime rightEnd) {
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    /**
     * 验证先后、同天、工作日、开放时段、未来时间及开放周。
     * 带快照重载避免重复取规则；私有 enforceOpenWeek 控制周期后续是否限制周界。
     */
    public void validateBookingWindow(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, settings());
    }

    /**
     * 外层已锁房间，再取锁定参数校验普通窗口。
     */
    public void validateLockedBookingWindow(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, lockedSettings.get(), true);
    }

    /**
     * 周期首场仍遵守当前开放周，协调参数并发修改。
     */
    public void validateLockedRecurringFirstWindow(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, lockedSettings.get(), true);
    }

    /**
     * 周期后续只跳过周界限制，其他时间规则继续生效。
     */
    public void validateLockedRecurringOccurrence(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, lockedSettings.get(), false);
    }

    /**
     * 验证先后、同天、工作日、开放时段、未来时间及开放周。
     * 带快照重载避免重复取规则；私有 enforceOpenWeek 控制周期后续是否限制周界。
     */
    public void validateBookingWindow(LocalDateTime start, LocalDateTime end, RuleSettings settings) {
        validateBookingWindow(start, end, settings, true);
    }

    /**
     * 验证先后、同天、工作日、开放时段、未来时间及开放周。
     * 带快照重载避免重复取规则；私有 enforceOpenWeek 控制周期后续是否限制周界。
     */
    private void validateBookingWindow(LocalDateTime start, LocalDateTime end, RuleSettings settings, boolean enforceOpenWeek) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BizException("INVALID_TIME", "开始时间必须早于结束时间");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            throw new BizException("CROSS_DAY", "会议不能跨天");
        }
        if (!settings.workdays().contains(start.getDayOfWeek().getValue())) {
            throw new BizException("NON_WORKDAY", "所选日期不是开放预约的工作日");
        }
        if (start.toLocalTime().isBefore(settings.openTime()) || end.toLocalTime().isAfter(settings.closeTime())) {
            throw new BizException("OUTSIDE_OPEN_HOURS", "预约时间须在 " + settings.openTime() + "–" + settings.closeTime() + " 内");
        }
        LocalDateTime now = now();
        if (!start.isAfter(now)) {
            throw new BizException("PAST_TIME", "不能预约已经开始的时间");
        }
        if (!enforceOpenWeek) return;
        DayOfWeek day = now.getDayOfWeek();
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 周末开放下周；默认工作日限制会同时过滤本周周六、周日。
        LocalDate exclusiveEnd = monday.plusWeeks(day.getValue() >= 6 ? 2 : 1);
        if (start.toLocalDate().isBefore(monday) || !start.toLocalDate().isBefore(exclusiveEnd)) {
            throw new BizException("WEEK_WINDOW", day.getValue() >= 6 ? "周末仅开放本周剩余时段及下周预约" : "工作日只允许预约本周未来时段");
        }
    }
}
