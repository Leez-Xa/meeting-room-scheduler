package com.company.meeting.notice;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 当前用户站内消息接口，提供列表、未读计数和已读操作。
 * 接收人来自认证上下文，客户端不能声明其他用户 ID。
 */
@RestController
@RequestMapping("/api/v1/notices")
public class NoticeController {
    private final NoticeService service;

    public NoticeController(NoticeService service) { this.service = service; }

    /**
     * 本人消息分页，可只未读。
     */
    @GetMapping
    public ApiResponse<NoticeDtos.NoticePage> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                                    @RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                    @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.list(user, unreadOnly, page, pageSize));
    }

    /**
     * 本人未读数。
     */
    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(service.unreadCount(user));
    }

    @PostMapping("/{id}/read")
    /**
     * 标记本人消息，ID 不可越权。
     */
    public ApiResponse<Void> read(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal user) {
        service.markRead(id, user);
        return ApiResponse.ok(null, "消息已读");
    }

    /**
     * 只标记当前用户全部消息。
     */
    @PostMapping("/read-all")
    public ApiResponse<Void> readAll(@AuthenticationPrincipal UserPrincipal user) {
        service.markAllRead(user);
        return ApiResponse.ok(null, "消息已全部读完");
    }
}