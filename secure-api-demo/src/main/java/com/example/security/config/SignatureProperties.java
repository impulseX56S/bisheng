package com.example.security.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全配置属性
 *
 * @author Security Architect
 */
@Data
@Component
@ConfigurationProperties(prefix = "security.signature")
public class SignatureProperties {

    /**
     * 是否启用签名验证
     */
    private boolean enabled = true;

    /**
     * 签名时间戳有效时间 (秒)
     */
    private long timestampWindow = 300;

    /**
     * 随机数 (nonce) 在 Redis 中的过期时间 (秒)
     */
    private long nonceExpire = 300;

    /**
     * 签名算法：HMACSHA256, HMACSHA1, MD5
     */
    private String algorithm = "HMACSHA256";

    /**
     * 派生密钥的盐
     */
    private String deriveSalt = "default-salt";

    /**
     * 派生密钥的迭代次数
     */
    private int deriveIterations = 1000;

    /**
     * Redis 中存储 nonce 的前缀
     */
    public static final String NONCE_REDIS_PREFIX = "api:nonce:";

    /**
     * Redis 中存储 AppInfo 的前缀
     */
    public static final String APP_INFO_REDIS_PREFIX = "api:app:";
}
