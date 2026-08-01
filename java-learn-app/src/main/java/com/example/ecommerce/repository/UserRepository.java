package com.example.ecommerce.repository;

import com.example.ecommerce.model.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户持久层。M2 复用 M1 已引入的 spring-boot-starter-jdbc，直接用 JdbcTemplate 读写，
 * 不新增 ORM 依赖；M3 会用 MyBatis-Plus 接入 MySQL，这里仅覆盖鉴权所需的最小 CRUD。
 */
@Repository
public class UserRepository {

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(User user) {
        jdbcTemplate.update(
                "INSERT INTO users (username, password, role) VALUES (?, ?, ?)",
                user.getUsername(), user.getPassword(), user.getRole());
    }

    public Optional<User> findByUsername(String username) {
        String sql = "SELECT id, username, password, role FROM users WHERE username = ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            User u = new User();
            u.setId(rs.getLong("id"));
            u.setUsername(rs.getString("username"));
            u.setPassword(rs.getString("password"));
            u.setRole(rs.getString("role"));
            return u;
        }, username).stream().findFirst();
    }
}
