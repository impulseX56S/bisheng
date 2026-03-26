# Spring Boot 安全 API 签名验证示例

基于 Spring Boot + MyBatis-Plus + Sa-Token 的安全 API 签名验证样板项目。

## 核心功能

1. **Master Secret 派生密钥** - PBKDF2-HMAC-SHA256 密钥派生
2. **签名验证** - HMAC-SHA256 防篡改
3. **防重放攻击** - 时间戳窗口 + Nonce(Redis)
4. **AppID 校验** - Redis 缓存 + MariaDB 数据库
5. **Sa-Token 集成** - 会话管理
6. **IP 白名单** - 可选访问控制
7. **密钥版本管理** - 支持密钥轮换

## 技术栈

- Spring Boot 3.2.0
- MyBatis-Plus 3.5.5
- MariaDB Driver 3.3.2
- Sa-Token 1.37.0
- Redis (Lettuce)
- Hutool 工具类

## 数据库配置

### 环境要求
- MariaDB 10.x 或 MySQL 8.x
- Redis 6.x+

### 数据库连接配置
```yaml
spring:
  datasource:
    driver-class-name: org.mariadb.jdbc.Driver
    url: jdbc:mariadb://127.0.0.1:3306/test?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: 1234
```

### 初始化数据库
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 执行建表脚本
mysql -u root -p test < src/main/resources/schema.sql
```

### 数据表结构
```sql
CREATE TABLE app_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    app_id VARCHAR(64) UNIQUE NOT NULL,
    master_secret VARCHAR(128) NOT NULL,
    secret_version INT DEFAULT 1,
    app_name VARCHAR(128) NOT NULL,
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    ip_whitelist TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

## 快速开始

### 1. 启动依赖服务
```bash
# 启动 Redis
docker run -d -p 6379:6379 --name redis redis:latest

# 启动 MariaDB
docker run -d -p 3306:3306 --name mariadb \
  -e MARIADB_ROOT_PASSWORD=1234 \
  -e MARIADB_DATABASE=test \
  mariadb:latest
```

### 2. 初始化数据库
```bash
# 等待 MariaDB 启动后，执行建表脚本
docker exec -i mariadb mysql -u root -p1234 test < src/main/resources/schema.sql
```

### 3. 运行项目
```bash
cd secure-api-demo
mvn spring-boot:run
```

### 4. 测试接口

#### 注册新应用
```bash
curl -X POST http://localhost:8080/api/admin/apps \
  -H "Content-Type: application/json" \
  -d '{
    "appId": "my-app",
    "appName": "我的应用",
    "description": "测试应用"
  }'
```

#### 查询应用信息
```bash
curl http://localhost:8080/api/admin/apps/test-app-001
```

#### 验证 AppID 是否存在
```bash
curl http://localhost:8080/api/admin/apps/test-app-001/exists
```

#### 获取派生密钥（调试用）
```bash
curl http://localhost:8080/api/admin/apps/test-app-001/derived-secret
```

## 签名流程

### 客户端签名生成
1. 获取当前时间戳 `timestamp`
2. 生成随机数 `nonce`
3. 使用 AppID 和派生密钥生成签名：
   ```
   signature = HMAC-SHA256(appId + timestamp + nonce + body, derivedSecret)
   ```
4. 在请求头中携带：
   - `X-App-Id`: 应用 ID
   - `X-Timestamp`: 时间戳
   - `X-Nonce`: 随机数
   - `X-Signature`: 签名值

### 服务端验证流程
1. 从请求头提取 AppID、Timestamp、Nonce、Signature
2. 校验 AppID 是否存在且有效（Redis 缓存 → 数据库）
3. 校验时间戳是否在允许窗口内（默认 5 分钟）
4. 校验 Nonce 是否已使用（Redis 去重）
5. 使用相同算法计算签名并比对
6. 验证通过，执行业务逻辑

## 项目结构

```
secure-api-demo/
├── src/main/java/com/example/security/
│   ├── SecureApiApplication.java      # 启动类
│   ├── annotation/
│   │   └── ApiSignature.java          # 签名注解
│   ├── aspect/
│   │   └── ApiSignatureAspect.java    # AOP 切面验证
│   ├── config/
│   │   ├── SignatureProperties.java   # 签名配置
│   │   ├── WebConfig.java             # Web 配置
│   │   └── MybatisPlusConfig.java     # MyBatis-Plus 配置
│   ├── controller/
│   │   ├── SecureApiController.java   # 示例安全接口
│   │   └── AppManagementController.java # 应用管理接口
│   ├── dto/
│   │   ├── ApiResponse.java           # 统一响应
│   │   └── SignatureHeader.java       # 签名请求头
│   ├── entity/
│   │   └── AppInfo.java               # 应用信息实体
│   ├── handler/
│   │   └── GlobalExceptionHandler.java # 全局异常处理
│   ├── mapper/
│   │   └── AppInfoMapper.java         # MyBatis-Plus Mapper
│   ├── service/
│   │   ├── AppInfoService.java        # 应用信息服务
│   │   └── ReplayAttackService.java   # 防重放攻击服务
│   └── util/
│       └── SignatureUtil.java         # 签名工具类
├── src/main/resources/
│   ├── application.yml                # 应用配置
│   └── schema.sql                     # 数据库建表脚本
└── pom.xml                            # Maven 配置
```

## 使用 @ApiSignature 注解

在任何需要签名验证的 Controller 方法上添加 `@ApiSignature` 注解：

```java
@RestController
@RequestMapping("/api/secure")
public class SecureApiController {

    @PostMapping("/data")
    @ApiSignature
    public ApiResponse<Map<String, Object>> submitData(@RequestBody Map<String, Object> data) {
        // 业务逻辑
        return ApiResponse.success(data, "数据处理成功");
    }
}
```

## 缓存策略

- **Redis 缓存**: 应用信息缓存 24 小时
- **缓存键**: `app:info:{appId}`
- **缓存内容**: JSON 格式的 AppInfo 对象
- **缓存更新**: 应用信息变更时自动清除缓存

## 安全建议

1. **生产环境**: 
   - 修改默认 salt 配置
   - 启用 HTTPS
   - 限制管理接口访问 IP
   - 禁用派生密钥查询接口

2. **密钥管理**:
   - Master Secret 应加密存储
   - 定期轮换密钥（通过 secretVersion）
   - 不在日志中输出密钥

3. **防重放**:
   - 合理设置时间戳窗口
   - Nonce 过期时间应 >= 时间戳窗口

## License

MIT
