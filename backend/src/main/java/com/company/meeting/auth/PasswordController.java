package com.company.meeting.auth;
import com.company.meeting.common.ApiResponse;
import com.company.meeting.resource.AdminDtos;
import com.company.meeting.resource.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/auth")
public class PasswordController {
    private final AdminService service;
    public PasswordController(AdminService service) { this.service=service; }
    @PostMapping("/password") public ApiResponse<?> change(@Valid @RequestBody AdminDtos.PasswordRequest req,@AuthenticationPrincipal UserPrincipal actor) { service.changePassword(req,actor); return ApiResponse.ok(null,"密码已修改，请重新登录"); }
}
