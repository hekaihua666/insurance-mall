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
 * 保费试算表，映射 premium_quotes 表。
 *
 * 试算一次就留一条记录，request_params 存用户填的参数、result 存命中费率，
 * 下单时引用这条报价，保证「算的」和「买的」是同一个数。
 */
@Getter
@Setter
@TableName(value = "premium_quotes", autoResultMap = true)
public class PremiumQuote {

    @TableId(type = IdType.INPUT)
    private UUID id;

    @TableField("user_id")
    private Long userId;

    @TableField("product_id")
    private Long productId;

    @TableField(value = "request_params", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> requestParams;

    @TableField(value = "result", typeHandler = JsonbTypeHandler.class)
    private Map<String, Object> result;

    @TableField("total_premium")
    private BigDecimal totalPremium;

    @TableField("pricing_mode")
    private String pricingMode;

    @TableField("expires_at")
    private OffsetDateTime expiresAt;
}
