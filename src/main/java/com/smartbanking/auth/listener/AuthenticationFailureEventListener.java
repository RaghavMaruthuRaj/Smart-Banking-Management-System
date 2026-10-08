package com.smartbanking.auth.listener;

import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import com.smartbanking.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFailureEventListener implements ApplicationListener<AbstractAuthenticationFailureEvent> {

    private final UserService userService;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Override
    public void onApplicationEvent(AbstractAuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        log.warn("Login failure for user: {}. Reason: {}", username, event.getException().getMessage());
        
        userService.registerFailedAttempt(username);
        
        userRepository.findByUsername(username).ifPresentOrElse(
            user -> {
                if (user.getLockTime() != null && java.time.LocalDateTime.now().isBefore(user.getLockTime())) {
                    auditLogService.log(user.getId(), "USER_LOCKED", "User account locked due to excessive failed logins");
                } else {
                    auditLogService.log(user.getId(), "LOGIN_FAILURE", "Failed login attempt: " + event.getException().getMessage());
                }
            },
            () -> {
                // User doesn't exist, log audit with null user ID
                auditLogService.log(null, "LOGIN_FAILURE_UNREGISTERED", "Failed login attempt for non-existent username: " + username);
            }
        );
    }
}
