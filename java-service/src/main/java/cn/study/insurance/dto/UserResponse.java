package cn.study.insurance.dto;

import lombok.Getter;
import lombok.Setter;

/** 用户信息出参，不包含密码哈希 */
@Getter
@Setter
public class UserResponse {

    private Long id;
    private String username;
    private String email;
    private String displayName;
}
