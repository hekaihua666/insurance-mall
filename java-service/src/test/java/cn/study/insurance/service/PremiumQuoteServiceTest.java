package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.QuoteRequest;
import cn.study.insurance.entity.Product;
import cn.study.insurance.entity.RateTableItem;
import cn.study.insurance.mapper.PremiumQuoteMapper;
import cn.study.insurance.mapper.ProductMapper;
import cn.study.insurance.mapper.RateTableItemMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** 固定产品及费率输入，验证计价规则；不使用真实身份证、数据库或保险公司接口。 */
class PremiumQuoteServiceTest {
    private final RateTableItemMapper rates = mock(RateTableItemMapper.class);
    private final PremiumQuoteMapper quotes = mock(PremiumQuoteMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final PremiumQuoteService service = new PremiumQuoteService(rates, quotes, products);
    private QuoteRequest request;

    @BeforeEach
    void setUp() {
        Product product = new Product();
        product.setId(2L);
        product.setName("回归测试产品");
        product.setStatus("active");
        when(products.selectById(2L)).thenReturn(product);
        request = new QuoteRequest();
        request.setProductId(2L);
        request.setInsuredName("测试用户");
        request.setInsuredIdNo("110101199001011234");
        request.setInsuredPhone("13800000000");
        request.setCoverageAmount(new BigDecimal("500000"));
    }

    @Test
    void nonPositiveCoverageIsRejectedAtTheRequestBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            for (String amount : List.of("0", "-10000")) {
                request.setCoverageAmount(new BigDecimal(amount));
                assertFalse(factory.getValidator().validate(request).isEmpty(), amount);
            }
        }
    }

    @Test
    void invalidCalendarDateIsRejected() {
        when(rates.selectList(any())).thenReturn(List.of(rate("per_10000_sum_assured", 0, 100, "8.2", null, null)));
        request.setInsuredIdNo("110101199002301234");
        assertThrows(BizException.class, () -> service.quote(9L, request));
        verifyNoInteractions(quotes);
    }

    @Test
    void birthdayBoundaryUsesCompletedYearsRatherThanYearSubtraction() {
        LocalDate birth = LocalDate.now().minusYears(31).plusDays(1);
        request.setInsuredIdNo("110101" + birth.format(DateTimeFormatter.BASIC_ISO_DATE) + "1234");
        when(rates.selectList(any())).thenReturn(List.of(
                rate("per_10000_sum_assured", 18, 30, "8.2", null, null),
                rate("per_10000_sum_assured", 31, 40, "11.22", null, null)));
        assertEquals(new BigDecimal("410.00"), service.quote(9L, request).getTotalPremium());
    }

    @Test
    void medicalPlanCannotQuoteAnotherCoverageAmount() {
        when(rates.selectList(any())).thenReturn(List.of(rate("age_band_plan_premium", 0, 100, null, "350", "1000000")));
        assertThrows(BizException.class, () -> service.quote(9L, request));
        verifyNoInteractions(quotes);
    }

    @Test
    void medicalPlanWithMatchingCoverageUsesItsFixedPremium() {
        request.setCoverageAmount(new BigDecimal("1000000"));
        when(rates.selectList(any())).thenReturn(List.of(rate("age_band_plan_premium", 0, 100, null, "350", "1000000")));
        assertEquals(0, new BigDecimal("350").compareTo(service.quote(9L, request).getTotalPremium()));
    }

    @Test
    void proportionalPremiumRoundsOnlyTheFinalAmount() {
        request.setCoverageAmount(new BigDecimal("10001"));
        when(rates.selectList(any())).thenReturn(List.of(rate("per_10000_sum_assured", 0, 100, "116", null, null)));
        assertEquals(new BigDecimal("116.01"), service.quote(9L, request).getTotalPremium());
    }

    @Test
    void unsupportedAgeIsRejectedWithoutSavingAQuote() {
        when(rates.selectList(any())).thenReturn(List.of(rate("per_10000_sum_assured", 0, 17, "8.2", null, null)));
        assertThrows(BizException.class, () -> service.quote(9L, request));
        verifyNoInteractions(quotes);
    }

    private RateTableItem rate(String type, Integer ageMin, Integer ageMax, String value, String premium, String coverage) {
        RateTableItem rate = new RateTableItem();
        rate.setRateType(type);
        rate.setAgeMin(ageMin);
        rate.setAgeMax(ageMax);
        rate.setRateValue(value == null ? null : new BigDecimal(value));
        rate.setPremium(premium == null ? null : new BigDecimal(premium));
        rate.setCoverageAmount(coverage == null ? null : new BigDecimal(coverage));
        return rate;
    }
}
