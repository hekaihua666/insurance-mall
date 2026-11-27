package cn.study.insurance.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 保费试算出参。
 *
 * totalPremium 是按当前配置费率计算的试算值，
 * 前端展示这个数，用户点「立即投保」时用 quoteId 下单。
 */
@Getter
@Setter
public class QuoteResponse {

    private String quoteId;
    private Long productId;
    private String productName;
    private BigDecimal totalPremium;
    private String pricingMode;
    private BigDecimal rateValue;
    private String coveragePeriod;
    private String paymentPeriod;
}
