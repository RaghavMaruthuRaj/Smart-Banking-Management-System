package com.smartbanking.auth.dto;

import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseCookie;

@Data
@Builder
public class AuthResult {
    private UserResponse user;
    private ResponseCookie accessTokenCookie;
    private ResponseCookie refreshTokenCookie;
}
