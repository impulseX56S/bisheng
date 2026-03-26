package com.example.security.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.example.security.annotation.ApiSignature;
import com.example.security.dto.ApiResponse;
import com.example.security.entity.AppInfo;
import com.example.security.service.AppInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 示例 Controller - 演示受签名保护的接口
 *
 * @author Security Architect
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SecureApiController {

    private final AppInfoService appInfoService;

    /**
     * 示例：需要签名验证的 GET 接口
     */
    @GetMapping("/data")
    @ApiSignature
    public ApiResponse<Map<String, Object>> getData(
            @RequestParam(defaultValue = "default") String param) {
        
        // 从 Sa-Token Session 中获取已验证的 AppInfo
        AppInfo appInfo = (AppInfo) StpUtil.getSession().get("appInfo");
        
        Map<String, Object> data = new HashMap<>();
        data.put("message", "这是一个受签名保护的数据接口");
        data.put("param", param);
        data.put("appId", appInfo != null ? appInfo.getAppId() : "unknown");
        data.put("appName", appInfo != null ? appInfo.getAppName() : "unknown");
        data.put("timestamp", System.currentTimeMillis());
        
        return ApiResponse.success(data);
    }

    /**
     * 示例：需要签名验证的 POST 接口
     */
    @PostMapping("/submit")
    @ApiSignature
    public ApiResponse<String> submitData(@RequestBody Map<String, Object> body) {
        
        AppInfo appInfo = (AppInfo) StpUtil.getSession().get("appInfo");
        
        log.info("收到提交数据：appId={}, data={}", 
                 appInfo != null ? appInfo.getAppId() : "unknown", body);
        
        return ApiResponse.success("数据提交成功");
    }

    /**
     * 示例：不需要签名验证的公开接口
     */
    @GetMapping("/public/info")
    public ApiResponse<Map<String, String>> getPublicInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("version", "1.0.0");
        info.put("description", "安全 API 演示系统");
        info.put("status", "running");
        
        return ApiResponse.success(info);
    }

    /**
     * 示例：带 IP 白名单验证的接口
     */
    @GetMapping("/secure/admin")
    @ApiSignature(validateIp = true)
    public ApiResponse<String> adminOperation() {
        AppInfo appInfo = (AppInfo) StpUtil.getSession().get("appInfo");
        return ApiResponse.success("管理员操作成功，应用：" + appInfo.getAppName());
    }

    /**
     * 示例：允许从查询参数获取签名信息的接口 (不推荐用于生产环境)
     */
    @GetMapping("/flexible")
    @ApiSignature(allowQueryParam = true)
    public ApiResponse<String> flexibleAccess(@RequestParam String query) {
        return ApiResponse.success("查询结果：" + query);
    }
}
