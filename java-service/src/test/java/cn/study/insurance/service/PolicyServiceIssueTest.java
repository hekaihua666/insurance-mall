package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.CreatePolicyRequest;
import cn.study.insurance.entity.Policy;
import cn.study.insurance.entity.PremiumQuote;
import cn.study.insurance.entity.Product;
import cn.study.insurance.mapper.PolicyMapper;
import cn.study.insurance.mapper.PremiumQuoteMapper;
import cn.study.insurance.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 模拟报价和产品，验证出单前置条件及保单期限；不会落任何真实数据。 */
class PolicyServiceIssueTest {
    private final PremiumQuoteMapper quotes = mock(PremiumQuoteMapper.class);
    private final PolicyMapper policies = mock(PolicyMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final PolicyService service = new PolicyService(policies, products, quotes);
    private final CreatePolicyRequest request = new CreatePolicyRequest();
    private PremiumQuote quote;
    private Product product;

    @BeforeEach
    void setUp() {
        UUID id = UUID.randomUUID();
        request.setQuoteId(id.toString());
        quote = new PremiumQuote();
        quote.setId(id);
        quote.setUserId(9L);
        quote.setProductId(2L);
        quote.setExpiresAt(OffsetDateTime.now().plusDays(1));
        quote.setTotalPremium(new BigDecimal("410"));
        quote.setRequestParams(Map.of("insured_name", "测试用户", "insured_id_no_masked", "110101********1234",
                "insured_phone", "13800000000", "coverage_amount", 500000));
        quote.setResult(Map.of("coverage_period", "1y", "payment_period", "annual"));
        when(quotes.selectById(id)).thenReturn(quote);
        product = new Product();
        product.setId(2L);
        product.setName("测试产品");
        product.setStatus("active");
        when(products.selectById(2L)).thenReturn(product);
    }

    @Test
    void expiredQuoteCannotIssueAPolicy() {
        quote.setExpiresAt(OffsetDateTime.now().minusMinutes(1));
        assertThrows(BizException.class, () -> service.issue(9L, request));
        verify(policies, never()).insert(any(Policy.class));
    }

    @Test
    void anotherUsersQuoteCannotBeUsed() {
        assertThrows(BizException.class, () -> service.issue(10L, request));
        verifyNoInteractions(policies);
    }

    @Test
    void inactiveProductCannotBeIssuedFromAnOldQuote() {
        product.setStatus("inactive");
        assertThrows(BizException.class, () -> service.issue(9L, request));
        verify(policies, never()).insert(any(Policy.class));
    }

    @Test
    void sameQuoteCannotIssueAnotherPolicy() {
        when(policies.selectCount(any())).thenReturn(1L);
        BizException error = assertThrows(BizException.class, () -> service.issue(9L, request));
        assertEquals(409, error.getStatus());
        verify(policies, never()).insert(any(Policy.class));
    }

    @Test
    void sameInsuredCannotHoldTwoPoliciesOfTheSameProduct() {
        // 第一次 selectCount 是查「这份报价出过单没」，第二次才是查「这个人是否已有同产品有效保单」
        when(policies.selectCount(any())).thenReturn(0L, 1L);
        BizException error = assertThrows(BizException.class, () -> service.issue(9L, request));
        assertEquals(409, error.getStatus());
        verify(policies, never()).insert(any(Policy.class));
    }

    @Test
    void legacyQuoteWithoutCoverageSnapshotMustBeRequoted() {
        quote.setResult(Map.of("rate_type", "per_10000_sum_assured"));
        assertThrows(BizException.class, () -> service.issue(9L, request));
        verify(policies, never()).insert(any(Policy.class));
    }

    @Test
    void lifetimeCoverageDoesNotBecomeAOneYearPolicy() {
        quote.setResult(Map.of("coverage_period", "lifetime", "payment_period", "20y"));
        service.issue(9L, request);
        ArgumentCaptor<Policy> saved = ArgumentCaptor.forClass(Policy.class);
        verify(policies).insert(saved.capture());
        assertNull(saved.getValue().getExpiresAt());
    }

    @Test
    void annualPolicyUsesOneConsistentEffectiveTimestamp() {
        service.issue(9L, request);
        ArgumentCaptor<Policy> saved = ArgumentCaptor.forClass(Policy.class);
        verify(policies).insert(saved.capture());
        assertEquals(saved.getValue().getEffectiveAt().plusYears(1), saved.getValue().getExpiresAt());
    }

    @Test
    void issuedPolicyKeepsTheQuoteItCameFrom() {
        service.issue(9L, request);
        ArgumentCaptor<Policy> saved = ArgumentCaptor.forClass(Policy.class);
        verify(policies).insert(saved.capture());
        assertEquals(UUID.fromString(request.getQuoteId()), saved.getValue().getQuoteId());
    }
}
