package com.example.demo.web;

import com.example.demo.common.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT = Header.Payload.Signature，三段 Base64URL 用点连接。
 * <ul>
 *   <li>Header：签名算法，这里是 HS256</li>
 *   <li>Payload：业务数据（userId、过期时间），只是 Base64 编码，<b>不是加密</b>，任何人都能解码看到，不能放密码</li>
 *   <li>Signature：用服务端密钥对前两段签名。别人改了 Payload，签名就对不上，解析时直接抛异常</li>
 * </ul>
 * 服务端不存 token，靠验签判断真伪，所以天然支持多实例横向扩展；代价是签发后无法主动作废（见八股）。
 * <p>
 * 这里用 @Value 逐个读取配置，对比 starter/GreetingProperties 的 @ConfigurationProperties 写法。
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long ttlMillis;

    public JwtUtil(@Value("${demo.jwt.secret}") String secret,
                   @Value("${demo.jwt.ttl-minutes}") long ttlMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlMinutes * 60 * 1000;
    }

    public String createToken(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    /** 验签 + 校验过期时间，任何一项失败都按未登录处理 */
    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException e) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "token 无效或已过期");
        }
    }
}
