package cn.study.insurance.security;

import cn.study.insurance.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

/**
 * JWT 签发与解析。
 *
 * token 里只放 userId 和 username，不放敏感信息。
 * 密钥和有效期从配置读取，避免硬编码。
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey key;
    private final Duration ttl;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.ttl-hours:24}") long ttlHours) {
        // 默认密钥已经写在这份讲义里了，谁都能拿去签发任意用户的 token，上线必须换成环境变量
        if (secret.startsWith("insurance-demo-")) {
            log.warn("!! 正在使用演示 JWT 密钥，生产环境必须通过 JWT_SECRET 环境变量注入 !!");
        }
        // HS256 要求密钥至少 256 位，这里直接按 UTF-8 字节构造
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** 签发 token */
    public String issue(Long userId, String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    /** 解析 token，取用户 ID；无效或过期抛 401 */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw BizException.unauthorized("登录状态无效或已过期，请重新登录");
        }
    }
}
