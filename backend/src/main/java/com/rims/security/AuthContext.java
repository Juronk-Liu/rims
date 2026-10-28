package com.rims.security;

import com.rims.common.BizException;

public final class AuthContext {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(LoginUser user) {
        HOLDER.set(user);
    }

    public static LoginUser get() {
        LoginUser user = HOLDER.get();
        if (user == null) {
            throw new BizException(401, "登录已失效，请重新登录");
        }
        return user;
    }

    public static LoginUser getOrNull() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public static void requireRole(String... roles) {
        LoginUser user = get();
        for (String role : roles) {
            if (role.equals(user.role())) {
                return;
            }
        }
        throw BizException.forbidden("当前角色无此操作权限");
    }
}
