package cn.study.insurance.entity;

import cn.study.insurance.common.JsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 保单，映射 policies 表。
 *
 * 出单即保单：没有订单表，一份保单就是一次投保的全部凭证。
 * 表里只保留真正要写的列，没有映射不到的残留字段。
 */
@Getter
@Setter
@TableName(value = "policies", autoResultMap = true)
public class Policy {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    /** 出单用的报价；同一份报价只能出一张保单，靠这一列的唯一索引兜底。 */
    @TableField("quote_id")
    private UUID quoteId;

    @TableField("policy_number")
    private String policyNumber;

    private String status;

    @TableField("effective_at")
    private OffsetDateTime effectiveAt;

    @TableField("expires_at")
    private OffsetDateTime expiresAt;

    @TableField("insured_name")
    private String insuredName;

    @TableField("insured_id_no_masked")
    private String insuredIdNoMasked;

    @TableField("insured_phone")
    private String insuredPhone;

    @TableField("coverage_amount")
    private BigDecimal coverageAmount;

    @TableField("premium_amount")
    private BigDecimal premiumAmount;

    @TableField(value = "policy_snapshot", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> policySnapshot;

    @TableField("issued_at")
    private OffsetDateTime issuedAt;

    @TableField("created_at")
    private OffsetDateTime createdAt;
}
