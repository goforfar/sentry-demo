package com.example.demo.controller;

import com.example.demo.entity.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {

    private final RestTemplateBuilder restTemplateBuilder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String BASE_URL = "http://8c7bbbba95c1025975e548cee86dfadc.nebulab.app";

    /**
     * 随机访问项目的其他endpoint
     * GET /api/test/testSend?count=5
     */
    @GetMapping("/testSend")
    public ResponseEntity<Map<String, Object>> testSend(@RequestParam(defaultValue = "5") int count) {
        log.info("收到随机测试请求，将随机访问 {} 个endpoint", count);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> responses = new ArrayList<>();
        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < count; i++) {
            try {
                ApiRequest apiRequest = getRandomApiRequest();
                log.info("第 {}/{} 次请求: {} {}", i + 1, count, apiRequest.method, apiRequest.url);

                Map<String, Object> response = sendRequest(apiRequest);
                responses.add(response);

                if ((int) response.get("statusCode") < 300) {
                    successCount++;
                } else {
                    failureCount++;
                }

                // 随机延迟 100-500ms
                Thread.sleep(new Random().nextInt(400) + 100);

            } catch (Exception e) {
                log.error("请求失败", e);
                
                failureCount++;
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                responses.add(errorResponse);
            }
        }

        result.put("totalRequests", count);
        result.put("successCount", successCount);
        result.put("failureCount", failureCount);
        result.put("responses", responses);

        return ResponseEntity.ok(result);
    }

    /**
     * 只访问会导致错误的endpoint
     * GET /api/test/testSendError?count=5
     */
    @GetMapping("/testSendError")
    public ResponseEntity<Map<String, Object>> testSendError(@RequestParam(defaultValue = "5") int count) {
        log.info("收到错误测试请求，将访问 {} 个会导致错误的endpoint", count);

        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> responses = new ArrayList<>();
        int errorCount = 0;

        for (int i = 0; i < count; i++) {
            try {
                ApiRequest apiRequest = getErrorApiRequest();
                log.info("第 {}/{} 次错误测试: {} {}", i + 1, count, apiRequest.method, apiRequest.url);

                Map<String, Object> response = sendRequest(apiRequest);
                responses.add(response);

                int statusCode = (int) response.get("statusCode");
                if (statusCode >= 400 || response.containsKey("error")) {
                    errorCount++;
                }

                // 随机延迟 100-500ms
                Thread.sleep(new Random().nextInt(400) + 100);

            } catch (Exception e) {
                log.error("请求失败", e);
                
                errorCount++;
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", e.getMessage());
                responses.add(errorResponse);
            }
        }

        result.put("totalRequests", count);
        result.put("errorCount", errorCount);
        result.put("successRate", String.format("%.2f%%", (errorCount * 100.0 / count)));
        result.put("responses", responses);

        return ResponseEntity.ok(result);
    }

    /**
     * 发送HTTP请求
     * 使用 RestTemplateBuilder 创建的 RestTemplate 会被 Sentry 自动注入拦截器
     * 每次请求会自动创建 HTTP Client Span，并在请求头中注入 sentry-trace 以支持分布式追踪
     */
    private Map<String, Object> sendRequest(ApiRequest apiRequest) {
        Map<String, Object> response = new HashMap<>();
        long startTime = System.currentTimeMillis();

        try {
            // 使用 RestTemplateBuilder 创建 RestTemplate，Sentry 会自动注入拦截器
            RestTemplate restTemplate = restTemplateBuilder.build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<?> entity;
            if (apiRequest.body != null) {
                entity = new HttpEntity<>(apiRequest.body, headers);
            } else {
                entity = new HttpEntity<>(headers);
            }

            ResponseEntity<String> httpResponse;
            String fullUrl = BASE_URL + apiRequest.url;

            switch (apiRequest.method.toUpperCase()) {
                case "GET":
                    httpResponse = restTemplate.exchange(
                            fullUrl,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );
                    break;
                case "POST":
                    httpResponse = restTemplate.exchange(
                            fullUrl,
                            HttpMethod.POST,
                            entity,
                            String.class
                    );
                    break;
                case "PUT":
                    httpResponse = restTemplate.exchange(
                            fullUrl,
                            HttpMethod.PUT,
                            entity,
                            String.class
                    );
                    break;
                case "DELETE":
                    httpResponse = restTemplate.exchange(
                            fullUrl,
                            HttpMethod.DELETE,
                            entity,
                            String.class
                    );
                    break;
                default:
                    throw new IllegalArgumentException("不支持的HTTP方法: " + apiRequest.method);
            }

            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;

            response.put("method", apiRequest.method);
            response.put("url", apiRequest.url);
            response.put("fullUrl", fullUrl);
            response.put("statusCode", httpResponse.getStatusCode().value());
            response.put("duration", duration + "ms");
            response.put("responseBody", httpResponse.getBody());

            log.info("响应: 状态码={}, 耗时={}ms", httpResponse.getStatusCode().value(), duration);

        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            response.put("method", apiRequest.method);
            response.put("url", apiRequest.url);
            response.put("fullUrl", BASE_URL + apiRequest.url);
            response.put("statusCode", 500);
            response.put("duration", (endTime - startTime) + "ms");
            response.put("error", e.getMessage());
            log.error("请求异常: {} {}", apiRequest.method, apiRequest.url, e);
        }

        return response;
    }

    /**
     * 随机获取一个API请求
     */
    private ApiRequest getRandomApiRequest() {
        Random random = new Random();
        int choice = random.nextInt(22); // 0-21

        return switch (choice) {
            // 基础接口
            case 0 -> new ApiRequest("GET", "/", null);
            case 1 -> new ApiRequest("GET", "/hello?name=RandomUser", null);
            case 2 -> new ApiRequest("GET", "/health", null);
            case 3 -> new ApiRequest("GET", "/crash", null);
            case 4 -> new ApiRequest("GET", "/crash2", null);
            case 5 -> new ApiRequest("GET", "/crash3?did=" + random.nextBoolean(), null);

            // 用户查询接口
            case 6 -> new ApiRequest("GET", "/api/users", null);
            case 7 -> new ApiRequest("GET", "/api/users/" + (random.nextInt(100) + 1), null);
            case 8 -> new ApiRequest("GET", "/api/users/by-username/user_" + random.nextInt(1000), null);

            // 用户创建接口
            case 9 -> new ApiRequest("POST", "/api/users", generateRandomUser(random));
            case 10 -> new ApiRequest("POST", "/api/users/validate", generateRandomUser(random));

            // 批量操作
            case 11 -> new ApiRequest("POST", "/api/users/batch/success",
                    List.of(generateRandomUser(random), generateRandomUser(random), generateRandomUser(random)));

            // 事务回滚测试（故意触发失败）
            case 12 -> {
                User user1 = generateRandomUser(random);
                User user2 = generateRandomUser(random);
                user2.setUsername(user1.getUsername()); // 故意重复username
                yield new ApiRequest("POST", "/api/users/batch/rollback", List.of(user1, user2));
            }

            case 13 -> new ApiRequest("POST", "/api/users/runtime-error", generateErrorUser(random));

            // 复杂事务
            case 14 -> new ApiRequest("POST", "/api/users/complex-transaction", null);

            // 超时测试
            case 15 -> new ApiRequest("POST", "/api/users/timeout", generateRandomUser(random));

            // 更新和删除
            case 16 -> new ApiRequest("PUT", "/api/users/" + (random.nextInt(100) + 1), generateRandomUser(random));
            case 17 -> new ApiRequest("DELETE", "/api/users/" + (random.nextInt(100) + 1), null);

            // 并发测试
            case 18 -> new ApiRequest("POST", "/api/users/" + (random.nextInt(100) + 1) + "/concurrent?email=test@example.com", null);

            // 业务验证异常
            case 19 -> {
                User underageUser = generateRandomUser(random);
                underageUser.setAge(15); // 故意设置年龄小于18
                yield new ApiRequest("POST", "/api/users/validate", underageUser);
            }

            case 20 -> {
                User elderlyUser = generateRandomUser(random);
                elderlyUser.setAge(150); // 故意设置年龄大于120
                yield new ApiRequest("POST", "/api/users/validate", elderlyUser);
            }

            // 默认：查询所有用户
            default -> new ApiRequest("GET", "/api/users", null);
        };
    }

    /**
     * 生成随机用户数据
     */
    private User generateRandomUser(Random random) {
        String username = "user_" + random.nextInt(10000);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPhone("1" + (300000000 + random.nextInt(700000000)));
        user.setAge(18 + random.nextInt(50)); // 18-67岁
        user.setActive(random.nextBoolean());
        return user;
    }

    /**
     * 生成会触发运行时异常的用户（username包含error）
     */
    private User generateErrorUser(Random random) {
        String username = "error_user_" + random.nextInt(10000);
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPhone("1" + (300000000 + random.nextInt(700000000)));
        user.setAge(18 + random.nextInt(50));
        user.setActive(random.nextBoolean());
        return user;
    }

    /**
     * 获取一个会导致错误的API请求
     */
    private ApiRequest getErrorApiRequest() {
        Random random = new Random();
        int choice = random.nextInt(14); // 0-13，共14种错误场景

        return switch (choice) {
            // 基础异常接口
            case 0 -> new ApiRequest("GET", "/crash", null);
            case 1 -> new ApiRequest("GET", "/crash2", null);
            case 2 -> new ApiRequest("GET", "/crash3?did=true", null);

            // 查询不存在的用户
            case 3 -> new ApiRequest("GET", "/api/users/99999", null);
            case 4 -> new ApiRequest("GET", "/api/users/by-username/nonexistent_user_12345", null);

            // 更新不存在的用户
            case 5 -> new ApiRequest("PUT", "/api/users/88888", generateRandomUser(random));

            // 删除不存在的用户
            case 6 -> new ApiRequest("DELETE", "/api/users/77777", null);

            // 事务回滚 - username重复
            case 7 -> {
                User user1 = generateRandomUser(random);
                User user2 = generateRandomUser(random);
                user2.setUsername(user1.getUsername()); // 故意重复username
                yield new ApiRequest("POST", "/api/users/batch/rollback", List.of(user1, user2));
            }

            // 事务回滚 - email重复
            case 8 -> {
                User user1 = generateRandomUser(random);
                User user2 = generateRandomUser(random);
                user2.setEmail(user1.getEmail()); // 故意重复email
                yield new ApiRequest("POST", "/api/users/batch/rollback", List.of(user1, user2));
            }

            // 运行时异常
            case 9 -> new ApiRequest("POST", "/api/users/runtime-error", generateErrorUser(random));

            // 业务验证 - 年龄太小
            case 10 -> {
                User underageUser = generateRandomUser(random);
                underageUser.setAge(15); // 故意设置年龄小于18
                yield new ApiRequest("POST", "/api/users/validate", underageUser);
            }

            // 业务验证 - 年龄太大
            case 11 -> {
                User elderlyUser = generateRandomUser(random);
                elderlyUser.setAge(150); // 故意设置年龄大于120
                yield new ApiRequest("POST", "/api/users/validate", elderlyUser);
            }

            // 超时测试
            case 12 -> new ApiRequest("POST", "/api/users/timeout", generateRandomUser(random));

            // 并发冲突
            case 13 -> new ApiRequest("POST", "/api/users/1/concurrent?email=conflict@example.com", null);

            // 默认：运行时异常
            default -> new ApiRequest("POST", "/api/users/runtime-error", generateErrorUser(random));
        };
    }

    /**
     * API请求封装类
     */
    private static class ApiRequest {
        String method;
        String url;
        Object body;

        ApiRequest(String method, String url, Object body) {
            this.method = method;
            this.url = url;
            this.body = body;
        }
    }
}
