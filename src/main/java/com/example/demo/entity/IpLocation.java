package com.example.demo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * IP地址位置信息实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IpLocation {
    /**
     * 查询的IP地址
     */
    private String query;

    /**
     * 国家
     */
    private String country;

    /**
     * 国家代码
     */
    private String countryCode;

    /**
     * 地区/省份
     */
    private String region;

    /**
     * 地区名称
     */
    private String regionName;

    /**
     * 城市
     */
    private String city;

    /**
     * 邮政编码
     */
    private String zip;

    /**
     * 纬度
     */
    private Double lat;

    /**
     * 经度
     */
    private Double lon;

    /**
     * 时区
     */
    private String timezone;

    /**
     * ISP运营商
     */
    private String isp;

    /**
     * 组织机构
     */
    private String org;

    /**
     * AS号码
     */
    private String as;

    /**
     * 查询状态
     */
    private String status;

    /**
     * 错误消息
     */
    private String message;
}
