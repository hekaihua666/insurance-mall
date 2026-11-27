package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.QuoteRequest;
import cn.study.insurance.dto.QuoteResponse;
import cn.study.insurance.entity.PremiumQuote;
import cn.study.insurance.entity.Product;
import cn.study.insurance.entity.RateTableItem;
import cn.study.insurance.mapper.PremiumQuoteMapper;
import cn.study.insurance.mapper.ProductMapper;
import cn.study.insurance.mapper.RateTableItemMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 保费试算。
 *
 * 流程：从身份证解出年龄 → 按产品查费率明细 → 按费率类型算保费 → 存报价留痕。
 * 费率三类：per_10000_sum_assured（rate_value × 保额/10000）、
 *           age_band_plan_premium（按年龄档取固定 premium）、
 *           fixed_plan_premium（按保额档取固定 premium）。
 * 身份证只在这里脱敏后入库，明文不落库。
 */
@Service
public class PremiumQuoteService {

    private final RateTableItemMapper rateTableItemMapper;
    private final PremiumQuoteMapper premiumQuoteMapper;
    private final ProductMapper productMapper;

    public PremiumQuoteService(RateTableItemMapper rateTableItemMapper,
                               PremiumQuoteMapper premiumQuoteMapper,
                               ProductMapper productMapper) {
        this.rateTableItemMapper = rateTableItemMapper;
        this.premiumQuoteMapper = premiumQuoteMapper;
        this.productMapper = productMapper;
    }
    // 保费试算
    @Transactional
    public QuoteResponse quote(Long userId, QuoteRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !"active".equals(product.getStatus())) {
            throw BizException.notFound("产品不存在或已下架");
        }
        // 解身份证出生日期为年龄
        int age = extractAge(request.getInsuredIdNo());
        //查该产品所有的费率明细
        List<RateTableItem> items = rateTableItemMapper.selectList(
                new LambdaQueryWrapper<RateTableItem>()
                        .eq(RateTableItem::getProductId, request.getProductId())
                        .eq(RateTableItem::getStatus, "active"));
        if (items.isEmpty()) {
            throw new BizException("该产品暂无费率数据，暂不支持试算");
        }

