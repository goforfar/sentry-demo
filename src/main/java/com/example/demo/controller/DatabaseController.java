package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.UserService;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class DatabaseController {

    private final UserService userService;

    /**
     * 创建单个用户 - 正常情况
     * POST /api/users
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody User user) {
        log.info("收到创建用户请求: {}", user.getUsername());
        try {
            User createdUser = userService.createUser(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户创建成功");
            response.put("data", createdUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量创建用户 - 成功场景
     * POST /api/users/batch/success
     */
    @PostMapping("/batch/success")
    public ResponseEntity<Map<String, Object>> batchCreateSuccess(@RequestBody List<User> users) {
        log.info("收到批量创建用户请求（成功场景），数量: {}", users.size());
        try {
            List<User> createdUsers = userService.batchCreateUsersSuccess(users);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量创建成功");
            response.put("count", createdUsers.size());
            response.put("data", createdUsers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量创建失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "批量创建失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量创建用户 - 事务回滚场景（违反唯一约束）
     * POST /api/users/batch/rollback
     */
    @PostMapping("/batch/rollback")
    public ResponseEntity<Map<String, Object>> batchCreateWithRollback(@RequestBody List<User> users) {
        log.info("收到批量创建用户请求（回滚场景），数量: {}", users.size());
        try {
            List<User> createdUsers = userService.batchCreateUsersWithRollback(users);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "批量创建成功");
            response.put("data", createdUsers);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("批量创建失败，事务已回滚", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "批量创建失败，事务已回滚: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 创建用户 - 业务验证异常场景
     * POST /api/users/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<Map<String, Object>> createUserWithValidation(@RequestBody User user) {
        log.info("收到创建用户请求（含业务验证）");
        try {
            User createdUser = userService.createUserWithBusinessValidation(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户创建成功");
            response.put("data", createdUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建用户失败，业务验证未通过", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "业务验证失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 创建用户 - 运行时异常场景（事务回滚）
     * POST /api/users/runtime-error
     */
    @PostMapping("/runtime-error")
    public ResponseEntity<Map<String, Object>> createUserWithRuntimeError(@RequestBody User user) {
        log.info("收到创建用户请求（模拟运行时异常）");
        try {
            User createdUser = userService.createUserWithRuntimeException(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户创建成功");
            response.put("data", createdUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("创建用户失败，运行时异常导致事务回滚", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "运行时异常，事务已回滚: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 复杂事务场景 - 部分失败
     * POST /api/users/complex-transaction
     */
    @PostMapping("/complex-transaction")
    public ResponseEntity<Map<String, Object>> complexTransaction() {
        log.info("收到复杂事务场景请求");
        try {
            userService.transferUserScenario();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "复杂事务执行成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("复杂事务执行失败，事务已回滚", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "复杂事务执行失败，事务已回滚: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 查询所有用户
     * GET /api/users
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        log.info("收到查询所有用户请求");
        try {
            List<User> users = userService.getAllUsers();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("count", users.size());
            response.put("data", users);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 根据ID查询用户 - 可能抛出用户不存在异常
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        log.info("收到查询用户请求，ID: {}", id);
        try {
            User user = userService.getUserById(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", user);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("查询用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 根据用户名查询
     * GET /api/users/by-username/{username}
     */
    @GetMapping("/by-username/{username}")
    public ResponseEntity<Map<String, Object>> getUserByUsername(@PathVariable String username) {
        log.info("收到查询用户请求，用户名: {}", username);
        try {
            return userService.getUserByUsername(username)
                    .map(user -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", true);
                        response.put("data", user);
                        return ResponseEntity.ok(response);
                    })
                    .orElseGet(() -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("success", false);
                        response.put("message", "用户不存在: " + username);
                        return ResponseEntity.notFound().build();
                    });
        } catch (Exception e) {
            log.error("查询用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "查询失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 更新用户
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody User userDetails) {
        log.info("收到更新用户请求，ID: {}", id);
        try {
            User updatedUser = userService.updateUser(id, userDetails);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户更新成功");
            response.put("data", updatedUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("更新用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除用户
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        log.info("收到删除用户请求，ID: {}", id);
        try {
            userService.deleteUser(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "用户删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("删除用户失败", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 模拟数据库超时
     * POST /api/users/timeout
     */
    @PostMapping("/timeout")
    public ResponseEntity<Map<String, Object>> simulateTimeout(@RequestBody User user) {
        log.info("收到模拟数据库超时请求");
        try {
            User createdUser = userService.simulateDatabaseTimeout(user);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "操作完成");
            response.put("data", createdUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("数据库操作超时", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "操作超时: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 模拟并发修改冲突
     * POST /api/users/{id}/concurrent
     */
    @PostMapping("/{id}/concurrent")
    public ResponseEntity<Map<String, Object>> simulateConcurrent(@PathVariable Long id, @RequestParam String email) {
        log.info("收到模拟并发修改请求，ID: {}", id);
        try {
            User updatedUser = userService.simulateConcurrentModification(id, email);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "并发修改测试完成");
            response.put("data", updatedUser);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("并发修改冲突", e);
            Sentry.captureException(e);
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "并发修改冲突: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
