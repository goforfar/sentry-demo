package com.example.demo.controller;

import com.example.demo.entity.IpLocation;
import com.example.demo.service.IpLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * IP地址位置查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/ip")
@RequiredArgsConstructor
public class IpLocationController {

    private final IpLocationService ipLocationService;

    /**
     * 根据IP地址查询地理位置信息
     *
     * 示例请求:
     * GET /api/ip/location?ip=8.8.8.8
     * GET /api/ip/location?ip=114.114.114.114
     *
     * @param ip IP地址（必填）
     * @return IP地址对应的地理位置信息
     */
    @GetMapping("/location")
    public ResponseEntity<IpLocation> getIpLocation(@RequestParam("ip") String ip) {
        log.info("收到IP地址查询请求: {}", ip);

        IpLocation location = ipLocationService.getIpLocation(ip);

        if ("fail".equals(location.getStatus())) {
            log.warn("IP地址查询失败: {}, 原因: {}", ip, location.getMessage());
            return ResponseEntity.badRequest().body(location);
        }

        log.info("IP地址查询成功: {} -> {}, {}, {}", ip, location.getCountry(), location.getRegionName(), location.getCity());
        return ResponseEntity.ok(location);
    }
}
