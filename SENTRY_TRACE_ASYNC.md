# Sentry Trace 传播测试报告

## 测试概述

本测试旨在验证 Sentry 在 Java Spring Boot 应用中，不同异步场景下的 Trace ID 传播能力。

**测试时间**: 2026-01-09
**Sentry SDK 版本**: 8.29.0
**Spring Boot 版本**: 3.2.0
**测试环境**: macOS Darwin 22.1.0

---

## 测试结论总结

### ✅ 自动传播 Trace ID（无需手动处理）

| 异步方式 | 测试场景 | 传播状态 | 说明 |
|---------|---------|---------|------|
| **Spring @Async** | `@Async` 注解的方法 | ✅ 成功 | 通过配置的线程池（taskExecutor）执行 |
| **ExecutorService** | `submit(Runnable)` | ✅ 成功 | 线程池自动包装任务 |
| **ExecutorService** | `submit(Callable)` | ✅ 成功 | 线程池自动包装任务 |
| **CompletableFuture** | `supplyAsync()` | ✅ 成功 | 使用 ForkJoinPool，自动支持 |

### ❌ 不会自动传播 Trace ID

| 异步方式 | 测试场景 | 传播状态 | 说明 |
|---------|---------|---------|------|
| **手动创建 Thread** | `new Thread(lambda)` | ❌ 失败 | 直接创建，绕过线程池 |
| **Runnable 接口** | `new Thread(runnable)` | ❌ 失败 | 直接创建，绕过线程池 |
| **继承 Thread 类** | `class MyThread extends Thread` | ❌ 失败 | 继承方式，绕过线程池 |
| **并发手动线程** | 多个 `new Thread()` 并发 | ❌ 失败 | 所有手动线程都无法传播 |
| **嵌套手动线程** | 手动线程中创建子线程 | ❌ 失败 | 父子线程都无法传播 |

---

## 详细测试场景

### 1. Spring @Async 方法

**测试代码**:
```java
@Async
public CompletableFuture<String> asyncDatabaseOperation(String username) {
    log.info("异步线程执行操作");
    // 数据库操作
    return CompletableFuture.completedFuture("完成");
}
```

**测试 Endpoint**: `GET /api/async-trace/test?username=testuser`

**结果**: ✅ 异步线程中的日志和数据库查询都被关联到同一个 Trace

---

### 2. ExecutorService

**测试代码**:
```java
ExecutorService executorService = Executors.newFixedThreadPool(3);

// 测试1: Runnable
Future<?> future1 = executorService.submit(() -> {
    log.info("Executor1 执行");
    // 数据库操作
});

// 测试2: Callable
Future<String> future2 = executorService.submit(() -> {
    log.info("Executor2 执行");
    return "完成";
});
```

**测试 Endpoint**: `GET /api/async-trace/executor-service`

**结果**: ✅ 所有通过 ExecutorService 提交的任务都能传播 Trace ID

---

### 3. CompletableFuture

**测试代码**:
```java
CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
    log.info("CompletableFuture 执行");
    // 数据库操作
    return "完成";
});
```

**测试 Endpoint**: `GET /api/async-trace/executor-service`

**结果**: ✅ CompletableFuture 中的操作能传播 Trace ID

---

### 4. 手动创建 Thread

**测试代码**:
```java
// 测试1: Lambda 方式
Thread thread1 = new Thread(() -> {
    log.info("手动 Thread 执行");
    // 数据库操作
});
thread1.start();

// 测试2: Runnable 接口
Runnable runnable = () -> {
    log.info("Runnable 执行");
    // 数据库操作
};
Thread thread2 = new Thread(runnable);
thread2.start();

// 测试3: 继承 Thread 类
class MyThread extends Thread {
    @Override
    public void run() {
        log.info("继承 Thread 执行");
        // 数据库操作
    }
}
MyThread thread3 = new MyThread();
thread3.start();

// 测试4: 多个并发手动线程
Thread[] threads = new Thread[3];
for (int i = 0; i < 3; i++) {
    threads[i] = new Thread(() -> {
        // 数据库操作
    });
    threads[i].start();
}

// 测试5: 嵌套手动线程
Thread parentThread = new Thread(() -> {
    Thread childThread = new Thread(() -> {
        log.info("子线程执行");
        // 数据库操作
    });
    childThread.start();
});
parentThread.start();
```

**测试 Endpoint**: `GET /api/async-trace/manual-thread`

**结果**: ❌ 所有手动创建的线程都无法传播 Trace ID，会创建新的 Transaction

