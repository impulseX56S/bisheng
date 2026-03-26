package com.example.security.service;

import com.example.security.config.SignatureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 防重放攻击服务
 * 使用 Redis 存储已使用的 nonce，防止请求被重复提交
 *
 * @author Security Architect
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayAttackService {

    private final StringRedisTemplate redisTemplate;
    private final SignatureProperties properties;

    /**
     * 检查并记录 Nonce
     * 如果 Nonce 已存在，说明是重放攻击，返回 false
     * 如果 Nonce 不存在，记录到 Redis 并返回 true
     *
     * @param appId     应用 ID
     * @param nonce     随机数
     * @param timestamp 时间戳 (用于计算过期时间)
     * @return 是否验证通过 (true=首次使用，false=重放攻击)
     */
    public boolean checkAndRecordNonce(String appId, String nonce, Long timestamp) {
        String redisKey = buildNonceKey(appId, nonce);
        
        // 尝试设置 key，如果 key 已存在则返回 false
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, timestamp.toString(), 
                            properties.getNonceExpire(), TimeUnit.SECONDS);
        
        if (Boolean.TRUE.equals(isNew)) {
            log.debug("Nonce 验证通过：appId={}, nonce={}", appId, nonce);
            return true;
        } else {
            log.warn("检测到重放攻击！appId={}, nonce={}, timestamp={}", appId, nonce, timestamp);
            return false;
        }
    }

    /**
     * 验证时间戳是否在有效窗口内
     *
     * @param timestamp 客户端时间戳 (毫秒)
     * @return 是否有效
     */
    public boolean validateTimestamp(Long timestamp) {
        if (timestamp == null) {
            return false;
        }

        long currentTimeMillis = System.currentTimeMillis();
        long diffSeconds = Math.abs(currentTimeMillis - timestamp) / 1000;
        
        boolean isValid = diffSeconds <= properties.getTimestampWindow();
        
        if (!isValid) {
            log.warn("时间戳超出有效窗口！当前时间={}, 客户端时间={}, 差值={}秒", 
                     currentTimeMillis, timestamp, diffSeconds);
        }
        
        return isValid;
    }

    /**
     * 构建 Nonce 的 Redis Key
     */
    private String buildNonceKey(String appId, String nonce) {
        return SignatureProperties.NONCE_REDIS_PREFIX + appId + ":" + nonce;
    }

    /**
     * 批量清理过期的 Nonce (可选的维护任务)
     * 由于 Redis 设置了 TTL，通常不需要手动清理
     */
    public void cleanupExpiredNonces() {
        // Redis 会自动处理 TTL，此方法仅用于监控或统计
        log.debug("Nonce 清理任务执行 (实际由 Redis TTL 自动处理)");
    }
}
