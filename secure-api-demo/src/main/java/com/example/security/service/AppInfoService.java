package com.example.security.service;

import com.example.security.config.SignatureProperties;
import com.example.security.entity.AppInfo;
import com.example.security.util.SignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 应用信息服务接口
 * 支持从数据库或 Redis 中获取和校验应用信息
 *
 * @author Security Architect
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppInfoService {

    private final StringRedisTemplate redisTemplate;
    private final SignatureUtil signatureUtil;
    private final SignatureProperties properties;

    // 模拟数据库存储 (实际项目中应替换为真实的 Repository)
    // private final AppInfoRepository appInfoRepository;

    /**
     * 根据 AppID 获取应用信息
     * 优先从 Redis 缓存获取，未命中则查询数据库
     *
     * @param appId 应用 ID
     * @return 应用信息，不存在则返回 null
     */
    public AppInfo getAppInfo(String appId) {
        if (appId == null || appId.trim().isEmpty()) {
            return null;
        }

        String redisKey = SignatureProperties.APP_INFO_REDIS_PREFIX + appId;
        
        // 尝试从 Redis 获取 (实际项目中这里应该反序列化 JSON)
        // 为了简化示例，这里使用简单字符串存储
        String cachedMasterSecret = redisTemplate.opsForValue().get(redisKey + ":secret");
        
        if (cachedMasterSecret != null) {
            log.debug("从 Redis 缓存命中应用信息：appId={}", appId);
            // 从缓存重建 AppInfo 对象
            String appName = redisTemplate.opsForValue().get(redisKey + ":name");
            String statusStr = redisTemplate.opsForValue().get(redisKey + ":status");
            String versionStr = redisTemplate.opsForValue().get(redisKey + ":version");
            
            return AppInfo.builder()
                    .appId(appId)
                    .masterSecret(cachedMasterSecret)
                    .appName(appName)
                    .status(statusStr != null ? Integer.parseInt(statusStr) : 1)
                    .secretVersion(versionStr != null ? Integer.parseInt(versionStr) : 1)
                    .build();
        }

        // Redis 未命中，查询数据库 (模拟)
        log.debug("Redis 未命中，查询数据库：appId={}", appId);
        AppInfo appInfo = queryFromDatabase(appId);
        
        if (appInfo != null && appInfo.isValid()) {
            // 写入 Redis 缓存
            cacheAppInfo(appInfo);
        }
        
        return appInfo;
    }

    /**
     * 从数据库查询应用信息 (模拟实现)
     * 实际项目中应替换为真实的数据库查询
     *
     * @param appId 应用 ID
     * @return 应用信息
     */
    public AppInfo queryFromDatabase(String appId) {
        // TODO: 替换为真实的数据库查询
        // return appInfoRepository.findByAppId(appId);
        
        // 模拟数据 - 仅用于演示
        if ("test-app-001".equals(appId)) {
            return AppInfo.builder()
                    .appId(appId)
                    .masterSecret("my-super-secret-master-key-2024")
                    .appName("测试应用")
                    .description("用于测试的应用")
                    .status(1)
                    .secretVersion(1)
                    .ipWhitelist("")
                    .build();
        }
        
        if ("demo-app".equals(appId)) {
            return AppInfo.builder()
                    .appId(appId)
                    .masterSecret("demo-master-secret-key")
                    .appName("演示应用")
                    .description("用于演示的应用")
                    .status(1)
                    .secretVersion(1)
                    .ipWhitelist("127.0.0.1,0:0:0:0:0:0:0:1")
                    .build();
        }
        
        return null;
    }

    /**
     * 将应用信息缓存到 Redis
     *
     * @param appInfo 应用信息
     */
    public void cacheAppInfo(AppInfo appInfo) {
        if (appInfo == null || appInfo.getAppId() == null) {
            return;
        }

        String redisKey = SignatureProperties.APP_INFO_REDIS_PREFIX + appInfo.getAppId();
        String secretKey = redisKey + ":secret";
        
        // 缓存 24 小时
        Duration ttl = Duration.ofHours(24);
        
        redisTemplate.opsForValue().set(secretKey, appInfo.getMasterSecret(), ttl);
        redisTemplate.opsForValue().set(redisKey + ":name", appInfo.getAppName(), ttl);
        redisTemplate.opsForValue().set(redisKey + ":status", String.valueOf(appInfo.getStatus()), ttl);
        redisTemplate.opsForValue().set(redisKey + ":version", String.valueOf(appInfo.getSecretVersion()), ttl);
        
        log.debug("应用信息已缓存到 Redis：appId={}", appInfo.getAppId());
    }

    /**
     * 验证应用是否有效
     *
     * @param appId 应用 ID
     * @return 是否有效
     */
    public boolean isValidApp(String appId) {
        AppInfo appInfo = getAppInfo(appId);
        return appInfo != null && appInfo.isValid();
    }

    /**
     * 获取派生密钥
     *
     * @param appId 应用 ID
     * @return 派生密钥 (Base64 编码)，如果应用不存在则返回 null
     */
    public String getDerivedSecret(String appId) {
        AppInfo appInfo = getAppInfo(appId);
        if (appInfo == null || !appInfo.isValid()) {
            return null;
        }

        int version = appInfo.getSecretVersion() != null ? appInfo.getSecretVersion() : 1;
        return signatureUtil.deriveSecret(appInfo.getMasterSecret(), appId, version);
    }

    /**
     * 清除应用信息缓存
     *
     * @param appId 应用 ID
     */
    public void evictCache(String appId) {
        String redisKey = SignatureProperties.APP_INFO_REDIS_PREFIX + appId;
        redisTemplate.delete(redisKey + ":secret");
        redisTemplate.delete(redisKey + ":name");
        redisTemplate.delete(redisKey + ":status");
        redisTemplate.delete(redisKey + ":version");
        log.debug("应用信息缓存已清除：appId={}", appId);
    }

    /**
     * 注册新应用 (模拟)
     * 实际项目中应调用数据库插入
     *
     * @param appInfo 应用信息
     * @return 注册后的应用信息
     */
    public AppInfo registerApp(AppInfo appInfo) {
        // TODO: 替换为真实的数据库插入
        // appInfo = appInfoRepository.save(appInfo);
        
        // 生成随机 Master Secret (如果未提供)
        if (appInfo.getMasterSecret() == null || appInfo.getMasterSecret().isEmpty()) {
            String randomSecret = java.util.UUID.randomUUID().toString() 
                                + java.util.UUID.randomUUID().toString();
            appInfo.setMasterSecret(randomSecret.replace("-", ""));
        }
        
        if (appInfo.getSecretVersion() == null) {
            appInfo.setSecretVersion(1);
        }
        
        if (appInfo.getStatus() == null) {
            appInfo.setStatus(1);
        }
        
        // 缓存到 Redis
        cacheAppInfo(appInfo);
        
        log.info("新应用注册成功：appId={}, appName={}", appInfo.getAppId(), appInfo.getAppName());
        return appInfo;
    }
}
