package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.service.AsyncTraceService;
import com.example.demo.service.UserService;
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 异步追踪测试控制器
 *
 * 用于验证在异步场景下，Sentry 的 Trace 上下文传播能力。
 * 测试内容：
 * 1. 主线程中的日志和数据库查询是否能被 Trace
 * 2. 异步线程中的日志和数据库查询是否能被关联到同一个 Trace
 */
@Slf4j
@RestController
@RequestMapping("/api/async-trace")
@RequiredArgsConstructor
public class AsyncTraceController {

    private final AsyncTraceService asyncTraceService;
    private final UserService userService;

    /**
     * 测试异步任务的 Trace 传播
     *
     * GET /api/async-trace/test?username=testuser
     *
     * 这个 endpoint 会：
     * 1. 在主线程中执行数据库操作和日志记录
     * 2. 然后启动异步任务，在异步线程中执行更多数据库操作
     * 3. 等待异步任务完成并返回结果
     *
     * 在 Sentry 中应该能看到：
     * - 主线程的操作 span
     * - 异步线程的操作是否在同一个 trace 中
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testAsyncTrace(
            @RequestParam(defaultValue = "testuser") String username) {

        log.info("════════════════════════════════════════");
        log.info("🚀 [主线程] 开始测试异步 Trace");
        log.info("🚀 [主线程] 线程名称: {}", Thread.currentThread().getName());
        log.info("════════════════════════════════════════");

        Map<String, Object> result = new HashMap<>();

        try {
            // ========== 主线程操作 ==========
            log.info("🚀 [主线程] 步骤1: 查询所有用户数量");
            long initialCount = userService.getAllUsers().size();
            log.info("🚀 [主线程] 当前用户总数: {}", initialCount);

            // 添加面包屑
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage("主线程查询用户");
            breadcrumb.setCategory("database");
            breadcrumb.setData("count", String.valueOf(initialCount));
            Sentry.addBreadcrumb(breadcrumb);

            // 稍微延迟
            Thread.sleep(200);

            log.info("🚀 [主线程] 步骤2: 创建测试用户");
            User user = new User();
            user.setUsername(username);
            user.setEmail(username + "@example.com");
            user.setPhone("13800138000");
            user.setAge(25);
            user.setActive(true);
            User createdUser = userService.createUser(user);
            log.info("🚀 [主线程] 用户创建成功: {}, ID: {}", createdUser.getUsername(), createdUser.getId());

            // 再次查询
            log.info("🚀 [主线程] 步骤3: 再次查询用户总数");
            long afterCreateCount = userService.getAllUsers().size();
            log.info("🚀 [主线程] 创建后用户总数: {}", afterCreateCount);

            // ========== 启动异步任务 ==========
            log.info("🚀 [主线程] 步骤4: 启动异步任务");
            log.info("🚀 [主线程] ⚠️  注意：异步任务可能在不同的 Trace 中");

            // 调用异步方法
            CompletableFuture<String> asyncFuture = asyncTraceService.asyncDatabaseOperation(username);

            // 主线程继续执行其他操作
            log.info("🚀 [主线程] 步骤5: 主线程继续执行（异步任务在后台运行）");
            Thread.sleep(300);
            log.info("🚀 [主线程] 查询单个用户: {}", username);
            User foundUser = userService.getUserByUsername(username).orElse(null);
            log.info("🚀 [主线程] 查询结果: {}", foundUser != null ? foundUser.getUsername() : "未找到");

            // 等待异步任务完成
            log.info("🚀 [主线程] 步骤6: 等待异步任务完成...");
            String asyncResult = asyncFuture.get(); // 阻塞等待
            log.info("🚀 [主线程] 异步任务结果: {}", asyncResult);

            // 最终查询
            log.info("🚀 [主线程] 步骤7: 最终查询用户总数");
            long finalCount = userService.getAllUsers().size();
            log.info("🚀 [主线程] 最终用户总数: {}", finalCount);

            // 构建结果
            result.put("mainThread", Thread.currentThread().getName());
            result.put("initialCount", initialCount);
            result.put("createdUserId", createdUser.getId());
            result.put("afterCreateCount", afterCreateCount);
            result.put("asyncResult", asyncResult);
            result.put("finalCount", finalCount);
            result.put("message", "异步 Trace 测试完成，请查看 Sentry 验证异步任务是否在同一 Trace 中");

            log.info("════════════════════════════════════════");
            log.info("✅ [主线程] 测试完成");
            log.info("════════════════════════════════════════");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [主线程] 测试失败", e);
            Sentry.captureException(e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 测试多个异步任务的 Trace
     *
     * GET /api/async-trace/test-multiple?prefix=batch
     */
    @GetMapping("/test-multiple")
    public ResponseEntity<Map<String, Object>> testMultipleAsync(
            @RequestParam(defaultValue = "batch") String prefix) {

        log.info("════════════════════════════════════════");
        log.info("🚀 [主线程] 开始测试多个异步任务 Trace");
        log.info("════════════════════════════════════════");

        Map<String, Object> result = new HashMap<>();

        try {
            // 主线程创建用户
            log.info("🚀 [主线程] 创建初始用户");
            User user = new User();
            user.setUsername(prefix + "_main");
            user.setEmail(prefix + "_main@example.com");
            user.setPhone("13900139000");
            user.setAge(30);
            user.setActive(true);
            User createdUser = userService.createUser(user);
            log.info("🚀 [主线程] 主线程用户创建成功: {}", createdUser.getId());

            // 启动多个异步任务
            log.info("🚀 [主线程] 启动第一个异步任务");
            CompletableFuture<String> future1 = asyncTraceService.asyncDatabaseOperation(prefix + "_1");

            Thread.sleep(100);

            log.info("🚀 [主线程] 启动第二个异步任务");
            CompletableFuture<String> future2 = asyncTraceService.asyncDatabaseOperation(prefix + "_2");

            Thread.sleep(100);

            log.info("🚀 [主线程] 启动第三个异步任务（多操作）");
            CompletableFuture<String> future3 = asyncTraceService.asyncMultipleDbOperations(prefix + "_multi");

            // 主线程继续工作
            log.info("🚀 [主线程] 主线程继续执行查询");
            Thread.sleep(300);
            long count = userService.getAllUsers().size();
            log.info("🚀 [主线程] 当前用户总数: {}", count);

            // 等待所有异步任务完成
            log.info("🚀 [主线程] 等待所有异步任务完成...");
            String result1 = future1.get();
            String result2 = future2.get();
            String result3 = future3.get();

            log.info("🚀 [主线程] 所有任务完成");

            result.put("mainThread", Thread.currentThread().getName());
            result.put("mainUserId", createdUser.getId());
            result.put("mainUserCount", count);
            result.put("asyncTask1", result1);
            result.put("asyncTask2", result2);
            result.put("asyncTask3", result3);
            result.put("message", "多个异步任务测试完成，请查看 Sentry 验证是否在同一 Trace 中");

            log.info("════════════════════════════════════════");
            log.info("✅ [主线程] 多异步任务测试完成");
            log.info("════════════════════════════════════════");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [主线程] 多异步任务测试失败", e);
            Sentry.captureException(e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 测试手动创建线程的 Trace 传播
     *
     * GET /api/async-trace/manual-thread
     */
    @GetMapping("/manual-thread")
    public ResponseEntity<Map<String, Object>> testManualThread() {
        log.info("════════════════════════════════════════");
        log.info("🧵 [主线程] 开始测试手动创建线程");
        log.info("🧵 [主线程] 线程: {}", Thread.currentThread().getName());
        log.info("════════════════════════════════════════");

        Map<String, Object> result = new HashMap<>();
        Map<String, String> threadResults = new HashMap<>();

        try {
            // 添加面包屑
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage("手动创建线程测试");
            breadcrumb.setCategory("thread-test");
            Sentry.addBreadcrumb(breadcrumb);

            // 测试1: 手动创建 Thread
            log.info("🧵 [主线程] 测试1: 手动创建 Thread");
            final StringBuilder thread1Result = new StringBuilder();
            Thread thread1 = new Thread(() -> {
                log.info("🧵 [手动Thread1] 线程名称: {}", Thread.currentThread().getName());
                log.info("🧵 [手动Thread1] 尝试获取 Trace ID");

                try {
                    // 添加面包屑
                    Breadcrumb b1 = new Breadcrumb();
                    b1.setMessage("手动Thread1执行");
                    b1.setCategory("manual-thread");
                    Sentry.addBreadcrumb(b1);

                    // 执行数据库操作
                    long count = userService.getAllUsers().size();
                    thread1Result.append(String.format("手动Thread1完成 - 用户数: %d", count));
                    log.info("🧵 [手动Thread1] {}", thread1Result);
                } catch (Exception e) {
                    log.error("🧵 [手动Thread1] 执行失败", e);
                    thread1Result.append("失败: ").append(e.getMessage());
                }
            });

            thread1.start();
            thread1.join();
            threadResults.put("manualThread", thread1Result.toString());

            // 测试2: 使用 Runnable 和 Thread
            log.info("🧵 [主线程] 测试2: 使用 Runnable");
            final StringBuilder thread2Result = new StringBuilder();
            Runnable runnable = () -> {
                log.info("🧵 [Runnable] 线程名称: {}", Thread.currentThread().getName());
                log.info("🧵 [Runnable] 尝试获取 Trace ID");

                try {
                    // 添加面包屑
                    Breadcrumb b2 = new Breadcrumb();
                    b2.setMessage("Runnable执行");
                    b2.setCategory("runnable-thread");
                    Sentry.addBreadcrumb(b2);

                    // 执行数据库操作
                    long count = userService.getAllUsers().size();
                    thread2Result.append(String.format("Runnable完成 - 用户数: %d", count));
                    log.info("🧵 [Runnable] {}", thread2Result);
                } catch (Exception e) {
                    log.error("🧵 [Runnable] 执行失败", e);
                    thread2Result.append("失败: ").append(e.getMessage());
                }
            };

            Thread thread2 = new Thread(runnable);
            thread2.start();
            thread2.join();
            threadResults.put("runnableThread", thread2Result.toString());

            // 测试3: 继承 Thread 类
            log.info("🧵 [主线程] 测试3: 继承 Thread 类");
            final StringBuilder thread3Result = new StringBuilder();
            class MyThread extends Thread {
                @Override
                public void run() {
                    log.info("🧵 [继承Thread] 线程名称: {}", Thread.currentThread().getName());
                    log.info("🧵 [继承Thread] 尝试获取 Trace ID");

                    try {
                        Breadcrumb b3 = new Breadcrumb();
                        b3.setMessage("继承Thread执行");
                        b3.setCategory("extended-thread");
                        Sentry.addBreadcrumb(b3);

                        Thread.sleep(100);
                        long count = userService.getAllUsers().size();
                        thread3Result.append(String.format("继承Thread完成 - 用户数: %d", count));
                        log.info("🧵 [继承Thread] {}", thread3Result);
                    } catch (Exception e) {
                        log.error("🧵 [继承Thread] 执行失败", e);
                        thread3Result.append("失败: ").append(e.getMessage());
                    }
                }
            }

            MyThread myThread = new MyThread();
            myThread.start();
            myThread.join();
            threadResults.put("extendedThread", thread3Result.toString());

            // 测试4: 多个并发手动线程
            log.info("🧵 [主线程] 测试4: 多个并发手动线程");
            final StringBuilder thread4Result = new StringBuilder();
            Thread[] concurrentThreads = new Thread[3];
            String[] concurrentResults = new String[3];

            for (int i = 0; i < 3; i++) {
                final int index = i;
                concurrentThreads[i] = new Thread(() -> {
                    log.info("🧵 [并发Thread-{}] 线程名称: {}", index, Thread.currentThread().getName());

                    try {
                        Breadcrumb b = new Breadcrumb();
                        b.setMessage(String.format("并发Thread-%d执行", index));
                        b.setCategory("concurrent-manual-thread");
                        b.setData("threadIndex", String.valueOf(index));
                        Sentry.addBreadcrumb(b);

                        Thread.sleep(50 * index);
                        long count = userService.getAllUsers().size();
                        concurrentResults[index] = String.format("Thread-%d完成 - 用户数: %d", index, count);
                        log.info("🧵 [并发Thread-{}] {}", index, concurrentResults[index]);
                    } catch (Exception e) {
                        log.error("🧵 [并发Thread-{}] 失败", index, e);
                        concurrentResults[index] = "Thread-" + index + "失败: " + e.getMessage();
                    }
                });
            }

            // 启动所有线程
            for (Thread t : concurrentThreads) {
                t.start();
            }

            // 等待所有线程完成
            for (Thread t : concurrentThreads) {
                t.join();
            }

            thread4Result.append(String.format("并发线程完成: [%s, %s, %s]",
                concurrentResults[0], concurrentResults[1], concurrentResults[2]));
            threadResults.put("concurrentThreads", thread4Result.toString());

            // 测试5: 手动线程中再创建子线程
            log.info("🧵 [主线程] 测试5: 手动线程中创建子线程");
            final StringBuilder thread5Result = new StringBuilder();
            Thread parentThread = new Thread(() -> {
                log.info("🧵 [父线程] 线程名称: {}", Thread.currentThread().getName());

                try {
                    Breadcrumb bp = new Breadcrumb();
                    bp.setMessage("父线程执行");
                    bp.setCategory("parent-thread");
                    Sentry.addBreadcrumb(bp);

                    // 在手动线程中再创建子线程
                    Thread childThread = new Thread(() -> {
                        log.info("🧵 [子线程] 线程名称: {}", Thread.currentThread().getName());

                        try {
                            Breadcrumb bc = new Breadcrumb();
                            bc.setMessage("子线程执行");
                            bc.setCategory("child-thread");
                            Sentry.addBreadcrumb(bc);

                            Thread.sleep(100);
                            long count = userService.getAllUsers().size();
                            thread5Result.append(String.format("子线程完成 - 用户数: %d", count));
                            log.info("🧵 [子线程] {}", thread5Result);
                        } catch (Exception e) {
                            log.error("🧵 [子线程] 失败", e);
                            thread5Result.append("失败: ").append(e.getMessage());
                        }
                    });
                    childThread.start();
                    childThread.join();

                } catch (Exception e) {
                    log.error("🧵 [父线程] 失败", e);
                    thread5Result.append("失败: ").append(e.getMessage());
                }
            });
            parentThread.start();
            parentThread.join();
            threadResults.put("nestedThreads", thread5Result.toString());

            result.put("mainThread", Thread.currentThread().getName());
            result.put("testResults", threadResults);
            result.put("message", "手动创建线程测试完成，请查看 Sentry 验证 Trace ID 传播");

            log.info("════════════════════════════════════════");
            log.info("✅ [主线程] 手动线程测试完成");
            log.info("════════════════════════════════════════");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [主线程] 测试失败", e);
            Sentry.captureException(e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    /**
     * 测试 ExecutorService 的 Trace 传播
     *
     * GET /api/async-trace/executor-service
     */
    @GetMapping("/executor-service")
    public ResponseEntity<Map<String, Object>> testExecutorService() {
        log.info("════════════════════════════════════════");
        log.info("🧵 [主线程] 开始测试 ExecutorService");
        log.info("🧵 [主线程] 线程: {}", Thread.currentThread().getName());
        log.info("════════════════════════════════════════");

        Map<String, Object> result = new HashMap<>();
        Map<String, String> executorResults = new HashMap<>();
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        try {
            // 添加面包屑
            Breadcrumb breadcrumb = new Breadcrumb();
            breadcrumb.setMessage("ExecutorService测试");
            breadcrumb.setCategory("executor-test");
            Sentry.addBreadcrumb(breadcrumb);

            // 测试1: 提交 Runnable 到 ExecutorService
            log.info("🧵 [主线程] 测试1: ExecutorService.submit(Runnable)");
            final StringBuilder result1 = new StringBuilder();
            Future<?> future1 = executorService.submit(() -> {
                log.info("🧵 [Executor1] 线程名称: {}", Thread.currentThread().getName());

                try {
                    Breadcrumb b1 = new Breadcrumb();
                    b1.setMessage("Executor1执行");
                    b1.setCategory("executor");
                    Sentry.addBreadcrumb(b1);

                    long count = userService.getAllUsers().size();
                    result1.append(String.format("Executor1完成 - 用户数: %d", count));
                    log.info("🧵 [Executor1] {}", result1);
                } catch (Exception e) {
                    log.error("🧵 [Executor1] 失败", e);
                    result1.append("失败: ").append(e.getMessage());
                }
            });
            future1.get();
            executorResults.put("executor1", result1.toString());

            // 测试2: 提交 Callable 到 ExecutorService
            log.info("🧵 [主线程] 测试2: ExecutorService.submit(Callable)");
            final StringBuilder result2 = new StringBuilder();
            Future<String> future2 = executorService.submit(() -> {
                log.info("🧵 [Executor2] 线程名称: {}", Thread.currentThread().getName());

                try {
                    Breadcrumb b2 = new Breadcrumb();
                    b2.setMessage("Executor2执行");
                    b2.setCategory("executor");
                    Sentry.addBreadcrumb(b2);

                    Thread.sleep(200);
                    long count = userService.getAllUsers().size();
                    String msg = String.format("Executor2完成 - 用户数: %d", count);
                    log.info("🧵 [Executor2] {}", msg);
                    return msg;
                } catch (Exception e) {
                    log.error("🧵 [Executor2] 失败", e);
                    return "失败: " + e.getMessage();
                }
            });
            executorResults.put("executor2", future2.get());

            // 测试3: 使用 CompletableFuture.supplyAsync
            log.info("🧵 [主线程] 测试3: CompletableFuture.supplyAsync");
            CompletableFuture<String> completableFuture = CompletableFuture.supplyAsync(() -> {
                log.info("🧵 [CompletableFuture] 线程名称: {}", Thread.currentThread().getName());

                try {
                    Breadcrumb b3 = new Breadcrumb();
                    b3.setMessage("CompletableFuture执行");
                    b3.setCategory("completable-future");
                    Sentry.addBreadcrumb(b3);

                    Thread.sleep(200);
                    long count = userService.getAllUsers().size();
                    String msg = String.format("CompletableFuture完成 - 用户数: %d", count);
                    log.info("🧵 [CompletableFuture] {}", msg);
                    return msg;
                } catch (Exception e) {
                    log.error("🧵 [CompletableFuture] 失败", e);
                    return "失败: " + e.getMessage();
                }
            });
            executorResults.put("completableFuture", completableFuture.get());

            result.put("mainThread", Thread.currentThread().getName());
            result.put("testResults", executorResults);
            result.put("message", "ExecutorService 测试完成，请查看 Sentry 验证 Trace ID 传播");

            log.info("════════════════════════════════════════");
            log.info("✅ [主线程] ExecutorService 测试完成");
            log.info("════════════════════════════════════════");

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("❌ [主线程] 测试失败", e);
            Sentry.captureException(e);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * 简单的测试 endpoint，仅打印日志信息
     *
     * GET /api/async-trace/simple
     */
    @GetMapping("/simple")
    public ResponseEntity<Map<String, Object>> simpleTest() {
        Map<String, Object> result = new HashMap<>();

        log.info("📍 [简单测试] 开始");
        log.info("📍 [简单测试] 线程: {}", Thread.currentThread().getName());

        result.put("thread", Thread.currentThread().getName());
        result.put("message", "简单测试完成，请查看 Sentry 日志中的 Trace 信息");

        log.info("📍 [简单测试] 完成");

        return ResponseEntity.ok(result);
    }
}
