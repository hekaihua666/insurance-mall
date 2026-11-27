package cn.study.insurance.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** 方案详情出参 */
@Getter
@Setter
public class PlanResponse {

    private String id;
    private String planName;
    private String summary;
    private BigDecimal annualPremiumBudget;
    private List<PlanItemResponse> items;
}
