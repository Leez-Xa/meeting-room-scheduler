package com.company.meeting.auth;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.company.meeting.resource.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

/**
 * 将安全框架的用户名查询委托给用户仓储。
 * 账号和部门的启用条件由仓储 SQL 约束，密码比对交由认证提供者和 BCrypt 完成。
 */
@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public AuthUserDetailsService(UserRepository userRepository) { this.userRepository = userRepository; }

    /**
     * 加载合法身份，缺失时统一提示账号或密码错误。
     */
    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findPrincipal(username).orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
}
