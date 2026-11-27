package cn.study.insurance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 方案项，映射 insurance_plan_items 表。 */
@Getter
@Setter
@TableName("insurance_plan_items")
public class InsurancePlanItem {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("plan_id")
    private UUID planId;

    @TableField("product_id")
    private Long productId;

    private Integer priority;

    @TableField("recommendation_reason")
    private String recommendationReason;

    @TableField("annual_premium_budget")
    private BigDecimal annualPremiumBudget;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
