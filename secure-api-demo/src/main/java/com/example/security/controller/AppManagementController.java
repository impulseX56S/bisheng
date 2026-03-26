package com.example.security.controller;

import com.example.security.dto.ApiResponse;
import com.example.security.entity.AppInfo;
import com.example.security.service.AppInfoService;
import com.example.security.util.SignatureUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 应用管理 Controller
 * 用于演示应用注册、密钥管理等操作
 *
 * @author Security Architect
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AppManagementController {

    private final AppInfoService appInfoService;
    private final SignatureUtil signatureUtil;

    /**
     * 注册新应用
     * POST /admin/app/register
     */
    @PostMapping("/app/register")
    public ApiResponse<Map<String, Object>> registerApp(@RequestBody Map<String, String> request) {
        String appId = request.get("appId");
        String appName = request.get("appName");
        String description = request.get("description");

        if (appId == null || appId.trim().isEmpty()) {
            return ApiResponse.error(400, "AppID 不能为空");
        }

        // 检查是否已存在
        if (appInfoService.getAppInfo(appId) != null) {
            return ApiResponse.error(409, "AppID 已存在");
        }

        AppInfo appInfo = AppInfo.builder()
                .appId(appId)
                .appName(appName != null ? appName : "未命名应用")
                .description(description)
                .status(1)
                .secretVersion(1)
                .build();

        AppInfo registered = appInfoService.registerApp(appInfo);

        Map<String, Object> result = new HashMap<>();
        result.put("appId", registered.getAppId());
        result.put("appName", registered.getAppName());
        result.put("masterSecret", registered.getMasterSecret());
        result.put("message", "应用注册成功，请妥善保管 Master Secret！");

        return ApiResponse.success(result);
    }

    /**
     * 获取应用信息 (仅用于调试，生产环境应限制访问)
     * GET /admin/app/{appId}
     */
    @GetMapping("/app/{appId}")
    public ApiResponse<Map<String, Object>> getAppInfo(@PathVariable String appId) {
        AppInfo appInfo = appInfoService.getAppInfo(appId);
        
        if (appInfo == null) {
            return ApiResponse.error(404, "应用不存在");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("appId", appInfo.getAppId());
        result.put("appName", appInfo.getAppName());
        result.put("status", appInfo.getStatus());
        result.put("secretVersion", appInfo.getSecretVersion());
        result.put("createTime", appInfo.getCreateTime());
        // 注意：不要返回完整的 masterSecret

        return ApiResponse.success(result);
    }

    /**
     * 生成签名示例 (仅用于调试和测试)
     * 实际项目中客户端应该自己实现签名逻辑
     * GET /admin/sign-demo?appId=xxx&timestamp=xxx&nonce=xxx
     */
    @GetMapping("/sign-demo")
    public ApiResponse<Map<String, String>> generateSignDemo(
            @RequestParam String appId,
            @RequestParam Long timestamp,
            @RequestParam String nonce,
            @RequestParam(defaultValue = "/api/data") String path,
            @RequestParam(required = false) String body) {
        
        AppInfo appInfo = appInfoService.getAppInfo(appId);
        if (appInfo == null) {
            return ApiResponse.error(404, "应用不存在");
        }

        // 获取派生密钥
        String derivedSecret = signatureUtil.deriveSecret(
                appInfo.getMasterSecret(), 
                appId, 
                appInfo.getSecretVersion() != null ? appInfo.getSecretVersion() : 1);

        // 构建待签名字符串
        String content = signatureUtil.buildSignContent(appId, timestamp, nonce, body, path);

        // 生成签名
        String sign = signatureUtil.generateSign(derivedSecret, content);

        Map<String, String> result = new HashMap<>();
        result.put("appId", appId);
        result.put("timestamp", String.valueOf(timestamp));
        result.put("nonce", nonce);
        result.put("path", path);
        result.put("content", content);
        result.put("sign", sign);
        result.put("algorithm", "HMACSHA256");

        return ApiResponse.success(result);
    }

    /**
     * 禁用/启用应用
     * POST /admin/app/{appId}/status
     */
    @PostMapping("/app/{appId}/status")
    public ApiResponse<String> updateAppStatus(
            @PathVariable String appId,
            @RequestParam Integer status) {
        
        AppInfo appInfo = appInfoService.getAppInfo(appId);
        if (appInfo == null) {
            return ApiResponse.error(404, "应用不存在");
        }

        appInfo.setStatus(status);
        appInfoService.cacheAppInfo(appInfo);

        return ApiResponse.success("应用状态已更新：" + (status == 1 ? "启用" : "禁用"));
    }

    /**
     * 清除应用缓存
     * DELETE /admin/app/{appId}/cache
     */
    @DeleteMapping("/app/{appId}/cache")
    public ApiResponse<String> clearAppCache(@PathVariable String appId) {
        appInfoService.evictCache(appId);
        return ApiResponse.success("应用缓存已清除");
    }
}
