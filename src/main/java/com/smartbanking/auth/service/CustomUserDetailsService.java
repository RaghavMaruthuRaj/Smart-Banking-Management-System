package com.smartbanking.auth.service;

import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import java.util.Collections;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));

        // Format role as ROLE_CUSTOMER or ROLE_ADMIN for standard Spring Security authority mapping
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + user.getRole().name());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(Collections.singletonList(authority))
                .disabled(false)
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(isAccountLocked(user))
                .build();
    }

    private boolean isAccountLocked(User user) {
        if (user.getLockTime() == null) {
            return false;
        }
        // Account is locked if the current time is before the lockout expiry time
        return java.time.LocalDateTime.now().isBefore(user.getLockTime());
    }
}
