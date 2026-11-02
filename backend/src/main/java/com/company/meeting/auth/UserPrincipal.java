package com.company.meeting.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 服务端认证身份，包含用户、部门、角色及办公楼层。
 * password 是 BCrypt 编码，不能将整个对象作为用户信息响应返回前端。
 * 启用状态由身份查询过滤，UserDetails 的布尔方法不另行查库。
 */
public record UserPrincipal(Long id, String username, String password, String realName, Long deptId,
                            String deptName, String role, Integer floorNo) implements UserDetails {
    /**
     * 适配认证用户名，供登录和会话刷新查询。
     */
    @Override public String getUsername() { return username; }
    /**
     * 提供 BCrypt 编码给认证匹配，不是明文密码。
     */
    @Override public String getPassword() { return password; }
    /**
     * 业务角色加 ROLE_ 前缀，匹配 hasRole 约定。
     */
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    /**
     * 无独立到期字段，有效性在身份查询中过滤。
     */
    @Override public boolean isAccountNonExpired() { return true; }
    /**
     * 无单独锁定字段，启用状态在身份查询处理。
     */
    @Override public boolean isAccountNonLocked() { return true; }
    /**
     * 无密码到期策略，密码变化由过滤器复查。
     */
    @Override public boolean isCredentialsNonExpired() { return true; }
    /**
     * 只对已满足身份查询条件的实例返回 true，过滤器持续复查。
     */
    @Override public boolean isEnabled() { return true; }
}
