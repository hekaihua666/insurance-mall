package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.CreatePolicyRequest;
import cn.study.insurance.dto.PolicyResponse;
import cn.study.insurance.entity.Policy;
import cn.study.insurance.entity.PremiumQuote;
import cn.study.insurance.entity.Product;
import cn.study.insurance.mapper.PolicyMapper;
import cn.study.insurance.mapper.PremiumQuoteMapper;
import cn.study.insurance.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 保单：出单 + 查询。
 *
 * 出单一步到位：拿试算得到的 quoteId，把报价里的被保人信息和金额冻结成一份保单。
 * 没有单独的订单表——「买了一份保险」这件事，保单就是那张凭证。
 */
@Service
public class PolicyService {

    private final PolicyMapper policyMapper;
    private final ProductMapper productMapper;
    private final PremiumQuoteMapper premiumQuoteMapper;

    public PolicyService(PolicyMapper policyMapper, ProductMapper productMapper,
                         PremiumQuoteMapper premiumQuoteMapper) {
        this.policyMapper = policyMapper;
        this.productMapper = productMapper;
        this.premiumQuoteMapper = premiumQuoteMapper;
    }

    /** 按报价出单：校验报价 → 冻结参数 → 写一条保单，一个事务提交。 */
    @Transactional
    public PolicyResponse issue(Long userId, CreatePolicyRequest request) {
        UUID quoteId;
        try {
            quoteId = UUID.fromString(request.getQuoteId());
        } catch (IllegalArgumentException e) {
            throw new BizException("报价编号格式不正确");
        }

        PremiumQuote quote = premiumQuoteMapper.selectById(quoteId);
        if (quote == null || !userId.equals(quote.getUserId())) {
            throw BizException.notFound("报价不存在");
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (quote.getExpiresAt() != null && !quote.getExpiresAt().isAfter(now)) {
            throw new BizException("报价已过期，请重新试算");
        }

        // 前端禁用按钮只能防连点，数据库成单号唯一键负责阻止重试重复出单。
        if (policyMapper.selectCount(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getQuoteId, quoteId)) > 0) {
            throw BizException.conflict("该报价已投保，请前往我的保单查看");
        }

        Map<String, Object> params = quote.getRequestParams();
        // 同一个产品，一个人只能买一份：被保人身份证相同就算同一个人（换个人买是允许的）。
        // 只拦「有效」保单——已到期或已终止的不占名额，否则今年买过明年就再也买不了了。
        String insuredIdNo = (String) params.get("insured_id_no_masked");
        if (policyMapper.selectCount(new LambdaQueryWrapper<Policy>()
                .eq(Policy::getUserId, userId)
                .eq(Policy::getProductId, quote.getProductId())
                .eq(Policy::getInsuredIdNoMasked, insuredIdNo)
                .eq(Policy::getStatus, "active")
                // 终身险的 expires_at 是 NULL，必须算作未到期，漏掉这条会把终身险判成已过期
                .and(w -> w.isNull(Policy::getExpiresAt).or().gt(Policy::getExpiresAt, now))) > 0) {
            throw BizException.conflict("该被保人已持有此产品的有效保单，无需重复投保");
        }

        Product product = productMapper.selectById(quote.getProductId());
        if (product == null || !"active".equals(product.getStatus())) {
            throw BizException.notFound("产品不存在或已下架，请重新选择");
        }
        String productName = product.getName();

        // 旧报价缺少期限快照时要求重算，不能擅自把终身产品变成一年期。
        String coveragePeriod = quote.getResult() == null ? null
                : (String) quote.getResult().get("coverage_period");
        OffsetDateTime expiresAt;
        if ("lifetime".equals(coveragePeriod)) {
            expiresAt = null;
        } else if ("1y".equals(coveragePeriod)) {
            expiresAt = now.plusYears(1);
        } else {
            throw new BizException("报价缺少支持的保障期限，请重新试算");
        }

        // jsonb 读回来的数字是 Integer/Long，不能直接强转 BigDecimal，走字符串中转
        BigDecimal coverageAmount = new BigDecimal(String.valueOf(params.get("coverage_amount")));

        UUID policyId = UUID.randomUUID();
        String policyNumber = "P" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                + policyId.toString().replace("-", "");

        Policy policy = new Policy();
        policy.setId(policyId);
        policy.setUserId(userId);
        policy.setProductId(quote.getProductId());
        policy.setQuoteId(quoteId);
        policy.setPolicyNumber(policyNumber);
        policy.setStatus("active");
        policy.setEffectiveAt(now);
        policy.setExpiresAt(expiresAt);
        policy.setInsuredName((String) params.get("insured_name"));
        policy.setInsuredIdNoMasked((String) params.get("insured_id_no_masked"));
        policy.setInsuredPhone((String) params.get("insured_phone"));
        policy.setCoverageAmount(coverageAmount);
        policy.setPremiumAmount(quote.getTotalPremium());
        policy.setPolicySnapshot(Map.of("product_name", productName, "coverage_period", coveragePeriod));
        policy.setIssuedAt(now);
        policyMapper.insert(policy);

        return toResponse(policy, productName);
    }

    public List<PolicyResponse> listPolicies(Long userId) {
        List<Policy> policies = policyMapper.selectList(
                new LambdaQueryWrapper<Policy>()
                        .eq(Policy::getUserId, userId)
                        .orderByDesc(Policy::getCreatedAt));
        // 没有保单直接返回，selectBatchIds 传空集合会生成残缺 SQL（IN ()）
        if (policies.isEmpty()) {
            return List.of();
        }
        // 批量查询产品名
        Map<Long, String> productNames = productMapper.selectBatchIds(
                        policies.stream().map(Policy::getProductId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Product::getId, Product::getName));

        return policies.stream().map(p -> toResponse(p, productNames.get(p.getProductId()))).toList();
    }

    private PolicyResponse toResponse(Policy policy, String productName) {
        PolicyResponse response = new PolicyResponse();
        response.setPolicyNumber(policy.getPolicyNumber());
        response.setProductId(policy.getProductId());
        // 历史保单沿用出单快照，商品改名不应重写已签发的保单名称。
        Object savedName = policy.getPolicySnapshot() == null ? null
                : policy.getPolicySnapshot().get("product_name");
        response.setProductName(savedName instanceof String name ? name
                : productName == null ? "未知产品" : productName);
        boolean expired = "active".equals(policy.getStatus()) && policy.getExpiresAt() != null
                && !policy.getExpiresAt().isAfter(OffsetDateTime.now());
        response.setStatus(expired ? "expired" : policy.getStatus());
        response.setEffectiveAt(policy.getEffectiveAt());
        response.setExpiresAt(policy.getExpiresAt());
        response.setInsuredName(policy.getInsuredName());
        response.setInsuredIdNoMasked(policy.getInsuredIdNoMasked());
        response.setCoverageAmount(policy.getCoverageAmount());
        response.setPremiumAmount(policy.getPremiumAmount());
        return response;
    }
}
