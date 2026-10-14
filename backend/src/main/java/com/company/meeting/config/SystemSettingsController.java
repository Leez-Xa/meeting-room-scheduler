package com.company.meeting.config;

import com.company.meeting.auth.UserPrincipal;
import com.company.meeting.common.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/settings")
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class SystemSettingsController {
    private final SystemSettingsService service;
    public SystemSettingsController(SystemSettingsService service) { this.service = service; }
    @GetMapping public ApiResponse<SystemSettingsService.SettingsView> get() { return ApiResponse.ok(service.current()); }
    @PutMapping public ApiResponse<SystemSettingsService.SettingsView> update(
            @RequestBody SystemSettingsService.SettingsUpdate request, @AuthenticationPrincipal UserPrincipal actor) {
        return ApiResponse.ok(service.update(request, actor));
    }
}
