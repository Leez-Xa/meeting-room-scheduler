package com.company.meeting.config;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 仅系统管理员使用的参数接口，返回规则和 revision。
 * 保存必须携带读取时的版本号，避免旧页面覆盖他人更新。
 */
@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemSettingsController {
    private final SystemSettingsService service;
    public SystemSettingsController(SystemSettingsService service) { this.service = service; }
    /**
     * 读取规则、版本与数据库升级标记。
     */
    @GetMapping public ApiResponse<SystemSettingsService.SettingsView> get() { return ApiResponse.ok(service.current()); }
    /**
     * 提交完整规则和旧 revision，检查并发和预约兼容性。
     */
    @PutMapping public ApiResponse<SystemSettingsService.SettingsView> update(
            @RequestBody SystemSettingsService.SettingsUpdate request, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(service.update(request, actor));
    }
}
