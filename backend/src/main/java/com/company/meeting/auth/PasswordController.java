package com.company.meeting.auth;
import com.company.meeting.common.ApiResponse;
import com.company.meeting.resource.AdminDtos;
import com.company.meeting.resource.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
/**
 * 当前登录用户修改密码的入口，服务层验证旧密码与新密码约束。
 * 密码变化后，账号过滤器会在后续请求撤销旧会话。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordController {
    private final AdminService service;
    public PasswordController(AdminService service) { this.service=service; }
    /**
     * 修改本人密码，旧密码比对交 AdminService。
     */
    @PostMapping("/password") public ApiResponse<?> change(@Valid @RequestBody AdminDtos.PasswordRequest req,@AuthenticationPrincipal UserPrincipal actor) { service.changePassword(req,actor); return ApiResponse.ok(null,"密码已修改，请重新登录"); }
}