---

## 原理分析

### Sentry 自动传播机制

Sentry 的 Java SDK 通过以下方式实现自动 Trace 传播：

1. **ThreadLocal 上下文管理**
   - Sentry 使用 `ThreadLocal` 存储 Trace 上下文（Transaction、Span、Breadcrumb 等）
   - 每个线程有自己的上下文副本

2. **线程池包装机制**
   - 当使用线程池（ExecutorService、@Async 线程池）时
   - Sentry 会自动包装提交的 Runnable/Callable 任务
   - 在任务执行前，从父线程复制 Trace 上下文到子线程
   - 在任务执行后，清理子线程的 Trace 上下文

3. **工作原理图示**

```
✅ 使用线程池（支持自动传播）:

主线程
  ↓
[提交任务到线程池]
  ↓
Sentry 包装器 → 复制 Trace 上下文
  ↓
工作线程（线程池）→ 获取父线程的 Trace 上下文
  ↓
执行任务 → 所有操作都在同一个 Trace 中


❌ 手动创建线程（不支持自动传播）:

主线程
  ↓
new Thread() → 绕过线程池
  ↓
新线程（手动创建）→ 没有父线程的 Trace 上下文
  ↓
执行任务 → 创建新的 Transaction，Trace 断裂
```

### 为什么手动线程无法传播？

1. **缺少包装层**
   - `new Thread()` 直接创建线程，绕过了 Sentry 的包装机制
   - Sentry 无法拦截 `Thread.start()` 调用

2. **ThreadLocal 隔离**
   - 手动创建的新线程有独立的 ThreadLocal
   - 不会自动继承父线程的 ThreadLocal 值

3. **生命周期管理**
   - 线程池由 Spring/Sentry 管理，可以注入上下文
   - 手动线程完全由开发者控制，Sentry 无法干预

---

## 实际应用建议

### ✅ 推荐做法（自动支持 Trace）

#### 1. 使用 Spring @Async

```java
@Service
public class MyService {

    @Async
    public CompletableFuture<Result> asyncMethod() {
        // ✅ Trace 会自动传播
        log.info("异步操作");
        // 数据库查询、HTTP 调用等
        return CompletableFuture.completedFuture(result);
    }
}
```

**配置**:
```java
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

#### 2. 使用 ExecutorService

```java
@Service
public class MyService {

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public void executeAsync() {
        executor.submit(() -> {
            // ✅ Trace 会自动传播
            log.info("异步任务");
            // 业务逻辑
        });
    }
}
```

**使用 Spring 管理的 Executor**:
```java
@Configuration
public class ExecutorConfig {

    @Bean
    public ExecutorService executorService() {
        return Executors.newFixedThreadPool(10);
    }
}
```

#### 3. 使用 CompletableFuture

```java
public CompletableFuture<Result> asyncOperation() {
    return CompletableFuture.supplyAsync(() -> {
        // ✅ Trace 会自动传播
        log.info("异步操作");
        // 业务逻辑
        return result;
    });
}
```

**使用自定义 Executor**:
```java
@Autowired
private Executor executor;

public CompletableFuture<Result> asyncOperation() {
    return CompletableFuture.supplyAsync(() -> {
        // ✅ Trace 会自动传播
        return result;
    }, executor);
}
```

---

### ⚠️ 不推荐做法（Trace 会丢失）

```java
// ❌ 避免直接创建线程
public void badAsync() {
    new Thread(() -> {
        // ❌ Trace 会丢失
        log.info("异步操作");
        // 业务逻辑
    }).start();
}
```

**问题**:
- Trace 上下文丢失，无法追踪
- 在 Sentry 中会看到断开的 Transaction
- 难以排查问题和性能分析

---

### 🔧 如果必须使用手动线程

如果确实需要手动创建线程，需要手动传播 Trace 上下文：

#### 方案 1: 使用 Sentry API 手动传播

```java
import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import io.sentry.Transaction;
import io.sentry.TracingUtils;

public void manualThreadWithContext() {
    // 获取当前 Transaction
    Transaction transaction = Sentry.getTransaction();

    Thread thread = new Thread(() -> {
        try {
            // 手动设置 Trace 上下文
            // 注意：具体 API 取决于 Sentry 版本
            if (transaction != null) {
                // 设置 Transaction 到新线程
                // 这可能需要使用 TracingUtils 或其他工具
            }

            log.info("手动线程执行");
            // 业务逻辑

        } finally {
            // 清理上下文
        }
    });
    thread.start();
}
```

#### 方案 2: 使用 Runnable 包装器

```java
public class TracePropagationRunnable implements Runnable {
    private final Runnable delegate;
    private final Object traceContext;

