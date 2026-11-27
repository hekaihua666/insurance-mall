package cn.study.insurance.config;

import cn.study.insurance.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 配置。
 *
 * 只做一件事：注册登录拦截器，并放行登录/注册/产品查询等不需要登录的路径。
 * Controller 里要拿当前用户 ID 直接用 Spring 自带的
 * {@code @RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId}——
 * 拦截器已经把它塞进 request attribute 了，不用自己再造参数解析器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/v1/auth/login",
                        "/api/v1/auth/register",
                        // 产品列表是公开浏览的，不登录也能看
                        "/api/v1/products",
                        "/api/v1/products/**"
                );
    }
}
