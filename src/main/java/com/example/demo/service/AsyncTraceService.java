package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

/**
 * 异步追踪测试服务
 *
 * 用于验证在异步线程中，Sentry 的 Trace 上下文是否能正确传播，
 * 包括日志记录和数据库查询是否能被关联到同一个 Trace 中。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncTraceService {

    private final UserRepository userRepository;

    /**
     * 异步方法：执行数据库操作和日志记录
     *
     * 注意：默认情况下，Spring 的 @Async 不会自动传播 Sentry 的 Trace 上下文。
     * 异步线程会创建新的 Trace ID。
     */
    @Async
    public CompletableFuture<String> asyncDatabaseOperation(String username) {
        log.info("📌 [异步线程] 开始执行异步任务，用户名: {}", username);
        log.info("📌 [异步线程] 线程名称: {}", Thread.currentThread().getName());

        try {
            // 添加面包屑以便追踪
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage("异步任务开始");
            breadcrumb.setCategory("async");
            breadcrumb.setData("username", username);
            Sentry.addBreadcrumb(breadcrumb);

            try {
                // 模拟延迟
                Thread.sleep(500);

                // 查询用户
                log.info("📌 [异步线程] 执行数据库查询: findByUsername");
                User user = userRepository.findByUsername(username).orElse(null);

                if (user != null) {
                    log.info("📌 [异步线程] 找到用户: {}, 年龄: {}", user.getUsername(), user.getAge());
                } else {
                    log.warn("📌 [异步线程] 未找到用户: {}", username);
                }

                // 创建新用户
                log.info("📌 [异步线程] 创建新用户");
                User newUser = new User();
                newUser.setUsername(username + "_async");
                newUser.setEmail(username + "_async@example.com");
                newUser.setPhone("13800138000");
                newUser.setAge(25);
                newUser.setActive(true);

                User savedUser = userRepository.save(newUser);
                log.info("📌 [异步线程] 用户创建成功: {}, ID: {}", savedUser.getUsername(), savedUser.getId());

                // 再次查询所有用户
                log.info("📌 [异步线程] 查询所有用户数量");
                long userCount = userRepository.count();
                log.info("📌 [异步线程] 当前用户总数: {}", userCount);

                String result = String.format(
                    "异步任务完成 - 创建用户: %s, ID: %d, 总用户数: %d",
                    savedUser.getUsername(),
                    savedUser.getId(),
                    userCount
                );

                log.info("📌 [异步线程] {}", result);
                log.info("📌 [异步线程] 任务完成时间: {}", System.currentTimeMillis());

                // 添加成功完成的面包屑
                Breadcrumb successBreadcrumb = new Breadcrumb();
                successBreadcrumb.setMessage("异步任务成功完成");
                successBreadcrumb.setCategory("async");
                successBreadcrumb.setData("result", "success");
                Sentry.addBreadcrumb(successBreadcrumb);

                return CompletableFuture.completedFuture(result);

            } catch (Exception e) {
                log.error("📌 [异步线程] 异步任务执行失败", e);
                Sentry.captureException(e);
                return CompletableFuture.failedFuture(e);
            }
        } catch (Exception e) {
            log.error("📌 [异步线程] 任务启动失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 异步方法：执行多个数据库操作
     */
    @Async
    @Transactional
    public CompletableFuture<String> asyncMultipleDbOperations(String prefix) {
        log.info("📌 [异步线程-多操作] 开始执行多个数据库操作");
        log.info("📌 [异步线程-多操作] 线程名称: {}", Thread.currentThread().getName());

        try {
            // 添加面包屑
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage("多操作异步任务开始");
            breadcrumb.setCategory("async");
            breadcrumb.setData("prefix", prefix);
            Sentry.addBreadcrumb(breadcrumb);

            try {
                // 操作1: 查询用户数量
                log.info("📌 [异步线程-多操作] 步骤1: 查询用户总数");
                long count1 = userRepository.count();
                log.info("📌 [异步线程-多操作] 当前用户数: {}", count1);
                Thread.sleep(200);

                // 操作2: 创建用户
                log.info("📌 [异步线程-多操作] 步骤2: 创建用户");
                User user1 = new User();
                user1.setUsername(prefix + "_async_1");
                user1.setEmail(prefix + "_async_1@example.com");
                user1.setPhone("13800138001");
                user1.setAge(28);
                user1.setActive(true);
                User saved1 = userRepository.save(user1);
                log.info("📌 [异步线程-多操作] 用户1创建成功: {}", saved1.getId());
                Thread.sleep(200);

                // 操作3: 再次查询
                log.info("📌 [异步线程-多操作] 步骤3: 再次查询用户总数");
                long count2 = userRepository.count();
                log.info("📌 [异步线程-多操作] 更新后用户数: {}", count2);
                Thread.sleep(200);

                // 操作4: 创建第二个用户
                log.info("📌 [异步线程-多操作] 步骤4: 创建第二个用户");
                User user2 = new User();
                user2.setUsername(prefix + "_async_2");
                user2.setEmail(prefix + "_async_2@example.com");
                user2.setPhone("13800138002");
                user2.setAge(32);
                user2.setActive(true);
                User saved2 = userRepository.save(user2);
                log.info("📌 [异步线程-多操作] 用户2创建成功: {}", saved2.getId());

                String result = String.format(
                    "多操作异步任务完成 - 创建了 %d 个用户, 最终用户总数: %d",
                    2,
                    count2 + 1
                );

                log.info("📌 [异步线程-多操作] {}", result);

                // 添加成功完成的面包屑
                Breadcrumb successBreadcrumb = new Breadcrumb();
                successBreadcrumb.setMessage("多操作异步任务成功完成");
                successBreadcrumb.setCategory("async");
                successBreadcrumb.setData("result", "success");
                Sentry.addBreadcrumb(successBreadcrumb);

                return CompletableFuture.completedFuture(result);

            } catch (Exception e) {
                log.error("📌 [异步线程-多操作] 异步多操作执行失败", e);
                Sentry.captureException(e);
                return CompletableFuture.failedFuture(e);
            }
        } catch (Exception e) {
            log.error("📌 [异步线程-多操作] 任务启动失败", e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
