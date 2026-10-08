package com.smartbanking.auth.controller;

import com.smartbanking.auth.dto.AuthResult;
import com.smartbanking.auth.dto.LoginRequest;
import com.smartbanking.auth.dto.RegisterRequest;
import com.smartbanking.auth.dto.UserResponse;
import com.smartbanking.auth.security.JwtTokenProvider;
import com.smartbanking.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResult result = authService.login(request);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.getRefreshTokenCookie().toString())
                .body(result.getUser());
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(HttpServletRequest request) {
        String refreshToken = jwtTokenProvider.getRefreshJwtFromCookies(request);
        ResponseCookie newAccessCookie = authService.refresh(refreshToken);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, newAccessCookie.toString())
                .build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        AuthResult result = authService.logout();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, result.getAccessTokenCookie().toString())
                .header(HttpHeaders.SET_COOKIE, result.getRefreshTokenCookie().toString())
                .build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserResponse response = authService.getMe(authentication.getName());
        return ResponseEntity.ok(response);
    }
}
