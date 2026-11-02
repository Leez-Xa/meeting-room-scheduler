package com.company.meeting.auth;

import com.company.meeting.common.ApiResponse;
import com.company.meeting.meeting.MeetingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

/**
 * 会话认证接口：CSRF 令牌、登录、退出和当前用户查询。
 * 通过 AuthenticationManager 校验凭证，将 SecurityContext 保存到 HttpSession。
 * 使用 Cookie 会话，不签发 JWT，也不提供公开注册。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final MeetingService meetingService;

    public AuthController(AuthenticationManager authenticationManager, MeetingService meetingService) {
        this.authenticationManager = authenticationManager; this.meetingService = meetingService;
    }

    /**
     * 获取当前会话的 CSRF 令牌，写请求由 X-CSRF-TOKEN 携带。
     */
    @GetMapping("/csrf")
    public ApiResponse<Map<String, String>> csrf(CsrfToken token) { return ApiResponse.ok(Map.of("token", token.getToken())); }

    /**
     * 验证凭证后创建安全上下文，更换 Session ID 并显式存入会话。
     * 手工 JSON 登录不依赖表单过滤器保存上下文，返回只包含公开摘要。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        HttpSession session = httpRequest.getSession(true);
        // 登录成功轮换会话标识，降低会话固定风险。
        httpRequest.changeSessionId();
        // JSON 登录显式保存上下文，后续请求才能恢复认证。
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();
        return ApiResponse.ok(meetingService.currentUser(user), "登录成功");
    }

    /**
     * 清理认证上下文并失效已有会话，不额外创建会话。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        return ApiResponse.ok(null, "已退出");
    }

    /**
     * 返回认证身份摘要，排除密码编码。
     */
    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me(@org.springframework.security.core.annotation.AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(meetingService.currentUser(user));
    }

    /**
     * 认证凭证，密码不可写日志、审计或响应。
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
}
