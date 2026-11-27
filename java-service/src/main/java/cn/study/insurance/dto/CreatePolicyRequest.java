package cn.study.insurance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 出单入参。
 *
 * 只需要一个报价编号：被保人信息、保额、保费都在试算那一步存进报价里了。
 */
@Getter
@Setter
public class CreatePolicyRequest {

    @NotBlank(message = "报价不能为空")
    private String quoteId;
}
