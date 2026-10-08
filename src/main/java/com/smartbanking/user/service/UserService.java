package com.smartbanking.user.service;

import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.security.max-failed-attempts}")
    private int maxFailedAttempts;

    @Value("${app.security.lockout-duration-ms}")
    private long lockoutDurationMs;

    @Transactional
    public void registerFailedAttempt(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            int newAttempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(newAttempts);
            
            if (newAttempts >= maxFailedAttempts) {
                lock(user);
            } else {
                userRepository.save(user);
            }
        }
    }

    @Transactional
    public void resetFailedAttempts(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            if (user.getFailedAttempts() > 0) {
                user.setFailedAttempts(0);
                user.setLockTime(null);
                userRepository.save(user);
                log.info("Reset failed login attempts for user: {}", username);
            }
        });
    }

    private void lock(User user) {
        user.setLockTime(LocalDateTime.now().plusNanos(lockoutDurationMs * 1000000));
        userRepository.save(user);
        log.warn("User {} has been locked out due to multiple failed login attempts. Lock expires at: {}", 
                user.getUsername(), user.getLockTime());
    }

    @Transactional
    public boolean unlockWhenTimeExpired(User user) {
        if (user.getLockTime() != null && LocalDateTime.now().isAfter(user.getLockTime())) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            userRepository.save(user);
            log.info("User {} account unlocked automatically as lockout expired.", user.getUsername());
            return true;
        }
        return false;
    }
}
