package cn.study.insurance.security;

import cn.study.insurance.common.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录拦截器。
 *
 * 从 Authorization: Bearer xxx 里取 token，解析出 userId 放进 request attribute。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "currentUserId";

    private final JwtTokenProvider tokenProvider;

    public AuthInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        boolean hasToken = header != null && header.startsWith("Bearer ");

        if (!hasToken) {
            throw BizException.unauthorized("请先登录");
        }

        String token = header.substring(7).trim();
        Long userId = tokenProvider.parseUserId(token);
        request.setAttribute(USER_ID_ATTR, userId);
        return true;
    }

}
