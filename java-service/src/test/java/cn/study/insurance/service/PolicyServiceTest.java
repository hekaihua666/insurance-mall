package cn.study.insurance.service;

import cn.study.insurance.entity.Policy;
import cn.study.insurance.entity.Product;
import cn.study.insurance.mapper.PolicyMapper;
import cn.study.insurance.mapper.PremiumQuoteMapper;
import cn.study.insurance.mapper.ProductMapper;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 历史保单按快照展示产品名，并正确显示已到期状态。 */
class PolicyServiceTest {
    private final PolicyMapper policies = mock(PolicyMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final PremiumQuoteMapper quotes = mock(PremiumQuoteMapper.class);
    private final PolicyService service = new PolicyService(policies, products, quotes);

    @Test
    void emptyPolicyListDoesNotQueryAnEmptyProductSet() {
        when(policies.selectList(any())).thenReturn(List.of());
        assertTrue(service.listPolicies(9L).isEmpty());
        verifyNoInteractions(products);
    }

    @Test
    void historicalNameAndExpiredStatusArePreserved() {
        Policy policy = new Policy();
        policy.setProductId(2L);
        policy.setStatus("active");
        policy.setExpiresAt(OffsetDateTime.now().minusDays(1));
        policy.setPolicySnapshot(Map.of("product_name", "签发时的产品名称"));
        Product renamed = new Product();
        renamed.setId(2L);
        renamed.setName("后来修改的商品名称");
        when(policies.selectList(any())).thenReturn(List.of(policy));
        when(products.selectBatchIds(any())).thenReturn(List.of(renamed));
        var response = service.listPolicies(9L).get(0);
        assertEquals("签发时的产品名称", response.getProductName());
        assertEquals("expired", response.getStatus());
    }
}
