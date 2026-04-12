package com.campus.common.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class JwtUtil {

    // 生产环境需通过配置注入，此处仅作为脚手架默认值
    private static final String SECRET = "campus-platform-secret-key-12345678901234567890";
    private static final long ACCESS_EXPIRE = 2 * 60 * 60 * 1000; // 2h
    private static final long REFRESH_EXPIRE = 7 * 24 * 60 * 60 * 1000; // 7d

    private static final SecretKey KEY = Keys.hmacShaKeyFor(Decoders.BASE64.decode(
            io.jsonwebtoken.io.Encoders.BASE64.encode(SECRET.getBytes())));

    public static String generateAccessToken(Long userId, String username, Integer role) {
        return generateToken(userId, username, role, ACCESS_EXPIRE);
    }

    public static String generateRefreshToken(Long userId, String username, Integer role) {
        return generateToken(userId, username, role, REFRESH_EXPIRE);
    }

    private static String generateToken(Long userId, String username, Integer role, long expire) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expire);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .claim("jti", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(expiration)
                .signWith(KEY, Jwts.SIG.HS256)
                .compact();
    }

    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期");
            throw e;
        } catch (JwtException e) {
            log.warn("Token解析失败: {}", e.getMessage());
            throw e;
        }
    }

    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return Long.valueOf(claims.getSubject());
    }

    public static String getJti(String token) {
        Claims claims = parseToken(token);
        return claims.get("jti", String.class);
    }
}
