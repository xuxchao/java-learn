package com.example.ecommerce.service;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.common.ErrorCode;
import com.example.ecommerce.model.User;
import com.example.ecommerce.repository.UserRepository;
import com.example.ecommerce.security.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 鉴权业务逻辑：注册（BCrypt 加密落库）、登录（校验密码 + 签发 JWT）。
 * BCryptPasswordEncoder 为无状态工具，直接实例化即可，无需额外 @Bean。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    public void register(String username, String password, String role) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ApiException(ErrorCode.USERNAME_ALREADY_EXISTS);
        }
        String hashed = passwordEncoder.encode(password);
        String actualRole = (role == null || role.isBlank()) ? "USER" : role.toUpperCase();
        userRepository.save(new User(username, hashed, actualRole));
    }

    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
    }
}
