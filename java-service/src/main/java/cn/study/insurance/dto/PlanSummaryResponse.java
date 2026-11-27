package cn.study.insurance.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 列表只返回卡片需要的字段，推荐明细由详情接口加载。 */
public record PlanSummaryResponse(
        UUID id,
        String planName,
        String summary,
        BigDecimal annualPremiumBudget,
        OffsetDateTime createdAt
) {}
