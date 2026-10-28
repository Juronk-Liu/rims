package com.rims.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rims.common.BizException;
import com.rims.common.Dicts;
import com.rims.dto.Dtos;
import com.rims.entity.SysUser;
import com.rims.mapper.SysUserMapper;
import com.rims.security.AuthContext;
import com.rims.security.JwtUtil;
import com.rims.security.LoginUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(SysUserMapper userMapper, JwtUtil jwtUtil, AuditService auditService) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
    }

    public Dtos.LoginResponse login(Dtos.LoginRequest req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.username().trim()));
        if (user == null || !encoder.matches(req.password(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(403, "账号已停用，请联系管理员");
        }
        String token = jwtUtil.generate(user.getId(), user.getUsername(), user.getRole());
        auditService.recordAs(user.getId(), user.getUsername(), "LOGIN", "认证", "登录系统");
        return new Dtos.LoginResponse(token, toVO(user));
    }

    public Dtos.UserVO me() {
        LoginUser current = AuthContext.get();
        SysUser user = userMapper.selectById(current.id());
        if (user == null) {
            throw new BizException(401, "账号不存在");
        }
        return toVO(user);
    }

    /** 供申报页选择负责人/成员，任何登录用户可读，且不返回密码。 */
    public List<Dtos.UserVO> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getStatus, 1)
                        .orderByAsc(SysUser::getId))
                .stream().map(this::toVO).toList();
    }

    private Dtos.UserVO toVO(SysUser user) {
        String roleLabel = "";
        try {
            roleLabel = Dicts.Role.valueOf(user.getRole()).getLabel();
        } catch (IllegalArgumentException ignored) {
            roleLabel = user.getRole();
        }
        return new Dtos.UserVO(user.getId(), user.getUsername(), user.getRealName(), user.getRole(),
                roleLabel, user.getDept(), user.getTitle(), user.getEmail());
    }
}
