package com.example.security.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.security.config.SignatureProperties;
import com.example.security.entity.AppInfo;
import com.example.security.mapper.AppInfoMapper;
import com.example.security.util.SignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 应用信息服务
 * 支持从数据库或 Redis 中获取和校验应用信息
 * 使用 MyBatis-Plus + Redis 缓存
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
    private final AppInfoMapper appInfoMapper;

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
        
        // 尝试从 Redis 获取 (JSON 字符串)
        String cachedJson = redisTemplate.opsForValue().get(redisKey);
        
        if (cachedJson != null && !cachedJson.isEmpty()) {
            log.debug("从 Redis 缓存命中应用信息：appId={}", appId);
            try {
                // 使用 Hutool JSON 解析 (项目已引入 hutool-all)
                return cn.hutool.json.JSONUtil.toBean(cachedJson, AppInfo.class);
            } catch (Exception e) {
                log.warn("Redis 缓存数据解析失败，重新从数据库查询：appId={}", appId, e);
                redisTemplate.delete(redisKey);
            }
        }

        // Redis 未命中，查询数据库
        log.debug("Redis 未命中，查询数据库：appId={}", appId);
        AppInfo appInfo = queryFromDatabase(appId);
        
        if (appInfo != null && appInfo.isValid()) {
            // 写入 Redis 缓存
            cacheAppInfo(appInfo);
        }
        
        return appInfo;
    }

    /**
     * 从数据库查询应用信息
     * 使用 MyBatis-Plus 查询
     *
     * @param appId 应用 ID
     * @return 应用信息
     */
    public AppInfo queryFromDatabase(String appId) {
        // 使用 MyBatis-Plus 查询
        LambdaQueryWrapper<AppInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AppInfo::getAppId, appId);
        wrapper.last("LIMIT 1");
        
        AppInfo appInfo = appInfoMapper.selectOne(wrapper);
        
        if (appInfo != null) {
            log.debug("从数据库查询到应用信息：appId={}, appName={}", appId, appInfo.getAppName());
        } else {
            log.debug("数据库中未找到应用信息：appId={}", appId);
        }
        
        return appInfo;
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
        
        // 缓存 24 小时
        Duration ttl = Duration.ofHours(24);
        
        // 序列化为 JSON 存储
        String json = cn.hutool.json.JSONUtil.toJsonStr(appInfo);
        redisTemplate.opsForValue().set(redisKey, json, ttl);
        
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
        redisTemplate.delete(redisKey);
        log.debug("应用信息缓存已清除：appId={}", appId);
    }

    /**
     * 注册新应用
     * 保存到数据库并缓存
     *
     * @param appInfo 应用信息
     * @return 注册后的应用信息
     */
    @Transactional(rollbackFor = Exception.class)
    public AppInfo registerApp(AppInfo appInfo) {
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
        
        if (appInfo.getCreateTime() == null) {
            appInfo.setCreateTime(java.time.LocalDateTime.now());
        }
        
        if (appInfo.getUpdateTime() == null) {
            appInfo.setUpdateTime(java.time.LocalDateTime.now());
        }
        
        // 插入数据库
        int rows = appInfoMapper.insert(appInfo);
        if (rows != 1) {
            throw new RuntimeException("应用注册失败，数据库插入失败");
        }
        
        // 缓存到 Redis
        cacheAppInfo(appInfo);
        
        log.info("新应用注册成功：id={}, appId={}, appName={}", 
                 appInfo.getId(), appInfo.getAppId(), appInfo.getAppName());
        return appInfo;
    }

    /**
     * 更新应用信息
     * 更新数据库并清除缓存
     *
     * @param appInfo 应用信息
     * @return 更新后的应用信息
     */
    @Transactional(rollbackFor = Exception.class)
    public AppInfo updateApp(AppInfo appInfo) {
        if (appInfo.getId() == null) {
            // 根据 appId 查询 ID
            AppInfo existing = queryFromDatabase(appInfo.getAppId());
            if (existing == null) {
                throw new RuntimeException("应用不存在：" + appInfo.getAppId());
            }
            appInfo.setId(existing.getId());
        }
        
        appInfo.setUpdateTime(java.time.LocalDateTime.now());
        
        // 更新数据库
        int rows = appInfoMapper.updateById(appInfo);
        if (rows != 1) {
            throw new RuntimeException("应用更新失败");
        }
        
        // 清除缓存 (下次读取时会重新加载)
        evictCache(appInfo.getAppId());
        
        log.info("应用信息更新成功：id={}, appId={}", appInfo.getId(), appInfo.getAppId());
        return appInfo;
    }

    /**
     * 删除应用
     *
     * @param appId 应用 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteApp(String appId) {
        AppInfo appInfo = queryFromDatabase(appId);
        if (appInfo == null) {
            throw new RuntimeException("应用不存在：" + appId);
        }
        
        // 删除数据库记录
        appInfoMapper.deleteById(appInfo.getId());
        
        // 清除缓存
        evictCache(appId);
        
        log.info("应用已删除：id={}, appId={}", appInfo.getId(), appId);
    }
}
