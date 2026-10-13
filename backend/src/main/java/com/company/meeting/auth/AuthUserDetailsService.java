package com.company.meeting.auth;

import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.company.meeting.resource.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    public AuthUserDetailsService(UserRepository userRepository) { this.userRepository = userRepository; }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findPrincipal(username).orElseThrow(() -> new UsernameNotFoundException("账号或密码错误"));
    }
}
