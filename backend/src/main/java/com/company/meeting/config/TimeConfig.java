package com.company.meeting.config;
import com.company.meeting.common.BusinessRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;

/**
 * 提供统一北京时间 Clock，业务不依赖机器默认时区。
 * 测试可注入固定时钟，避免修改机器时间或等待真实时间。
 */
@Configuration
public class TimeConfig {
    /**
     * 创建北京时间业务时钟，测试可用 Primary 固定时钟替换。
     */
    @Bean public Clock clock() { return Clock.system(BusinessRules.ZONE); }
}
