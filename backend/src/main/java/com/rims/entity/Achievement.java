package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("achievement")
public class Achievement {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String type;
    private String title;
    private String authors;
    private LocalDate achievedDate;
    private String levelName;
    private String remark;
    private LocalDateTime createdAt;
}
