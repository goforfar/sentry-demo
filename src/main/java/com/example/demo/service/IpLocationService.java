package com.example.demo.service;

import com.example.demo.entity.IpLocation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * IP地址位置查询服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IpLocationService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 免费IP查询API
     */
    private static final String IP_API_URL = "http://ip-api.com/json/";

    /**
     * 根据IP地址查询地理位置信息
     *
     * @param ip IP地址，如果为空则查询当前客户端IP
     * @return IP位置信息
     */
    public IpLocation getIpLocation(String ip) {
        // 如果IP为空，返回错误信息
        if (ip == null || ip.trim().isEmpty()) {
            IpLocation errorLocation = new IpLocation();
            errorLocation.setStatus("fail");
            errorLocation.setMessage("IP地址不能为空");
            return errorLocation;
        }

        // 验证IP地址格式
        if (!isValidIpAddress(ip)) {
            IpLocation errorLocation = new IpLocation();
            errorLocation.setStatus("fail");
            errorLocation.setMessage("无效的IP地址格式: " + ip);
            return errorLocation;
        }

        try {
            String url = IP_API_URL + ip;
            log.info("查询IP地址: {}, URL: {}", ip, url);

            // 调用免费IP查询API
            String response = restTemplate.getForObject(url, String.class);
            log.info("IP查询API响应: {}", response);

            // 解析响应JSON
            JsonNode jsonNode = objectMapper.readTree(response);

            IpLocation location = new IpLocation();
            location.setQuery(ip);
            location.setStatus(jsonNode.path("status").asText());
            location.setCountry(jsonNode.path("country").asText());
            location.setCountryCode(jsonNode.path("countryCode").asText());
            location.setRegion(jsonNode.path("region").asText());
            location.setRegionName(jsonNode.path("regionName").asText());
            location.setCity(jsonNode.path("city").asText());
            location.setZip(jsonNode.path("zip").asText());
            location.setLat(jsonNode.path("lat").asDouble());
            location.setLon(jsonNode.path("lon").asDouble());
            location.setTimezone(jsonNode.path("timezone").asText());
            location.setIsp(jsonNode.path("isp").asText());
            location.setOrg(jsonNode.path("org").asText());
            location.setAs(jsonNode.path("as").asText());

            // 如果API返回失败状态
            if ("fail".equals(location.getStatus())) {
                location.setMessage(jsonNode.path("message").asText("查询失败"));
            }

            return location;

        } catch (Exception e) {
            log.error("查询IP地址失败: {}", ip, e);
            IpLocation errorLocation = new IpLocation();
            errorLocation.setQuery(ip);
            errorLocation.setStatus("fail");
            errorLocation.setMessage("查询IP地址时发生异常: " + e.getMessage());
            return errorLocation;
        }
    }

    /**
     * 验证IP地址格式是否正确
     *
     * @param ip IP地址
     * @return 是否有效
     */
    private boolean isValidIpAddress(String ip) {
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (UnknownHostException e) {
            return false;
        }
    }
}
