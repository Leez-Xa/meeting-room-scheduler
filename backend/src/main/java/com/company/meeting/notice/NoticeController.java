package com.company.meeting.notice;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {
    private final NoticeService service;

    public NoticeController(NoticeService service) { this.service = service; }

    @GetMapping
    public ApiResponse<NoticeDtos.NoticePage> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.list(user, unreadOnly, page, pageSize));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) {
        service.markRead(id, user);
        return ApiResponse.ok(null, "消息已读");
    }

    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal UserPrincipal user) {
        service.markAllRead(user);
        return ApiResponse.ok(null, "消息已全部读完");
    }
}