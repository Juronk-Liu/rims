package com.rims.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.rims.entity.BudgetItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface BudgetItemMapper extends BaseMapper<BudgetItem> {

    /**
     * 原子预留：仅在“已占用 + 本次 <= 科目额度”时成功（影响行数 1），否则 0。
     * 用数据库行锁保证并发安全，彻底消除 read-modify-write 超支竞态。
     */
    @Update("UPDATE budget_item SET reserved_amount = reserved_amount + #{amt} " +
            "WHERE id = #{id} AND reserved_amount + #{amt} <= amount")
    int reserve(@Param("id") Long id, @Param("amt") BigDecimal amt);

    @Update("UPDATE budget_item SET amount = #{amt} " +
            "WHERE id = #{id} AND reserved_amount <= #{amt}")
    int updateAmountIfAvailable(@Param("id") Long id, @Param("amt") BigDecimal amt);

    /** 原子释放：驳回/撤回经费时回退占用，仅当足够扣减时成功。 */
    @Update("UPDATE budget_item SET reserved_amount = reserved_amount - #{amt} " +
            "WHERE id = #{id} AND reserved_amount >= #{amt}")
    int release(@Param("id") Long id, @Param("amt") BigDecimal amt);
}