    public TracePropagationRunnable(Runnable delegate) {
        this.delegate = delegate;
        // 捕获当前 Trace 上下文
        this.traceContext = captureTraceContext();
    }

    private Object captureTraceContext() {
        // 实现 Trace 上下文捕获逻辑
        return null;
    }

    @Override
    public void run() {
        try {
            // 恢复 Trace 上下文
            restoreTraceContext(traceContext);
            delegate.run();
        } finally {
            // 清理上下文
        }
    }
}

// 使用
Thread thread = new Thread(new TracePropagationRunnable(() -> {
    log.info("手动线程执行");
}));
thread.start();
```

**注意**: 手动传播 Trace 的实现较为复杂，建议优先使用线程池方式。

---

## 性能影响分析

### 线程池 vs 手动线程

| 维度 | 线程池（推荐） | 手动线程（不推荐） |
|-----|--------------|------------------|
| **性能** | ✅ 线程复用，资源高效 | ❌ 每次创建销毁，开销大 |
| **内存** | ✅ 可控的线程数量 | ❌ 无限创建可能导致 OOM |
| **监控** | ✅ 完整的 Trace 链路 | ❌ Trace 断裂，难以监控 |
| **维护性** | ✅ 统一管理，易于维护 | ❌ 分散管理，难以维护 |
| **稳定性** | ✅ 限流、超时控制 | ❌ 无限制，可能雪崩 |

---

## 测试命令

### 启动应用

```bash
mvn spring-boot:run
```

### 测试 Endpoint

```bash
# 测试 Spring @Async
curl http://localhost:8080/api/async-trace/test?username=testuser

# 测试多个 @Async 任务
curl http://localhost:8080/api/async-trace/test-multiple?prefix=batch

# 测试 ExecutorService 和 CompletableFuture
curl http://localhost:8080/api/async-trace/executor-service

# 测试手动创建线程
curl http://localhost:8080/api/async-trace/manual-thread
```

### 在 Sentry 中查看结果

1. 打开 Sentry Dashboard
2. 进入项目 → Performance
3. 查看 Transactions 列表
4. 对比不同 endpoint 的 Trace 情况
   - **自动传播**: 一个 Transaction 包含多个 Span
   - **手动线程**: 多个独立的 Transaction

---

## 关键配置

### application.yml

```yaml
sentry:
  dsn: https://xxxxx@sentry.io/xxxxx
  # 启用性能监控
  traces-sample-rate: 1.0
  # 启用异常捕获
  exception-resolver-order: 2147483647
  # 日志集成
  logging:
    enabled: true
    minimum-event-level: info
    minimum-breadcrumb-level: info

# 数据库连接（使用 P6Spy 支持 JDBC Span）
spring:
  datasource:
    url: jdbc:p6spy:h2:mem:testdb
    driver-class-name: com.p6spy.engine.spy.P6SpyDriver
```

### spy.properties（P6Spy 配置）

```properties
modulelist=com.p6spy.engine.spy.P6SpyFactory
outagedetection=false
outagedetectioninterval=
```

---

## 总结

### 核心结论

1. **Sentry 对线程池方式的异步编程有完美的支持**
   - Spring @Async ✅
   - ExecutorService ✅
   - CompletableFuture ✅

2. **手动创建 Thread 无法自动传播 Trace**
   - 所有 `new Thread()` 方式都会导致 Trace 断裂
   - 需要手动处理，实现复杂

3. **实际开发建议**
   - 优先使用 Spring 的异步机制
   - 避免直接创建线程
   - 利用 Sentry 的自动传播能力

### 最佳实践

```java
// ✅ 推荐
@Async
public CompletableFuture<Result> asyncMethod() {
    // 自动 Trace 传播
    return CompletableFuture.completedFuture(result);
}

// ❌ 不推荐
public void manualThread() {
    new Thread(() -> {
        // Trace 丢失
    }).start();
}
```

### 参考资源

- [Sentry Java SDK Documentation](https://docs.sentry.io/platforms/java/)
- [Spring Boot Async Configuration](https://docs.spring.io/spring-framework/docs/current/reference/html/integration.html#scheduling)
- [CompletableFuture Guide](https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/CompletableFuture.html)

---

**文档版本**: 1.0
**最后更新**: 2026-01-09
**作者**: Claude Code & 用户验证