        //按年龄和保额匹配费率明细，挑出唯一命中的那一行
        RateTableItem matched = match(items, age, request.getCoverageAmount());
        //算保费
        BigDecimal premium = calcPremium(matched, request.getCoverageAmount());
        //存报价留痕
        PremiumQuote quote = new PremiumQuote();
        quote.setId(UUID.randomUUID());
        quote.setUserId(userId);
        quote.setProductId(request.getProductId());
        quote.setRequestParams(buildRequestParams(request, age));
        quote.setResult(buildResult(matched));
        quote.setTotalPremium(premium);
        quote.setPricingMode("table");
        quote.setExpiresAt(OffsetDateTime.now().plusDays(30));
        premiumQuoteMapper.insert(quote);
        //构建返回结果
        QuoteResponse response = new QuoteResponse();
        response.setQuoteId(quote.getId().toString());
        response.setProductId(product.getId());
        response.setProductName(product.getName());
        response.setTotalPremium(premium);
        response.setPricingMode("table");
        response.setRateValue(matched.getRateValue());
        response.setCoveragePeriod(matched.getCoveragePeriod());
        response.setPaymentPeriod(matched.getPaymentPeriod());
        return response;
    }

    // 按费率类型挑出命中用户参数的那一行。挑不出唯一一行就报错，不猜。
    private RateTableItem match(List<RateTableItem> items, int age, BigDecimal coverageAmount) {
        // 所有类型都核对年龄和保额；NULL 表示该费率不限制对应维度。
        List<RateTableItem> matches = items.stream()
                .filter(i -> i.getAgeMin() == null || age >= i.getAgeMin())
                .filter(i -> i.getAgeMax() == null || age <= i.getAgeMax())
                .filter(i -> i.getCoverageAmount() == null
                        || i.getCoverageAmount().compareTo(coverageAmount) == 0)
                .toList();
        // 0 行是用户填错、多行是数据配错，两种情况给不同提示
        if (matches.size() != 1) {
            throw new BizException(matchErrorMessage(items, matches));
        }
        return matches.get(0);
    }

    // 拼报错话术：没命中的时候要告诉用户可选保额有哪些，否则他不知道该改成多少
    private String matchErrorMessage(List<RateTableItem> items, List<RateTableItem> matches) {
        if (matches.size() > 1) {
            return "存在多个费率档位，需要补充投保条件后再试算";
        }
        // 从该产品的全部档位里统计可选保额，重疾那种不限保额的行（NULL）要滤掉
        String amounts = items.stream().map(RateTableItem::getCoverageAmount)
                .filter(Objects::nonNull).distinct().map(BigDecimal::toPlainString)
                .collect(Collectors.joining("、"));
        // amounts 为空说明这个产品压根不限保额，那问题只可能出在年龄上
        return "年龄或保额不符合该产品的费率档位"
                + (amounts.isEmpty() ? "，请核对投保条件" : "，可选保额：" + amounts + "元");
    }

    // 按费率类型算保费
    private BigDecimal calcPremium(RateTableItem item, BigDecimal coverageAmount) {
        if ("per_10000_sum_assured".equals(item.getRateType())) {
            // 费率表配错行会导致这里空指针，直接变成 500，先挡成能看懂的提示
            if (item.getRateValue() == null) {
                throw new BizException("该产品费率配置不完整，请联系客服");
            }
            // 先乘后除，只在最终金额舍入，避免保额单位提前舍入造成少算。
            return item.getRateValue().multiply(coverageAmount)
                    .divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP);
        }
        if ("age_band_plan_premium".equals(item.getRateType())
                || "fixed_plan_premium".equals(item.getRateType())) {
            if (item.getPremium() == null) {
                throw new BizException("该产品费率配置不完整，请联系客服");
            }
            return item.getPremium();
        }
        throw new BizException("暂不支持该费率类型");
    }

    private Map<String, Object> buildRequestParams(QuoteRequest request, int age) {
        Map<String, Object> params = new HashMap<>();
        params.put("insured_name", request.getInsuredName());
        params.put("insured_id_no_masked", maskIdNo(request.getInsuredIdNo()));
        params.put("insured_phone", request.getInsuredPhone());
        params.put("coverage_amount", request.getCoverageAmount());
        params.put("age", age);
        return params;
    }

    private Map<String, Object> buildResult(RateTableItem item) {
        Map<String, Object> result = new HashMap<>();
        result.put("rate_type", item.getRateType());
        result.put("rate_table_item_id", item.getId());
        result.put("coverage_period", item.getCoveragePeriod());
        result.put("payment_period", item.getPaymentPeriod());
        if (item.getRateValue() != null) {
            result.put("rate_value", item.getRateValue());
        }
        if (item.getPremium() != null) {
            result.put("premium", item.getPremium());
        }
        return result;
    }

    /** 校验完整出生日期，并按已满周岁选择年龄档位。这里只做格式校验，不替代身份核验。 */
    private int extractAge(String idNo) {
        LocalDate birth;
        try {
            birth = LocalDate.parse(idNo.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new BizException("身份证号出生日期不正确");
        }
        LocalDate today = LocalDate.now();
        if (birth.getYear() < 1900 || birth.isAfter(today)) {
            throw new BizException("身份证号出生日期不正确");
        }
        // Period 对 2 月 29 日出生的人在非闰年的 2 月 28 日会算成还没过生日（少 1 岁）。
        // 用「加满 N 年之后是否还没到今天」再确认一次，卡在费率档位边界上就不会错档。
        int age = Period.between(birth, today).getYears();
        if (!birth.plusYears(age + 1L).isAfter(today)) {
            age++;
        }
        return age;
    }

    /** 脱敏：前 6 位地区 + 后 4 位，中间打码 */
    private String maskIdNo(String idNo) {
        return idNo.substring(0, 6) + "********" + idNo.substring(14);
    }
}
