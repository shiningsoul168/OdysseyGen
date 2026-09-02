package com.odysseygen.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 启动期校验：HS256 要求密钥 ≥ 32 字节。
     * 用默认占位符（如 your_jwt_secret_here，仅 20 字节）启动时，
     * 若不校验会"应用能起、但登录抛 WeakKeyException、全站 401"，故障极难定位——这里直接 fail-fast。
     */
    @PostConstruct
    public void validateConfig() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "jwt.secret 长度不足 32 字节（HS256 最低要求）。请通过环境变量 JWT_SECRET 配置强随机密钥。");
        }
        if (expiration == null || expiration <= 0) {
            throw new IllegalStateException("jwt.expiration 必须为正数（毫秒）");
        }
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 Token
     */
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * 从 Token 中提取所有 Claims
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 提取指定字段
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    /**
     * 获取用户ID
     */
    public Long getUserId(String token) {
        return extractClaim(token, claims -> claims.get("userId", Long.class));
    }

    public String getRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /**
     * 获取用户名
     */
    public String getUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 校验 Token 是否有效（未过期且未被篡改）
     */
    public Boolean validateToken(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}