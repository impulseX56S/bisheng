package com.example.security.controller;

import com.example.security.dto.ApiResponse;
import com.example.security.entity.AppInfo;
import com.example.security.service.AppInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用管理控制器
 * 提供应用注册、查询、更新、删除等功能
 *
 * @author Security Architect
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/apps")
@RequiredArgsConstructor
public class AppManagementController {

    private final AppInfoService appInfoService;

    /**
     * 注册新应用
     */
    @PostMapping
    public ApiResponse<AppInfo> registerApp(@RequestBody AppInfo appInfo) {
        try {
            // 检查 appId 是否已存在
            AppInfo existing = appInfoService.queryFromDatabase(appInfo.getAppId());
            if (existing != null) {
                return ApiResponse.error("应用 ID 已存在：" + appInfo.getAppId());
            }
            
            AppInfo registered = appInfoService.registerApp(appInfo);
            return ApiResponse.success(registered, "应用注册成功");
        } catch (Exception e) {
            log.error("应用注册失败", e);
            return ApiResponse.error("应用注册失败：" + e.getMessage());
        }
    }

    /**
     * 根据 AppID 查询应用信息
     */
    @GetMapping("/{appId}")
    public ApiResponse<AppInfo> getAppInfo(@PathVariable String appId) {
        try {
            AppInfo appInfo = appInfoService.getAppInfo(appId);
            if (appInfo == null) {
                return ApiResponse.error("应用不存在：" + appId);
            }
            // 不返回 masterSecret
            appInfo.setMasterSecret(null);
            return ApiResponse.success(appInfo);
        } catch (Exception e) {
            log.error("查询应用信息失败", e);
            return ApiResponse.error("查询失败：" + e.getMessage());
        }
    }

    /**
     * 验证 AppID 是否存在
     */
    @GetMapping("/{appId}/exists")
    public ApiResponse<Map<String, Object>> checkAppExists(@PathVariable String appId) {
        Map<String, Object> result = new HashMap<>();
        result.put("appId", appId);
        result.put("exists", appInfoService.isValidApp(appId));
        return ApiResponse.success(result);
    }

    /**
     * 更新应用信息
     */
    @PutMapping("/{appId}")
    public ApiResponse<AppInfo> updateApp(@PathVariable String appId, 
                                          @RequestBody AppInfo appInfo) {
        try {
            appInfo.setAppId(appId);
            AppInfo updated = appInfoService.updateApp(appInfo);
            updated.setMasterSecret(null);
            return ApiResponse.success(updated, "应用更新成功");
        } catch (Exception e) {
            log.error("应用更新失败", e);
            return ApiResponse.error("应用更新失败：" + e.getMessage());
        }
    }

    /**
     * 删除应用
     */
    @DeleteMapping("/{appId}")
    public ApiResponse<Void> deleteApp(@PathVariable String appId) {
        try {
            appInfoService.deleteApp(appId);
            return ApiResponse.success(null, "应用删除成功");
        } catch (Exception e) {
            log.error("应用删除失败", e);
            return ApiResponse.error("应用删除失败：" + e.getMessage());
        }
    }

    /**
     * 刷新应用缓存
     */
    @PostMapping("/{appId}/refresh-cache")
    public ApiResponse<Void> refreshCache(@PathVariable String appId) {
        try {
            appInfoService.evictCache(appId);
            // 重新加载缓存
            appInfoService.getAppInfo(appId);
            return ApiResponse.success(null, "缓存刷新成功");
        } catch (Exception e) {
            log.error("刷新缓存失败", e);
            return ApiResponse.error("刷新缓存失败：" + e.getMessage());
        }
    }

    /**
     * 获取派生密钥 (仅用于调试，生产环境不建议暴露)
     */
    @GetMapping("/{appId}/derived-secret")
    public ApiResponse<Map<String, Object>> getDerivedSecret(@PathVariable String appId) {
        try {
            String derivedSecret = appInfoService.getDerivedSecret(appId);
            if (derivedSecret == null) {
                return ApiResponse.error("应用不存在或已禁用：" + appId);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("appId", appId);
            result.put("derivedSecret", derivedSecret);
            result.put("note", "此接口仅用于调试，生产环境请禁用");
            
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("获取派生密钥失败", e);
            return ApiResponse.error("获取失败：" + e.getMessage());
        }
    }
}
