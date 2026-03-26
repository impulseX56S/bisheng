package com.example.security.aspect;

import cn.dev33.satoken.stp.StpUtil;
import com.example.security.annotation.ApiSignature;
import com.example.security.config.SignatureProperties;
import com.example.security.dto.SignatureHeader;
import com.example.security.entity.AppInfo;
import com.example.security.service.AppInfoService;
import com.example.security.service.ReplayAttackService;
import com.example.security.util.SignatureUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 接口签名验证切面
 * 拦截标注了 @ApiSignature 注解的接口，进行签名验证
 *
 * @author Security Architect
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiSignatureAspect {

    private final SignatureUtil signatureUtil;
    private final AppInfoService appInfoService;
    private final ReplayAttackService replayAttackService;
    private final SignatureProperties properties;

    private static final String HEADER_APP_ID = "X-App-ID";
    private static final String HEADER_TIMESTAMP = "X-Timestamp";
    private static final String HEADER_NONCE = "X-Nonce";
    private static final String HEADER_SIGN = "X-Sign";
    private static final String HEADER_SIGN_VERSION = "X-Sign-Version";

    /**
     * 定义切点：所有标注了 @ApiSignature 注解的方法或类
     */
    @Pointcut("@within(com.example.security.annotation.ApiSignature) || @annotation(com.example.security.annotation.ApiSignature)")
    public void apiSignaturePointcut() {
    }

    /**
     * 环绕通知：执行签名验证
     */
    @Around("apiSignaturePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 获取方法上的注解 (优先方法级，其次类级)
        ApiSignature apiSignature = method.getAnnotation(ApiSignature.class);
        if (apiSignature == null) {
            apiSignature = AnnotationUtils.findAnnotation(
                joinPoint.getTarget().getClass(), ApiSignature.class);
        }

        if (apiSignature == null) {
            return joinPoint.proceed();
        }

        // 检查是否启用签名验证
        if (!properties.isEnabled()) {
            log.debug("签名验证已禁用，跳过验证");
            return joinPoint.proceed();
        }

        // 获取请求信息
        HttpServletRequest request = getRequest();
        
        // 解析签名头信息
        SignatureHeader header = parseSignatureHeader(request, apiSignature.allowQueryParam());
        
        // 1. 验证 AppID 是否存在且有效
        AppInfo appInfo = validateAppId(header.getAppId());
        
        // 2. 验证时间戳 (防重放)
        if (apiSignature.validateTimestamp()) {
            validateTimestamp(header.getTimestamp());
        }
        
        // 3. 验证 Nonce (防重放)
        if (apiSignature.validateNonce()) {
            validateNonce(header.getAppId(), header.getNonce(), header.getTimestamp());
        }
        
        // 4. 验证 IP 白名单 (可选)
        if (apiSignature.validateIp() && StringUtils.hasText(appInfo.getIpWhitelist())) {
            validateIpWhitelist(request, appInfo.getIpWhitelist());
        }
        
        // 5. 验证签名
        validateSignature(header, request, appInfo);
        
        // 6. 将 AppInfo 存入 Sa-Token 上下文 (供后续业务使用)
        StpUtil.getSession().set("appInfo", appInfo);
        StpUtil.getSession().set("appId", appInfo.getAppId());
        
        log.info("签名验证通过：appId={}, path={}", appInfo.getAppId(), request.getRequestURI());
        
        return joinPoint.proceed();
    }

    /**
     * 解析请求头中的签名信息
     */
    private SignatureHeader parseSignatureHeader(HttpServletRequest request, boolean allowQueryParam) {
        String appId = request.getHeader(HEADER_APP_ID);
        String timestampStr = request.getHeader(HEADER_TIMESTAMP);
        String nonce = request.getHeader(HEADER_NONCE);
        String sign = request.getHeader(HEADER_SIGN);
        String signVersionStr = request.getHeader(HEADER_SIGN_VERSION);

        // 如果请求头中没有，尝试从查询参数获取 (仅当允许时)
        if (!StringUtils.hasText(appId) && allowQueryParam) {
            appId = request.getParameter("appId");
        }
        if (!StringUtils.hasText(timestampStr) && allowQueryParam) {
            timestampStr = request.getParameter("timestamp");
        }
        if (!StringUtils.hasText(nonce) && allowQueryParam) {
            nonce = request.getParameter("nonce");
        }
        if (!StringUtils.hasText(sign) && allowQueryParam) {
            sign = request.getParameter("sign");
        }

        Long timestamp = null;
        if (StringUtils.hasText(timestampStr)) {
            try {
                timestamp = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                throw new SecurityException("时间戳格式错误");
            }
        }

        Integer signVersion = null;
        if (StringUtils.hasText(signVersionStr)) {
            try {
                signVersion = Integer.parseInt(signVersionStr);
            } catch (NumberFormatException e) {
                signVersion = 1; // 默认版本
            }
        }

        return SignatureHeader.builder()
                .appId(appId)
                .timestamp(timestamp)
                .nonce(nonce)
                .sign(sign)
                .signVersion(signVersion)
                .contentType(request.getContentType())
                .build();
    }

    /**
     * 验证 AppID
     */
    private AppInfo validateAppId(String appId) {
        if (!StringUtils.hasText(appId)) {
            throw new SecurityException("AppID 不能为空");
        }

        AppInfo appInfo = appInfoService.getAppInfo(appId);
        if (appInfo == null) {
            log.warn("AppID 不存在：{}", appId);
            throw new SecurityException("无效的 AppID");
        }

        if (!appInfo.isValid()) {
            log.warn("AppID 已被禁用：{}", appId);
            throw new SecurityException("AppID 已被禁用");
        }

        return appInfo;
    }

    /**
     * 验证时间戳
     */
    private void validateTimestamp(Long timestamp) {
        if (timestamp == null) {
            throw new SecurityException("时间戳不能为空");
        }

        if (!replayAttackService.validateTimestamp(timestamp)) {
            throw new SecurityException("时间戳已过期，请检查系统时间或重新发起请求");
        }
    }

    /**
     * 验证 Nonce
     */
    private void validateNonce(String appId, String nonce, Long timestamp) {
        if (!StringUtils.hasText(nonce)) {
            throw new SecurityException("随机数 (Nonce) 不能为空");
        }

        if (!replayAttackService.checkAndRecordNonce(appId, nonce, timestamp)) {
            throw new SecurityException("重复的请求，疑似重放攻击");
        }
    }

    /**
     * 验证 IP 白名单
     */
    private void validateIpWhitelist(HttpServletRequest request, String whitelist) {
        String remoteAddr = request.getRemoteAddr();
        
        // 处理 IPv6 映射到 IPv4 的情况
        if (remoteAddr != null && remoteAddr.startsWith("0:0:0:0:0:0:ffff:")) {
            remoteAddr = remoteAddr.substring("0:0:0:0:0:0:ffff:".length());
        }
        
        String[] ips = whitelist.split(",");
        for (String ip : ips) {
            if (ip.trim().equals(remoteAddr)) {
                return;
            }
        }
        
        log.warn("IP 不在白名单中：{}, 白名单：{}", remoteAddr, whitelist);
        throw new SecurityException("IP 地址不允许访问");
    }

    /**
     * 验证签名
     */
    private void validateSignature(SignatureHeader header, HttpServletRequest request, AppInfo appInfo) {
        if (!StringUtils.hasText(header.getSign())) {
            throw new SecurityException("签名值不能为空");
        }

        // 获取派生密钥
        int version = header.getSignVersion() != null ? header.getSignVersion() 
                    : (appInfo.getSecretVersion() != null ? appInfo.getSecretVersion() : 1);
        String derivedSecret = signatureUtil.deriveSecret(appInfo.getMasterSecret(), 
                                                          appInfo.getAppId(), version);

        // 读取请求体 (用于签名计算)
        String requestBody = readRequestBody(request);

        // 构建待签名字符串
        String content = signatureUtil.buildSignContent(
                header.getAppId(),
                header.getTimestamp(),
                header.getNonce(),
                requestBody,
                request.getRequestURI()
        );

        // 验证签名
        boolean isValid = signatureUtil.verifySign(derivedSecret, content, header.getSign());
        
        if (!isValid) {
            log.error("签名验证失败！appId={}, expected content={}", appInfo.getAppId(), content);
            throw new SecurityException("签名验证失败");
        }
    }

    /**
     * 读取请求体
     */
    private String readRequestBody(HttpServletRequest request) {
        try {
            // 注意：这里需要确保请求体可以被多次读取
            // 实际项目中可能需要使用 ContentCachingRequestWrapper
            StringBuilder sb = new StringBuilder();
            byte[] buffer = new byte[1024];
            int len;
            while ((len = request.getInputStream().read(buffer)) != -1) {
                sb.append(new String(buffer, 0, len, "UTF-8"));
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("读取请求体失败", e);
            return "";
        }
    }

    /**
     * 获取当前 HTTP 请求
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("无法获取 HTTP 请求上下文");
        }
        return attributes.getRequest();
    }
}
