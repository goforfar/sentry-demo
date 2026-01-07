package com.example.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.lang.Exception;
import io.sentry.Sentry;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Welcome to Spring Boot 3 Demo!");
        response.put("timestamp", LocalDateTime.now());
        response.put("status", "running");
        return response;
    }

    @GetMapping("/hello")
    public String hello(@RequestParam(defaultValue = "World") String name) {
        return String.format("Hello, %s!", name);
    }

    @GetMapping("/hello/{name}")
    public Map<String, String> helloPath(@PathVariable String name) {
        Map<String, String> response = new HashMap<>();
        response.put("greeting", String.format("Hello, %s!", name));
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("application", "sentry-demo");
        return response;
    }


    @GetMapping("/crash")
    public Map<String, String> crash() {
        try {
            throw new Exception("This is a test.");
        } catch (Exception e) {
            Sentry.captureException(e);
        }
        Map<String, String> response = new HashMap<>();
        return response;
    }
}
