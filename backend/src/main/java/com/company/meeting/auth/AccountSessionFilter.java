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

public class AccountSessionFilter extends OncePerRequestFilter {
    private final UserRepository users;
    private final ObjectMapper mapper;
    public AccountSessionFilter(UserRepository users, ObjectMapper mapper) { this.users=users; this.mapper=mapper; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal previous) {
            var current = users.findPrincipal(previous.username()).orElse(null);
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
