package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

@Data
@TableName("budget_item")
public class BudgetItem {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String category;
    private BigDecimal amount;
}
