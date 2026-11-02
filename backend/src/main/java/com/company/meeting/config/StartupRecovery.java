package com.company.meeting.config;
import com.company.meeting.meeting.MeetingService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 应用就绪时补齐停机期间遗漏的预约状态。
 * 通过注入的 MeetingService 代理调用，复用定时任务事务；不重放所有历史任务。
 */
@Component
public class StartupRecovery {
    private final MeetingService service;
    public StartupRecovery(MeetingService service) { this.service=service; }
    /**
     * 就绪事件触发一次补齐，通过外部服务代理进入事务。
     */
    @EventListener(ApplicationReadyEvent.class) public void recover() { service.advanceStates(); }
}
