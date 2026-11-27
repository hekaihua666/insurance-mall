package cn.study.insurance.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 方案项出参，附带产品名称和险种，前端可直接展示 */
@Getter
@Setter
public class PlanItemResponse {

    private String id;
    private Long productId;
    private String productName;
    private String category;
    private Integer priority;
    private String recommendationReason;
    private BigDecimal annualPremiumBudget;
}
