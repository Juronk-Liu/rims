package com.rims.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rims.common.PageResult;
import com.rims.common.Result;
import com.rims.entity.AuditLog;
import com.rims.mapper.AuditLogMapper;
import com.rims.security.RequireRole;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {

    private final AuditLogMapper auditLogMapper;

    public AuditController(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @GetMapping
    @RequireRole("ADMIN")
    public Result<PageResult<AuditLog>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AuditLog::getUsername, keyword)
                    .or().like(AuditLog::getAction, keyword)
                    .or().like(AuditLog::getTarget, keyword));
        }
        wrapper.orderByDesc(AuditLog::getId);
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.ok(new PageResult<>(result.getTotal(), result.getRecords()));
    }
}
