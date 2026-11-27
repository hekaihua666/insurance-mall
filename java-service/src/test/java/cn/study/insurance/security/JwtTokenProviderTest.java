package cn.study.insurance.security;

import cn.study.insurance.common.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 只用测试专用密钥检查签名及过期行为，不读取本机 JWT 配置。 */
class JwtTokenProviderTest {
    private static final String TEST_KEY = "test-only-signing-key-for-regression-20261120";

    @Test
    void validTokenResolvesItsSignedUserId() {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_KEY, 1);
        assertEquals(9L, provider.parseUserId(provider.issue(9L, "test-user")));
    }

    @Test
    void tokenFromAnotherKeyIsRejected() {
        String token = new JwtTokenProvider(TEST_KEY, 1).issue(9L, "test-user");
        JwtTokenProvider other = new JwtTokenProvider("different-test-signing-key-for-regression", 1);
        assertEquals(401, assertThrows(BizException.class, () -> other.parseUserId(token)).getStatus());
    }

    @Test
    void expiredTokenIsRejected() {
        JwtTokenProvider provider = new JwtTokenProvider(TEST_KEY, -1);
        String token = provider.issue(9L, "test-user");
        assertEquals(401, assertThrows(BizException.class, () -> provider.parseUserId(token)).getStatus());
    }
}
