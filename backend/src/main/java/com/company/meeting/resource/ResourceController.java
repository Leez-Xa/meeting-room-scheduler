package com.company.meeting.resource;

import com.company.meeting.common.ApiResponse;
import com.company.meeting.meeting.MeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    private final MeetingService meetingService;

    public ResourceController(MeetingService meetingService) { this.meetingService = meetingService; }

    @GetMapping("/rooms")
    public ApiResponse<?> rooms() { return ApiResponse.ok(meetingService.rooms()); }

    @GetMapping("/users")
    public ApiResponse<?> users() { return ApiResponse.ok(meetingService.users()); }
    @GetMapping("/rules")
    public ApiResponse<?> rules() { return ApiResponse.ok(meetingService.bookingRules()); }
}
