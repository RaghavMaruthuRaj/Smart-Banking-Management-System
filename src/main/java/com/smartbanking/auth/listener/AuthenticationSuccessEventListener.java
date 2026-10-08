package com.smartbanking.auth.listener;

import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import com.smartbanking.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationSuccessEventListener implements ApplicationListener<AuthenticationSuccessEvent> {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    public void onApplicationEvent(AuthenticationSuccessEvent event) {
        Object principal = event.getAuthentication().getPrincipal();
        if (principal instanceof UserDetails userDetails) {
            String username = userDetails.getUsername();
            userService.resetFailedAttempts(username);
            
            userRepository.findByUsername(username).ifPresent(user -> {
                auditLogService.log(user.getId(), "LOGIN_SUCCESS", "User logged in successfully");
            });
        }
    }
}
