package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

@Data
@TableName("milestone")
public class Milestone {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String name;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String status;
    private Integer sortNo;
}
