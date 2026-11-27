package cn.study.insurance.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 保单出参。证件号返回脱敏后的值。
 */
@Getter
@Setter
public class PolicyResponse {

    private String policyNumber;
    private Long productId;
    private String productName;
    private String status;
    private OffsetDateTime effectiveAt;
    private OffsetDateTime expiresAt;
    private String insuredName;
    private String insuredIdNoMasked;
    private BigDecimal coverageAmount;
    private BigDecimal premiumAmount;
}
