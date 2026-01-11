package com.example.demo.util;

import io.sentry.Breadcrumb;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Trace 上下文工具类
 *
 * 用于正确获取和传播 Sentry Trace ID，特别是在异步场景中。
 */
@Slf4j
public class TraceContext {

    private static final String TRACE_ID_KEY = "sentry-trace-id";
    private static final String TRANSACTION_NAME_KEY = "sentry-transaction";
    private static final ThreadLocal<String> traceIdHolder = new ThreadLocal<>();

    /**
     * 生成或获取当前 Trace ID
     *
     * 如果有活跃的 Transaction，返回其 Trace ID；
     * 否则生成一个新的 Trace ID
     *
     * @return Trace ID
     */
    public static String getCurrentTraceId() {
        try {
            // 尝试从 ThreadLocal 获取
            String traceId = traceIdHolder.get();
            if (traceId != null) {
                return traceId;
            }

            // 如果没有，生成一个新的
            traceId = UUID.randomUUID().toString().replace("-", "");
            traceIdHolder.set(traceId);
            return traceId;
        } catch (Exception e) {
            log.debug("无法获取 Trace ID: {}", e.getMessage());
            return "N/A";
        }
    }

    /**
     * 设置当前 Trace ID
     *
     * @param traceId 要设置的 Trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            traceIdHolder.set(traceId);
        }
    }

    /**
     * 获取当前 Transaction 名称
     *
     * @return Transaction 名称或 "N/A"
     */
    public static String getCurrentTransactionName() {
        // 简化实现，返回固定的名称
        return "active-transaction";
    }

    /**
     * 检查当前是否在 Transaction 中
     *
     * @return 是否在 Transaction 中
     */
    public static boolean isInTransaction() {
        return traceIdHolder.get() != null;
    }

    /**
     * 将当前 Trace 上下文保存到 MDC
     *
     * 这样可以在日志中自动包含 trace_id
     */
    public static void saveToMDC() {
//        String traceId = getCurrentTraceId();
//        if (!"N/A".equals(traceId)) {
//            MDC.put(TRACE_ID_KEY, traceId);
//        }
//        String transactionName = getCurrentTransactionName();
//        if (!"N/A".equals(transactionName)) {
//            MDC.put(TRANSACTION_NAME_KEY, transactionName);
//        }
    }

    /**
     * 从 MDC 清除 Trace 上下文
     */
    public static void clearMDC() {
        MDC.remove(TRACE_ID_KEY);
        MDC.remove(TRANSACTION_NAME_KEY);
        traceIdHolder.remove();
    }

    /**
     * 添加带有 Trace 信息的 Breadcrumb
     *
     * @param message Breadcrumb 消息
     * @param category Breadcrumb 类别
     * @param data 额外数据
     */
    public static void addBreadcrumb(String message, String category, Map<String, String> data) {
        Breadcrumb breadcrumb = new Breadcrumb();
        breadcrumb.setMessage(message);
        breadcrumb.setCategory(category);
        if (data != null && !data.isEmpty()) {
            breadcrumb.setData("trace_id", getCurrentTraceId());
            data.forEach(breadcrumb::setData);
        }
        Sentry.addBreadcrumb(breadcrumb);
    }

    /**
     * 添加带有 Trace 信息的 Breadcrumb
     */
    public static void addBreadcrumb(String message, String category) {
        addBreadcrumb(message, category, null);
    }

    /**
     * 打印当前 Trace 信息（用于调试）
     */
    public static void logTraceInfo(String location) {
        log.info("🔍 [{}] Trace ID: {}, Transaction: {}, In Transaction: {}",
            location,
            getCurrentTraceId(),
            getCurrentTransactionName(),
            isInTransaction()
        );
    }

    /**
     * 获取可用于传递的 Trace Header
     *
     * 这个值可以传递给其他服务以关联 Trace
     *
     * @return sentry-trace header 值
     */
    public static String getTraceHeader() {
        try {
            if (isInTransaction()) {
                String traceId = getCurrentTraceId();
                // 简化的实现，返回 trace_id
                return traceId;
            }
            return null;
        } catch (Exception e) {
            log.debug("无法获取 Trace Header: {}", e.getMessage());
            return null;
        }
    }
}
