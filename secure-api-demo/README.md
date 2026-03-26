# Spring Boot 安全 API 签名验证示例

## 项目概述

本项目是一个完整的 **AppID + AppSecret 签名验证** 解决方案，基于 Spring Boot 3.x 和 Sa-Token 实现。

### 核心功能

1. **签名验证** - 防止请求被篡改
2. **防重放攻击** - 时间戳 + Nonce 机制
3. **密钥派生** - Master Secret 派生签名密钥
4. **应用管理** - AppID 注册、启用/禁用
5. **IP 白名单** - 可选的 IP 访问控制
6. **Sa-Token 集成** - 会话管理和上下文传递

## 技术栈

- Spring Boot 3.2.0
- Sa-Token 1.37.0 (认证授权框架)
- Redis (缓存和防重放)
- Hutool (加密工具)
- Lombok (代码简化)

## 快速开始

### 1. 启动 Redis

```bash
docker run -d -p 6379:6379 --name redis redis:latest
```

### 2. 配置应用

编辑 `src/main/resources/application.yml`:

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379

security:
  signature:
    enabled: true
    timestamp-window: 300  # 时间戳有效窗口 (秒)
    nonce-expire: 300      # Nonce 过期时间 (秒)
    algorithm: HMACSHA256  # 签名算法
    derive-salt: "your-production-salt"  # 生产环境请修改
```

### 3. 启动应用

```bash
cd secure-api-demo
mvn spring-boot:run
```

## API 使用指南

### 签名请求头

客户端需要在请求中携带以下 HTTP 头：

| Header | 说明 | 示例 |
|--------|------|------|
| X-App-ID | 应用 ID | `test-app-001` |
| X-Timestamp | 时间戳 (毫秒) | `1703123456789` |
| X-Nonce | 随机数 | `a1b2c3d4e5f6` |
| X-Sign | 签名值 | `abc123...` |
| X-Sign-Version | 签名版本 (可选) | `1` |

### 签名生成流程

#### 步骤 1: 准备参数

```
appId = "test-app-001"
timestamp = 当前时间戳 (毫秒)
nonce = 随机字符串 (建议使用 UUID 或随机数)
path = 请求路径 (如 /api/data)
body = 请求体内容 (GET 请求为空)
```

#### 步骤 2: 构建待签名字符串

按参数名 ASCII 码排序后拼接：

```
content = "appId={appId}&nonce={nonce}&path={path}&timestamp={timestamp}"
如果 body 不为空：content += "&body={body}"
```

#### 步骤 3: 获取派生密钥

```
derivedSecret = PBKDF2(masterSecret, salt=deriveSalt:appId:v{version}, iterations=1000)
```

#### 步骤 4: 生成签名

```
sign = HMAC-SHA256(derivedSecret, content)
结果转为十六进制字符串
```

### 代码示例

#### Java 客户端

```java
public class SignatureClient {
    
    public static Map<String, String> generateHeaders(
            String appId, String masterSecret, String path, String body) {
        
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        
        // 构建待签名字符串
        StringBuilder sb = new StringBuilder();
        sb.append("appId=").append(appId)
          .append("&nonce=").append(nonce)
          .append("&path=").append(path)
          .append("&timestamp=").append(timestamp);
        if (body != null && !body.isEmpty()) {
            sb.append("&body=").append(body);
        }
        String content = sb.toString();
        
        // 派生密钥
        String salt = "your-master-secret-salt-2024:" + appId + ":v1";
        byte[] derivedKey = pbkdf2HmacSha256(masterSecret, salt, 1000, 32);
        String derivedSecret = Base64.getEncoder().encodeToString(derivedKey);
        
        // 生成签名
        String sign = hmacSha256(derivedSecret, content);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-App-ID", appId);
        headers.put("X-Timestamp", String.valueOf(timestamp));
        headers.put("X-Nonce", nonce);
        headers.put("X-Sign", sign);
        
        return headers;
    }
}
```

#### cURL 示例

```bash
# 1. 注册新应用
curl -X POST http://localhost:8080/admin/app/register \
  -H "Content-Type: application/json" \
  -d '{"appId":"my-app","appName":"我的应用"}'

# 2. 获取签名示例 (仅用于调试)
curl "http://localhost:8080/admin/sign-demo?appId=test-app-001&timestamp=1703123456789&nonce=a1b2c3d4"

# 3. 调用受保护接口
APP_ID="test-app-001"
TIMESTAMP=$(date +%s%3N)
NONCE=$(uuidgen | tr -d '-')

