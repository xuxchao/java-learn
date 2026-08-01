package com.example.ecommerce.model;

/**
 * 用户领域对象。M2 仅用 JDBC 直接落库，M3 接入 MyBatis-Plus 后可平滑替换为实体注解版本。
 * 这里同时提供无参构造 + setter 以便后续复用 RowMapper/ORM。
 */
public class User {

    private Long id;
    private String username;
    private String password;   // BCrypt 密文
    private String role;       // USER / ADMIN

    public User() {
    }

    public User(String username, String password, String role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
