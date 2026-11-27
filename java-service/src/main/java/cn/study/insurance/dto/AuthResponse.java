package cn.study.insurance.dto;

import lombok.Getter;

/** 登录/注册成功后的返回：token + 用户信息 */
@Getter
public class AuthResponse {

    private final String token;
    private final UserResponse user;

    public AuthResponse(String token, UserResponse user) {
        this.token = token;
        this.user = user;
    }
}
