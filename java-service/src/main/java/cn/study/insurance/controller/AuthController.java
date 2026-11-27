package cn.study.insurance.controller;

import cn.study.insurance.common.ApiResponse;
import cn.study.insurance.dto.AuthResponse;
import cn.study.insurance.dto.LoginRequest;
import cn.study.insurance.dto.RegisterRequest;
import cn.study.insurance.dto.UserResponse;
import cn.study.insurance.security.AuthInterceptor;
import cn.study.insurance.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户认证接口：注册、登录、查当前用户。 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    /** 前端刷新页面时用它验证 token 是否还有效 */
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId) {
        return ApiResponse.ok(authService.getUser(userId));
    }
}
