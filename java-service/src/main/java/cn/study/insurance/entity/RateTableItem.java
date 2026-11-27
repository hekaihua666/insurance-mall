package cn.study.insurance.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 费率明细表
 *
 * 映射 rate_table_items 表。
 *
 * 保障期限和缴费期限随命中费率一起写入报价快照，避免出单时丢失产品规则。
 */
@Getter
@Setter
@TableName("rate_table_items")
public class RateTableItem {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("product_id")
    private Long productId;

    @TableField("rate_type")
    private String rateType;

    @TableField("age_min")
    private Integer ageMin;

    @TableField("age_max")
    private Integer ageMax;

    @TableField("coverage_amount")
    private BigDecimal coverageAmount;

    @TableField("rate_value")
    private BigDecimal rateValue;

    private BigDecimal premium;

    @TableField("coverage_period")
    private String coveragePeriod;

    @TableField("payment_period")
    private String paymentPeriod;

    private String status;
}
