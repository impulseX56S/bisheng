package com.example.client;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Java 客户端签名生成示例
 * 
 * 演示如何生成与服务器端兼容的签名
 * 
 * @author Security Architect
 */
public class SignatureClient {

    // 配置信息 (从安全渠道获取)
    private static final String APP_ID = "test-app-001";
    private static final String MASTER_SECRET = "my-super-secret-master-key-2024";
    private static final String DERIVE_SALT = "your-master-secret-salt-2024";
    private static final int KEY_VERSION = 1;
    private static final int ITERATIONS = 1000;

    public static void main(String[] args) throws Exception {
        // 请求参数
        String path = "/api/data";
        String body = ""; // GET 请求为空
        
        // 生成签名头
        Map<String, String> headers = generateSignatureHeaders(APP_ID, MASTER_SECRET, path, body);
        
        System.out.println("=== 签名请求头 ===");
        headers.forEach((k, v) -> System.out.println(k + ": " + v));
        
        System.out.println("\n=== cURL 命令 ===");
        StringBuilder curlCmd = new StringBuilder();
        curlCmd.append("curl -X GET http://localhost:8080").append(path);
        headers.forEach((k, v) -> curlCmd.append(" \\\n  -H \"").append(k).append(": ").append(v).append("\""));
        System.out.println(curlCmd);
        
        System.out.println("\n=== OkHttp 示例 ===");
        printOkHttpExample(path, headers);
    }

    /**
     * 生成完整的签名请求头
     */
    public static Map<String, String> generateSignatureHeaders(
            String appId, String masterSecret, String path, String body) {
        
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        
        // 构建待签名字符串
        String content = buildSignContent(appId, timestamp, nonce, body, path);
        
        // 派生密钥
        String derivedSecret = deriveSecret(masterSecret, appId, KEY_VERSION);
        
        // 生成签名
        String sign = hmacSha256(derivedSecret, content);
        
        Map<String, String> headers = new HashMap<>();
        headers.put("X-App-ID", appId);
        headers.put("X-Timestamp", String.valueOf(timestamp));
        headers.put("X-Nonce", nonce);
        headers.put("X-Sign", sign);
        headers.put("X-Sign-Version", String.valueOf(KEY_VERSION));
        
        return headers;
    }

    /**
     * 构建待签名字符串
     * 规则：按参数名 ASCII 码排序后拼接
     */
    public static String buildSignContent(String appId, Long timestamp, String nonce, 
                                          String body, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("appId=").append(appId)
          .append("&nonce=").append(nonce)
          .append("&path=").append(path)
          .append("&timestamp=").append(timestamp);
        
        if (body != null && !body.isEmpty()) {
            sb.append("&body=").append(body);
        }
        
        return sb.toString();
    }

    /**
     * PBKDF2-HMAC-SHA256 密钥派生
     */
    public static String deriveSecret(String masterSecret, String appId, int version) {
        try {
            // 构造盐值
            String salt = DERIVE_SALT + ":" + appId + ":v" + version;
            
            // 使用 Java 自带的 PBKDF2
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                masterSecret.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                ITERATIONS,
                256 // 256 位 = 32 字节
            );
            
            byte[] derivedKey = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            
            // Base64 编码
            return Base64.getEncoder().encodeToString(derivedKey);
            
        } catch (Exception e) {
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * HMAC-SHA256 签名
     * @param derivedSecretBase64 Base64 编码的派生密钥
     * @param content 待签名字符串
     * @return 十六进制签名值
     */
    public static String hmacSha256(String derivedSecretBase64, String content) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(derivedSecretBase64);
            SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
            
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            
            byte[] signBytes = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
            
            return bytesToHex(signBytes);
            
        } catch (Exception e) {
            throw new RuntimeException("签名生成失败", e);
        }
    }

    /**
     * 字节数组转十六进制
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * 打印 OkHttp 使用示例
     */
    private static void printOkHttpExample(String path, Map<String, String> headers) {
        System.out.println("```java");
        System.out.println("OkHttpClient client = new OkHttpClient();");
        System.out.println("");
        System.out.println("Request request = new Request.Builder()");
        System.out.println("    .url(\"http://localhost:8080" + path + "\")");
        System.out.println("    .get();");
        System.out.println("");
        System.out.println("// 添加签名头");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            System.out.println("request = request.newBuilder()");
            System.out.println("    .addHeader(\"" + entry.getKey() + "\", \"" + entry.getValue() + "\")");
            System.out.println("    .build();");
        }
        System.out.println("");
        System.out.println("try (Response response = client.newCall(request).execute()) {");
        System.out.println("    System.out.println(response.body().string());");
        System.out.println("}");
        System.out.println("```");
    }
}
