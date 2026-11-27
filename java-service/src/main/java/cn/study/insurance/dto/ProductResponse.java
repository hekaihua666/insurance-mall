package cn.study.insurance.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * 产品出参。
 *
 * 字段名、类型刻意与 Python 版 ProductResponse 保持一致：
 * - 保费统一序列化成字符串（Python 的 Pydantic 对 Decimal 就是这么输出的）
 * - highlights 输出为字符串数组
 * 这样前端和 Agent 在 Java/Python 两版之间切换时无感知。
 */
@Getter
@Setter
public class ProductResponse {

    private Long id;
    private String name;

    @JsonProperty("clause_name")
    private String clauseName;

    private String category;
    private String insurer;

    @JsonProperty("image_url")
    private String imageUrl;

    private String description;

    @JsonProperty("min_premium")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal minPremium;

    @JsonProperty("max_premium")
    @JsonSerialize(using = ToStringSerializer.class)
    private BigDecimal maxPremium;

    @JsonProperty("target_group")
    private String targetGroup;

    private List<String> highlights;
}
