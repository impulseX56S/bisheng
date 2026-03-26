package com.example.security.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 应用信息实体类
 * 对应数据库中的应用注册信息
 *
 * @author Security Architect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppInfo {

    /**
     * 应用 ID (App ID)
     */
    private String appId;

    /**
     * 主密钥 (Master Secret) - 仅用于派生，不直接用于签名
     * 实际存储时应加密存储
     */
    private String masterSecret;

    /**
     * 派生密钥版本
     */
    private Integer secretVersion;

    /**
     * 应用名称
     */
    private String appName;

    /**
     * 应用描述
     */
    private String description;

    /**
     * 状态：1-启用，0-禁用
     */
    private Integer status;

    /**
     * IP 白名单 (逗号分隔)
     */
    private String ipWhitelist;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否有效
     */
    public boolean isValid() {
        return this.status != null && this.status == 1;
    }
}
