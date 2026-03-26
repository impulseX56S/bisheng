package com.example.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 签名请求头 DTO
 * 客户端需要在请求头中携带这些参数
 *
 * @author Security Architect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignatureHeader {

    /**
     * 应用 ID (App ID)
     * 用于标识调用方身份
     */
    @NotBlank(message = "AppID 不能为空")
    private String appId;

    /**
     * 时间戳 (Timestamp)
     * 单位：毫秒，用于防止重放攻击
     */
    @NotNull(message = "时间戳不能为空")
    private Long timestamp;

    /**
     * 随机数 (Nonce)
     * 与时间戳配合防止重放攻击
     */
    @NotBlank(message = "随机数不能为空")
    private String nonce;

    /**
     * 签名值 (Signature)
     * 使用派生密钥对请求参数进行 HMAC-SHA256 签名
     */
    @NotBlank(message = "签名值不能为空")
    private String sign;

    /**
     * 签名版本 (可选)
     * 用于密钥版本管理
     */
    private Integer signVersion;

    /**
     * 请求内容类型
     */
    private String contentType;
}