# 使用签名示例接口获取正确的签名
SIGN_RESPONSE=$(curl -s "http://localhost:8080/admin/sign-demo?appId=$APP_ID&timestamp=$TIMESTAMP&nonce=$NONCE")
SIGN=$(echo $SIGN_RESPONSE | jq -r '.data.sign')

# 调用实际接口
curl -X GET http://localhost:8080/api/data \
  -H "X-App-ID: $APP_ID" \
  -H "X-Timestamp: $TIMESTAMP" \
  -H "X-Nonce: $NONCE" \
  -H "X-Sign: $SIGN"
```

## 注解使用说明

### @ApiSignature

标注此注解的接口需要进行签名验证。

```java
@RestController
@RequestMapping("/api")
public class MyController {

    // 基础签名验证
    @GetMapping("/data")
    @ApiSignature
    public ApiResponse getData() { ... }

    // 带 IP 白名单验证
    @GetMapping("/admin")
    @ApiSignature(validateIp = true)
    public ApiResponse adminOp() { ... }

    // 允许从查询参数获取签名 (不推荐生产环境)
    @GetMapping("/flexible")
    @ApiSignature(allowQueryParam = true)
    public ApiResponse flexible() { ... }

    // 关闭某些验证
    @GetMapping("/no-timestamp")
    @ApiSignature(validateTimestamp = false, validateNonce = false)
    public ApiResponse noTimestampCheck() { ... }
}
```

## 架构设计

### 密钥派生机制

```
Master Secret (存储在数据库)
       ↓
   PBKDF2-HMAC-SHA256
   Salt = deriveSalt + ":" + appId + ":v" + version
   Iterations = 1000
       ↓
Derived Secret (用于签名，不存储)
```

### 防重放机制

1. **时间戳验证**: 请求时间与服务器时间差不能超过配置窗口 (默认 300 秒)
2. **Nonce 验证**: 每个 Nonce 在 Redis 中记录，过期前不能重复使用

### 验证流程

```
客户端请求
    ↓
拦截器捕获 (@ApiSignature)
    ↓
1. 解析请求头 (AppID, Timestamp, Nonce, Sign)
    ↓
2. 验证 AppID 是否存在且有效 (Redis/数据库)
    ↓
3. 验证时间戳是否在有效窗口内
    ↓
4. 验证 Nonce 是否已使用 (Redis)
    ↓
5. 验证 IP 白名单 (可选)
    ↓
6. 派生密钥并验证签名
    ↓
7. 将 AppInfo 存入 Sa-Token Session
    ↓
执行业务逻辑
```

## 生产环境建议

1. **密钥管理**
   - Master Secret 应加密存储
   - 定期轮换密钥 (通过版本号管理)
   - 使用 KMS 或 Vault 等密钥管理服务

2. **HTTPS**
   - 必须使用 HTTPS 传输
   - 防止中间人攻击

3. **限流**
   - 对每个 AppID 进行限流
   - 防止暴力破解

4. **监控告警**
   - 监控签名失败率
   - 检测异常请求模式

5. **日志审计**
   - 记录所有签名验证日志
   - 保留足够的审计信息

## 项目结构

```
secure-api-demo/
├── src/main/java/com/example/security/
│   ├── SecureApiApplication.java    # 启动类
│   ├── annotation/
│   │   └── ApiSignature.java        # 签名注解
│   ├── aspect/
│   │   └── ApiSignatureAspect.java  # 签名切面
│   ├── config/
│   │   ├── SignatureProperties.java # 配置属性
│   │   └── WebConfig.java           # Web 配置
│   ├── controller/
│   │   ├── SecureApiController.java     # 示例接口
│   │   └── AppManagementController.java # 管理接口
│   ├── dto/
│   │   ├── ApiResponse.java         # 统一响应
│   │   └── SignatureHeader.java     # 签名头 DTO
│   ├── entity/
│   │   └── AppInfo.java             # 应用信息实体
│   ├── handler/
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── service/
│   │   ├── AppInfoService.java      # 应用信息服务
│   │   └── ReplayAttackService.java # 防重放服务
│   └── util/
│       └── SignatureUtil.java       # 签名工具类
└── src/main/resources/
    └── application.yml              # 配置文件
```

## 常见问题

### Q: 如何处理时钟不同步？
A: 客户端应定期与 NTP 服务器同步时间，或在首次交互时获取服务器时间。

### Q: 如何支持多版本密钥？
A: 通过 `X-Sign-Version` 头指定密钥版本，服务端根据版本号派生对应密钥。

### Q: 如何调试签名问题？
A: 使用 `/admin/sign-demo` 接口生成正确的签名，对比客户端生成的签名。

## License

MIT License
