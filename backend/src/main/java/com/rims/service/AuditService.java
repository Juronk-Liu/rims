package com.rims.service;

import com.rims.entity.AuditLog;
import com.rims.mapper.AuditLogMapper;
import com.rims.security.AuthContext;
import com.rims.security.LoginUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作留痕。科研管理系统的审批动作需要可追溯，这里统一落 audit_log。
 */
@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(String action, String target, String detail) {
        LoginUser user = AuthContext.getOrNull();
        write(user == null ? null : user.id(), user == null ? null : user.username(), action, target, detail);
    }

    public void recordAs(Long userId, String username, String action, String target, String detail) {
        write(userId, username, action, target, detail);
    }

    private void write(Long userId, String username, String action, String target, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setUsername(username);
        log.setAction(action);
        log.setTarget(target);
        log.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        log.setIp(currentIp());
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    private String currentIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
            return request.getRemoteAddr();
        }
        return "system";
    }
}
