package com.company.meeting.resource;

import com.company.meeting.common.ApiResponse;
import com.company.meeting.meeting.MeetingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约表单的只读资源接口，提供房间、无密码员工摘要及当前规则。
 * 要求登录，与系统管理员资源维护入口分离。
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {
    private final MeetingService meetingService;

    public ResourceController(MeetingService meetingService) { this.meetingService = meetingService; }

    /**
     * 只读房间设备，真正可约需推荐和提交再查。
     */
    @GetMapping("/rooms")
    public ApiResponse<?> rooms() { return ApiResponse.ok(meetingService.rooms()); }

    /**
     * 内部参会选择的无密码摘要。
     */
    @GetMapping("/users")
    public ApiResponse<?> users() { return ApiResponse.ok(meetingService.users()); }
    /**
     * 时间规则与服务端时钟。
     */
    @GetMapping("/rules")
    public ApiResponse<?> rules() { return ApiResponse.ok(meetingService.bookingRules()); }
}
