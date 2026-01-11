package com.example.demo.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 错误率模拟测试控制器
 * 用于测试Sentry的错误率统计功能
 */
@Slf4j
@RestController
@RequestMapping("/api/error-rate")
@RequiredArgsConstructor
public class ErrorRateController {

    private final RestTemplate restTemplate;
    private final String BASE_URL = "http://localhost:8080";

    /**
     * 模拟单次请求 - 根据参数决定是否抛出异常
     * GET /api/error-rate/simulate?shouldError=true
     *
     * @param shouldError 是否应该抛出异常
     * @return 成功或失败的响应
     */
    @GetMapping("/simulate")
    public ResponseEntity<Map<String, Object>> simulateSingleRequest(
            @RequestParam boolean shouldError) {

        if (shouldError) {
            log.error("模拟错误：故意抛出异常");
            throw new RuntimeException("模拟的错误异常 - 用于测试Sentry错误率统计");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "请求成功");
        response.put("timestamp", System.currentTimeMillis());

        log.info("模拟成功：请求正常处理");
        return ResponseEntity.ok(response);
    }

    /**
     * 错误率测试 - 执行指定数量的请求，其中部分请求会发生错误
     * GET /api/error-rate/test?totalRequests=100&errorRate=0.35
     *
     * @param totalRequests 总请求数，默认100
     * @param errorRate     错误率（0.0-1.0），默认0.35表示35%错误率
     * @return 测试结果统计
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testErrorRate(
            @RequestParam(defaultValue = "100") int totalRequests,
            @RequestParam(defaultValue = "0.35") double errorRate) {

        log.info("开始错误率测试：总请求数={}, 目标错误率={}%", totalRequests, errorRate * 100);

        int actualErrorCount = 0;
        int actualSuccessCount = 0;
        int targetErrorCount = (int) Math.round(totalRequests * errorRate);

        Map<String, Object> result = new HashMap<>();
        result.put("totalRequests", totalRequests);
        result.put("targetErrorRate", errorRate);
        result.put("targetErrorCount", targetErrorCount);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            try {
                // 使用确定性算法：前 targetErrorCount 次请求为错误，后面为成功
                // 这样可以精确控制错误数量
                boolean shouldError = (i < targetErrorCount);

                log.info("执行第 {}/{} 次请求，预期结果: {}",
                        i + 1, totalRequests, shouldError ? "错误" : "成功");

                // 直接调用本地方法，避免网络开销
                if (shouldError) {
                    simulateSingleRequest(true);
                } else {
                    simulateSingleRequest(false);
                }

                actualSuccessCount++;

                // 添加小延迟避免过快
                if (i % 10 == 0) {
                    Thread.sleep(10);
                }

            } catch (Exception e) {
                actualErrorCount++;
                log.error("第 {} 次请求发生异常（预期内）: {}", i + 1, e.getMessage());
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 计算实际错误率
        double actualErrorRate = (double) actualErrorCount / totalRequests;

        result.put("actualErrorCount", actualErrorCount);
        result.put("actualSuccessCount", actualSuccessCount);
        result.put("actualErrorRate", actualErrorRate);
        result.put("actualErrorRatePercent", String.format("%.2f%%", actualErrorRate * 100));
        result.put("duration", duration + "ms");
        result.put("status", "completed");

        log.info("错误率测试完成：总数={}, 成功={}, 错误={}, 实际错误率={}%, 耗时={}ms",
                totalRequests, actualSuccessCount, actualErrorCount,
                String.format("%.2f", actualErrorRate * 100), duration);

        return ResponseEntity.ok(result);
    }

    /**
     * 快速错误率测试 - 随机分配错误（适用于大批量测试）
     * GET /api/error-rate/test-random?totalRequests=100&errorRate=0.35
     */
    @GetMapping("/test-random")
    public ResponseEntity<Map<String, Object>> testErrorRateRandom(
            @RequestParam(defaultValue = "100") int totalRequests,
            @RequestParam(defaultValue = "0.35") double errorRate) {

        log.info("开始随机错误率测试：总请求数={}, 目标错误率={}%", totalRequests, errorRate * 100);

        Random random = new Random();
        int actualErrorCount = 0;
        int actualSuccessCount = 0;

        Map<String, Object> result = new HashMap<>();
        result.put("totalRequests", totalRequests);
        result.put("targetErrorRate", errorRate);
        result.put("testType", "random");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < totalRequests; i++) {
            try {
                // 使用随机数决定是否错误
                boolean shouldError = random.nextDouble() < errorRate;

                if (shouldError) {
                    simulateSingleRequest(true);
                } else {
                    simulateSingleRequest(false);
                }

                actualSuccessCount++;

            } catch (Exception e) {
                actualErrorCount++;
                if (actualErrorCount <= 10) { // 只记录前10个错误避免日志过多
                    log.error("请求发生异常: {}", e.getMessage());
                }
            }
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        double actualErrorRate = (double) actualErrorCount / totalRequests;

        result.put("actualErrorCount", actualErrorCount);
        result.put("actualSuccessCount", actualSuccessCount);
        result.put("actualErrorRate", actualErrorRate);
        result.put("actualErrorRatePercent", String.format("%.2f%%", actualErrorRate * 100));
        result.put("duration", duration + "ms");
        result.put("status", "completed");

        log.info("随机错误率测试完成：总数={}, 成功={}, 错误={}, 实际错误率={}%, 耗时={}ms",
                totalRequests, actualSuccessCount, actualErrorCount,
                String.format("%.2f", actualErrorRate * 100), duration);

        return ResponseEntity.ok(result);
    }
}
