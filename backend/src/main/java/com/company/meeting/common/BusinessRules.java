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

@Component
public final class BusinessRules {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final java.util.function.Supplier<RuleSettings> settings;
    private final java.util.function.Supplier<RuleSettings> lockedSettings;
    private final Clock clock;
    public BusinessRules(RuleProperties settings, Clock clock) {
        this.settings = () -> RuleSettings.from(settings);
        this.lockedSettings = this.settings;
        this.clock = clock;
    }
    @Autowired
    public BusinessRules(SystemSettingsService settings, Clock clock) {
        this.settings = () -> settings.current().rules();
        this.lockedSettings = settings::lockForSubmission;
        this.clock = clock;
    }
    public RuleSettings settings() { return settings.get(); }
    public LocalDateTime now() { return LocalDateTime.now(clock.withZone(ZONE)); }

    public static boolean activeResourceStatus(String status) {
        return switch (status) {
            case "PENDING", "APPROVED", "IN_PROGRESS" -> true;
            default -> false;
        };
    }

    public static boolean overlaps(LocalDateTime leftStart, LocalDateTime leftEnd, LocalDateTime rightStart, LocalDateTime rightEnd) {
        return leftStart.isBefore(rightEnd) && rightStart.isBefore(leftEnd);
    }

    public void validateBookingWindow(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, settings());
    }

    public void validateLockedBookingWindow(LocalDateTime start, LocalDateTime end) {
        validateBookingWindow(start, end, lockedSettings.get());
    }

    public void validateBookingWindow(LocalDateTime start, LocalDateTime end, RuleSettings settings) {
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
        DayOfWeek day = now.getDayOfWeek();
        LocalDate monday = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // 周末开放下周；默认工作日限制会同时过滤本周周六、周日。
        LocalDate exclusiveEnd = monday.plusWeeks(day.getValue() >= 6 ? 2 : 1);
        if (start.toLocalDate().isBefore(monday) || !start.toLocalDate().isBefore(exclusiveEnd)) {
            throw new BizException("WEEK_WINDOW", day.getValue() >= 6 ? "周末仅开放本周剩余时段及下周预约" : "工作日只允许预约本周未来时段");
        }
    }
}
