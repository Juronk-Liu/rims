package com.rims.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("expense")
public class Expense {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long projectId;
    private String category;
    private BigDecimal amount;
    private String purpose;
    private LocalDate expenseDate;
    private Long applicantId;
    private String applicantName;
    private String status;
    private Long approverId;
    private String approverName;
    private String opinion;
    private LocalDateTime createdAt;
    private LocalDateTime approvedAt;
}
