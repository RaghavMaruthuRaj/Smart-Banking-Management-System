package com.smartbanking.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long jwtRefreshExpirationMs;

    @Value("${app.jwt.cookie-name}")
    private String jwtCookieName;

    @Value("${app.jwt.refresh-cookie-name}")
    private String jwtRefreshCookieName;

    private Key getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public ResponseCookie generateAccessCookie(String username, String role) {
        String token = generateToken(username, role, jwtExpirationMs);
        return ResponseCookie.from(jwtCookieName, token)
                .path("/api")
                .maxAge(jwtExpirationMs / 1000)
                .httpOnly(true)
                .secure(false) // Set to true in production with HTTPS
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie generateRefreshCookie(String username) {
        String token = generateToken(username, "REFRESH", jwtRefreshExpirationMs);
        return ResponseCookie.from(jwtRefreshCookieName, token)
                .path("/api/auth/refresh")
                .maxAge(jwtRefreshExpirationMs / 1000)
                .httpOnly(true)
                .secure(false) // Set to true in production
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie getCleanAccessCookie() {
        return ResponseCookie.from(jwtCookieName, "")
                .path("/api")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .build();
    }

    public ResponseCookie getCleanRefreshCookie() {
        return ResponseCookie.from(jwtRefreshCookieName, "")
                .path("/api/auth/refresh")
                .maxAge(0)
                .httpOnly(true)
                .secure(false)
                .sameSite("Strict")
                .build();
    }

    public String getJwtFromCookies(HttpServletRequest request) {
        return getCookieValue(request, jwtCookieName);
    }

    public String getRefreshJwtFromCookies(HttpServletRequest request) {
        return getCookieValue(request, jwtRefreshCookieName);
    }

    public String getUsernameFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public String getRoleFromToken(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("role", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.error("Invalid JWT token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("JWT token is expired: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("JWT token is unsupported: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    private String generateToken(String username, String role, long expirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .setSubject(username)
                .claim("role", role)
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }
}
