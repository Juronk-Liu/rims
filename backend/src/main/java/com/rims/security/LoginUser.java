package com.rims.security;

/**
 * 当前请求的登录人快照。由 AuthInterceptor 写入、请求结束时清理。
 */
public record LoginUser(Long id, String username, String realName, String role, String dept) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isDeptAdmin() {
        return "DEPT_ADMIN".equals(role);
    }

    public boolean isFinance() {
        return "FINANCE".equals(role);
    }

    public boolean isResearcher() {
        return "RESEARCHER".equals(role);
    }
}
