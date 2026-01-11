package com.example.demo.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置类
 *
 * 通过 RestTemplateBuilder 创建的 RestTemplate 会被 Sentry 自动注入拦截器，
 * 为每个外部 HTTP 请求创建 HTTP Client Span，并在请求头中注入 sentry-trace
 * 以支持分布式追踪。
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 配置 RestTemplate Bean
     *
     * 使用 RestTemplateBuilder 创建 RestTemplate 实例：
     * - Sentry 会自动注入拦截器
     * - 每次调用 restTemplate.exchange / getForObject 等方法时，自动创建 Span
     * - 自动添加 sentry-trace 请求头以支持分布式追踪
     *
     * @param restTemplateBuilder Spring Boot 提供的 RestTemplate 构建器
     * @return 配置好的 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder restTemplateBuilder) {
        return restTemplateBuilder
                .build();
    }
}
