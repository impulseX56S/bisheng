package com.example.security.annotation;

import java.lang.annotation.*;

/**
 * 接口签名验证注解
 * 标注此注解的接口需要进行 AppID + AppSecret 签名验证
 *
 * @author Security Architect
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiSignature {

    /**
     * 是否需要验证时间戳 (防止重放攻击)
     * 默认：true
     */
    boolean validateTimestamp() default true;

    /**
     * 是否需要验证随机数 (防止重放攻击)
     * 默认：true
     */
    boolean validateNonce() default true;

    /**
     * 是否需要验证 IP 白名单
     * 默认：false
     */
    boolean validateIp() default false;

    /**
     * 是否允许从查询参数中获取签名信息
     * 默认：false (仅从请求头获取)
     */
    boolean allowQueryParam() default false;

    /**
     * 排除的路径模式 (在方法级别使用时有效)
     * 例如：{"**/public/**", "**/health"}
     */
    String[] excludePatterns() default {};
}
