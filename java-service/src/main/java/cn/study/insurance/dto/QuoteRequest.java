package cn.study.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 保费试算入参。
 *
 * 被保人信息在试算时填一次，算出的报价存库；下单时只传 quoteId，不再重复填。
 * 身份证号仅用于解出年龄参与试算，脱敏后存库，不落明文。
 */
@Getter
@Setter
public class QuoteRequest {

    @NotNull(message = "产品不能为空")
    private Long productId;

    @NotBlank(message = "被保人姓名不能为空")
    private String insuredName;

    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "[0-9]{17}[0-9Xx]", message = "身份证号格式不正确")
    private String insuredIdNo;

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "[0-9]{11}", message = "联系电话应为11位数字")
    private String insuredPhone;

    @NotNull(message = "保额不能为空")
    @DecimalMin(value = "0.01", message = "保额必须大于0")
    @Digits(integer = 12, fraction = 2, message = "保额最多12位整数、2位小数")
    private BigDecimal coverageAmount;
}
