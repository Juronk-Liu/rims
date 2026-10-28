package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    /** BCrypt 摘要，永不出现在响应里 */
    @JsonIgnore
    private String password;
    private String realName;
    private String role;
    private String dept;
    private String title;
    private String email;
    private String phone;
    private Integer status;
    private LocalDateTime createdAt;
}
