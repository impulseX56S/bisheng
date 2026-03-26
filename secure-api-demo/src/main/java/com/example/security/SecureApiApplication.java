package com.example.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 安全 API 演示应用启动类
 * 
 * 功能特性:
 * 1. AppID + AppSecret 签名验证 (防篡改)
 * 2. 时间戳 + Nonce 机制 (防重放攻击)
 * 3. Master Secret 派生密钥 (密钥安全管理)
 * 4. Sa-Token 集成 (会话管理)
 * 5. Redis 缓存支持 (高性能)
 * 6. IP 白名单控制 (可选)
 *
 * @author Security Architect
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class SecureApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecureApiApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  安全 API 演示系统启动成功!");
        System.out.println("========================================");
        System.out.println("\n测试步骤:");
        System.out.println("1. 注册新应用：POST /admin/app/register");
        System.out.println("2. 获取签名示例：GET /admin/sign-demo?appId=xxx&timestamp=xxx&nonce=xxx");
        System.out.println("3. 调用受保护接口：GET /api/data (携带签名头)");
        System.out.println("\n默认测试应用:");
        System.out.println("  AppID: test-app-001");
        System.out.println("  MasterSecret: my-super-secret-master-key-2024");
        System.out.println("========================================\n");
    }
}
