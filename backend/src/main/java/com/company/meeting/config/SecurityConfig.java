package com.company.meeting.config;

import com.company.meeting.auth.AuthUserDetailsService;
import com.company.meeting.auth.AccountSessionFilter;
import com.company.meeting.resource.UserRepository;
import com.company.meeting.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

/**
 * 安全入口：BCrypt、Session、CSRF、路径角色与跨域设置。
 * 账号过滤器复查旧会话，服务层继续校验部门及预约范围。
 * 前后端分离不等于关闭 CSRF，携带 Cookie 的写请求仍需令牌。
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    /**
     * 提供 BCrypt 编码与匹配，禁止存储明文。
     */
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    /**
     * 连接合法用户查询及密码匹配。
     */
    @Bean
    DaoAuthenticationProvider authenticationProvider(AuthUserDetailsService service, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(service);
        provider.setPasswordEncoder(encoder);
        return provider;
    }

    /**
     * 提供给 JSON 登录入口主动发起认证。
     */
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * 配置 Session、CSRF、路径权限与安全异常响应。
     * 账号复查放在上下文恢复之后；登录和 CSRF 允许匿名，管理路径限制角色，其余要求登录。
     */
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, UserRepository users, ObjectMapper mapper) throws Exception {
        var csrfRepository = new HttpSessionCsrfTokenRepository();
        var csrfHandler = new CsrfTokenRequestAttributeHandler();
        http
            .addFilterAfter(new AccountSessionFilter(users, mapper), SecurityContextHolderFilter.class)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/csrf").permitAll()
                .requestMatchers("/api/v1/admin/**", "/api/v1/audit").hasRole("SYSTEM_ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/v1/resources/**").authenticated()
                .anyRequest().authenticated())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, error) -> {
                    response.setStatus(401); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), new ApiResponse<>(false, Map.of("code", "UNAUTHORIZED"), "请先登录"));
                })
                .accessDeniedHandler((request, response, error) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    mapper.writeValue(response.getWriter(), new ApiResponse<>(false, Map.of("code", "FORBIDDEN"), "权限不足或 CSRF 校验失败"));
                }))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }

    /**
     * 仅放行本机开发前端的两个来源携带 Cookie，不能随意改为来源通配。
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://127.0.0.1:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
