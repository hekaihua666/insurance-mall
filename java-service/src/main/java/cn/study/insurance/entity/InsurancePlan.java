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

/**
 * 保险方案，映射 insurance_plans 表。
 *
 * 方案由 Python 侧的 Agent 工具写入（save_insurance_plan），
 * Java 负责按用户查询列表和详情。
 */
@Getter
@Setter
@TableName("insurance_plans")
public class InsurancePlan {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private Long userId;

    @TableField("plan_name")
    private String planName;

    private String summary;

    @TableField("annual_premium_budget")
    private BigDecimal annualPremiumBudget;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
