package com.example.demo.exception;

/**
 * 用户不存在异常
 * 当尝试访问、更新或删除不存在的用户时抛出此异常
 */
public class UserNotFoundException extends RuntimeException {
    
    private final Long userId;
    
    public UserNotFoundException(Long userId) {
        super("用户不存在: " + userId);
        this.userId = userId;
    }
    
    public UserNotFoundException(String message) {
        super(message);
        this.userId = null;
    }
    
    public Long getUserId() {
        return userId;
    }
}