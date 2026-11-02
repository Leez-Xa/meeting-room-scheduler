package com.company.meeting.auth;

import com.company.meeting.resource.UserRepository;
import com.company.meeting.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;

/**
 * 已登录会话的账号有效性检查。
 * 每次请求重新读取账号，账号或部门失效、密码变化时撤销旧会话；
 * 角色、部门和楼层变化则刷新当前请求的身份，避免沿用登录时的旧权限。
 */
public class AccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper mapper;
    public AccountSessionFilter(UserRepository users, ObjectMapper mapper) { this.users=users; this.mapper=mapper; }
    /**
     * 复查身份、密码变化并刷新角色部门；撤销时返回 401 且终止过滤链。
     */
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal previous) {
            var current = users.findPrincipal(previous.username()).orElse(null);
            // 比较密码编码是否变化，不是重新接收浏览器明文密码。
            if (current == null || !current.password().equals(previous.password())) {
                SecurityContextHolder.clearContext();
                var session = request.getSession(false); if (session != null) session.invalidate();
                response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(response.getWriter(), new ApiResponse<>(false, Map.of("code", "SESSION_REVOKED"), "账号已停用或密码已修改，请重新登录"));
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities()));
        }
        chain.doFilter(request, response);
    }
}
