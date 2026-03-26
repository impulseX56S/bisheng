package com.example.security.util;

import cn.hutool.crypto.digest.HMac;
import cn.hutool.crypto.digest.HmacAlgorithm;
import cn.hutool.crypto.symmetric.AES;
import com.example.security.config.SignatureProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 签名工具类
 * 提供派生密钥生成和签名验证功能
 *
 * @author Security Architect
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SignatureUtil {

    private final SignatureProperties properties;

    /**
     * 从 Master Secret 派生签名密钥
     * 使用 PBKDF2 或类似算法进行密钥派生
     *
     * @param masterSecret 主密钥
     * @param appId        应用 ID (作为 salt 的一部分)
     * @param version      密钥版本
     * @return 派生后的密钥 (Base64 编码)
     */
    public String deriveSecret(String masterSecret, String appId, int version) {
        // 构造盐值：固定盐 + AppID + 版本号
        String salt = properties.getDeriveSalt() + ":" + appId + ":v" + version;
        
        // 使用 Hutool 的 PBKDF2 进行密钥派生
        // 注意：Hutool 5.8+ 支持 PBKDF2，如果版本较低可以使用其他方法
        byte[] derivedKey = pbkdf2HmacSha256(
            masterSecret.getBytes(StandardCharsets.UTF_8),
            salt.getBytes(StandardCharsets.UTF_8),
            properties.getDeriveIterations(),
            32 // 32 字节 = 256 位
        );
        
        String derivedKeyBase64 = Base64.getEncoder().encodeToString(derivedKey);
        log.debug("派生密钥生成成功：appId={}, version={}, keyLength={}", appId, version, derivedKey.length);
        
        return derivedKeyBase64;
    }

    /**
     * 生成请求签名
     *
     * @param derivedSecret 派生密钥 (Base64 编码)
     * @param content       待签名字符串 (按规则拼接的参数)
     * @return 签名值 (十六进制字符串)
     */
    public String generateSign(String derivedSecret, String content) {
        byte[] keyBytes = Base64.getDecoder().decode(derivedSecret);
        
        HMac hmac = switch (properties.getAlgorithm().toUpperCase()) {
            case "HMACSHA1" -> new HMac(HmacAlgorithm.HmacSHA1, keyBytes);
            case "MD5" -> new HMac(HmacAlgorithm.HmacMD5, keyBytes);
            default -> new HMac(HmacAlgorithm.HmacSHA256, keyBytes);
        };
        
        byte[] signBytes = hmac.digest(content.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(signBytes);
    }

    /**
     * 验证签名
     *
     * @param derivedSecret 派生密钥 (Base64 编码)
     * @param content       待签名字符串
     * @param sign          客户端传来的签名值
     * @return 是否验证通过
     */
    public boolean verifySign(String derivedSecret, String content, String sign) {
        String expectedSign = generateSign(derivedSecret, content);
        boolean result = expectedSign.equalsIgnoreCase(sign);
        log.debug("签名验证结果：expected={}, actual={}, match={}", 
                  expectedSign, sign, result);
        return result;
    }

    /**
     * 构建待签名字符串
     * 规则：按参数名 ASCII 码从小到大排序，然后拼接成 key=value&key=value 格式
     *
     * @param appId     应用 ID
     * @param timestamp 时间戳
     * @param nonce     随机数
     * @param body      请求体内容 (如果是 GET 请求则为空)
     * @param path      请求路径
     * @return 待签名字符串
     */
    public String buildSignContent(String appId, Long timestamp, String nonce, 
                                   String body, String path) {
        StringBuilder sb = new StringBuilder();
        sb.append("appId=").append(appId)
          .append("&nonce=").append(nonce)
          .append("&path=").append(path)
          .append("&timestamp=").append(timestamp);
        
        // 如果有请求体，也加入签名计算
        if (body != null && !body.isEmpty()) {
            sb.append("&body=").append(body);
        }
        
        return sb.toString();
    }

    /**
     * PBKDF2-HMAC-SHA256 密钥派生函数
     *
     * @param password   密码 (Master Secret)
     * @param salt       盐值
     * @param iterations 迭代次数
     * @param dkLen      派生密钥长度 (字节)
     * @return 派生密钥
     */
    private byte[] pbkdf2HmacSha256(byte[] password, byte[] salt, int iterations, int dkLen) {
        // 使用 Hutool 的 PBKDF2 实现
        // 如果 Hutool 版本不支持，可以使用 Java 自带的 Crypto 库
        try {
            javax.crypto.SecretKeyFactory skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            javax.crypto.spec.PBEKeySpec spec = new javax.crypto.spec.PBEKeySpec(
                new String(password, StandardCharsets.UTF_8).toCharArray(),
                salt,
                iterations,
                dkLen * 8
            );
            byte[] derivedKey = skf.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return derivedKey;
        } catch (Exception e) {
            log.error("PBKDF2 密钥派生失败", e);
            throw new RuntimeException("密钥派生失败", e);
        }
    }

    /**
     * 字节数组转十六进制字符串
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
