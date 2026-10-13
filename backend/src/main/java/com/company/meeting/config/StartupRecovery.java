package com.company.meeting.config;
import com.company.meeting.meeting.MeetingService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class StartupRecovery {
    private final MeetingService service;
    public StartupRecovery(MeetingService service) { this.service=service; }
    @EventListener(ApplicationReadyEvent.class) public void recover() { service.advanceStates(); }
}
