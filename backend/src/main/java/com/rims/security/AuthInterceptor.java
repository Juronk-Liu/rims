package com.rims.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rims.common.Result;
import com.rims.entity.SysUser;
import com.rims.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 轻量鉴权：JWT 解析 -> 回查用户 -> 写入上下文 -> 校验 @RequireRole。
 * 没有引入 Spring Security 全家桶，理由见 DESIGN.md（MVP 阶段只需认证 + 角色门禁）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, SysUserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = resolveToken(request);
        if (token == null) {
            writeError(response, 401, "未登录或登录已失效");
            return false;
        }
        SysUser user;
        try {
            Long userId = jwtUtil.parseUserId(token);
            user = userMapper.selectById(userId);
        } catch (Exception e) {
            writeError(response, 401, "登录已失效，请重新登录");
            return false;
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            writeError(response, 401, "账号不存在或已停用");
            return false;
        }
        LoginUser loginUser = new LoginUser(user.getId(), user.getUsername(), user.getRealName(),
                user.getRole(), user.getDept());
        AuthContext.set(loginUser);

        if (handler instanceof HandlerMethod handlerMethod) {
            RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
            if (requireRole == null) {
                requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
            }
            if (requireRole != null && !hasAnyRole(loginUser, requireRole.value())) {
                AuthContext.clear();
                writeError(response, 403, "当前角色无此操作权限");
                return false;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean hasAnyRole(LoginUser user, String[] roles) {
        for (String role : roles) {
            if (role.equals(user.role())) {
                return true;
            }
        }
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        String param = request.getParameter("token");
        return (param == null || param.isBlank()) ? null : param;
    }

    private void writeError(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(200);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(code, message)));
    }
}
