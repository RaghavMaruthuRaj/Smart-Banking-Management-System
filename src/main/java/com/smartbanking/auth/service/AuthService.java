package com.smartbanking.auth.service;

import com.smartbanking.account.model.Account;
import com.smartbanking.account.model.AccountStatus;
import com.smartbanking.account.model.AccountTier;
import com.smartbanking.account.repository.AccountRepository;
import com.smartbanking.audit.service.AuditLogService;
import com.smartbanking.auth.dto.AuthResult;
import com.smartbanking.auth.dto.LoginRequest;
import com.smartbanking.auth.dto.RegisterRequest;
import com.smartbanking.auth.dto.UserResponse;
import com.smartbanking.auth.security.JwtTokenProvider;
import com.smartbanking.common.exception.AccountBlockedException;
import com.smartbanking.common.exception.BankException;
import com.smartbanking.common.exception.ResourceNotFoundException;
import com.smartbanking.user.model.User;
import com.smartbanking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditLogService auditLogService;
    
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BankException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BankException("Email '" + request.getEmail() + "' is already registered");
        }

        // 1. Create User
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole())
                .build();

        User savedUser = userRepository.save(user);

        // 2. Automatically provision an Account with $1,000 demo funds
        String accountNumber = generateAccountNumber();
        Account account = Account.builder()
                .accountNumber(accountNumber)
                .user(savedUser)
                .balance(new BigDecimal("1000.00")) // Give starting deposit
                .currency("USD")
                .tier(AccountTier.SILVER)
                .status(AccountStatus.ACTIVE)
                .build();

        accountRepository.save(account);

        // 3. Log actions
        auditLogService.log(savedUser.getId(), "USER_REGISTERED", "User registered: " + savedUser.getUsername());
        auditLogService.log(savedUser.getId(), "ACCOUNT_PROVISIONED", "Default account " + accountNumber + " created with $1000 starting balance");

        return mapToUserResponse(savedUser);
    }

    @Transactional
    public AuthResult login(LoginRequest request) {
        // Authenticate credentials. Failure listener handles Lockouts automatically.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getLockTime() != null && LocalDateTime.now().isBefore(user.getLockTime())) {
            throw new AccountBlockedException("Account is locked due to multiple failed login attempts. Try again later.");
        }

        // Generate Access and Refresh JWT Cookies
        ResponseCookie accessCookie = jwtTokenProvider.generateAccessCookie(user.getUsername(), user.getRole().name());
        ResponseCookie refreshCookie = jwtTokenProvider.generateRefreshCookie(user.getUsername());

        return AuthResult.builder()
                .user(mapToUserResponse(user))
                .accessTokenCookie(accessCookie)
                .refreshTokenCookie(refreshCookie)
                .build();
    }

    public ResponseCookie refresh(String refreshToken) {
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            throw new BankException("Invalid or expired refresh token");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.getLockTime() != null && LocalDateTime.now().isBefore(user.getLockTime())) {
            throw new AccountBlockedException("User is locked out");
        }

        return jwtTokenProvider.generateAccessCookie(user.getUsername(), user.getRole().name());
    }

    public AuthResult logout() {
        return AuthResult.builder()
                .accessTokenCookie(jwtTokenProvider.getCleanAccessCookie())
                .refreshTokenCookie(jwtTokenProvider.getCleanRefreshCookie())
                .build();
    }

    public UserResponse getMe(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    private String generateAccountNumber() {
        // Generate an 11-digit random account number e.g. "SB-582910482"
        long number = 100000000L + secureRandom.nextInt(900000000);
        return "SB-" + number;
    }

    private UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
