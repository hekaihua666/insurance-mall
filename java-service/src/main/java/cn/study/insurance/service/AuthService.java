package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.AuthResponse;
import cn.study.insurance.dto.LoginRequest;
import cn.study.insurance.dto.RegisterRequest;
import cn.study.insurance.dto.UserResponse;
import cn.study.insurance.entity.User;
import cn.study.insurance.mapper.UserMapper;
import cn.study.insurance.security.JwtTokenProvider;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户认证业务。
 *
 * 注册时用 BCrypt 加密密码存储；登录时比对哈希，成功后签发 JWT。
 * 密码哈希绝不返回给前端。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtTokenProvider tokenProvider;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtTokenProvider tokenProvider) {
        this.userMapper = userMapper;
        this.tokenProvider = tokenProvider;
    }

    /** 注册：用户名和邮箱都要求唯一，密码加密后入库 */
    public AuthResponse register(RegisterRequest request) {
        boolean usernameExists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername())) > 0;
        if (usernameExists) {
            throw new BizException("用户名已被占用");
        }

        boolean emailExists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())) > 0;
        if (emailExists) {
            throw new BizException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        // 没填昵称就用用户名代替，保证前端展示不为空
        user.setDisplayName(
                request.getDisplayName() == null || request.getDisplayName().isBlank()
                        ? request.getUsername()
                        : request.getDisplayName());
        user.setStatus("active");
        userMapper.insert(user);

        return buildAuthResponse(user);
    }

    /** 登录：校验用户名和密码，成功返回 token */
    public AuthResponse login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));

        // 用户不存在和密码错误给同样的提示，避免暴露哪些用户名存在
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw BizException.unauthorized("用户名或密码错误");
        }
        if (!"active".equals(user.getStatus())) {
            throw BizException.forbidden("账号已停用，请联系客服");
        }

        return buildAuthResponse(user);
    }

    /** 按 ID 查当前用户信息，供 /auth/me 使用 */
    public UserResponse getUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        return toUserResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = tokenProvider.issue(user.getId(), user.getUsername());
        return new AuthResponse(token, toUserResponse(user));
    }

    private UserResponse toUserResponse(User user) {
        UserResponse response = new UserResponse();
        // User 和 UserResponse 字段完全同名同类型，一次性拷过去，不暴露密码哈希
        BeanUtils.copyProperties(user, response);
        return response;
    }
}
