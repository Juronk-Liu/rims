package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("progress_report")
public class ProgressReport {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private Long reporterId;
    private String reporterName;
    private LocalDate reportDate;
    private Integer progressPercent;
    private String content;
    private String issues;
    private String nextPlan;
    private LocalDateTime createdAt;
}
