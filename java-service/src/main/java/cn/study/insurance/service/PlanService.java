package cn.study.insurance.service;

import cn.study.insurance.common.BizException;
import cn.study.insurance.dto.PlanItemResponse;
import cn.study.insurance.dto.PlanResponse;
import cn.study.insurance.dto.PlanSummaryResponse;
import cn.study.insurance.entity.InsurancePlan;
import cn.study.insurance.entity.InsurancePlanItem;
import cn.study.insurance.entity.Product;
import cn.study.insurance.mapper.InsurancePlanItemMapper;
import cn.study.insurance.mapper.InsurancePlanMapper;
import cn.study.insurance.mapper.ProductMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 仅查询当前登录用户的推荐方案。 */
@Service
public class PlanService {

    private final InsurancePlanMapper planMapper;
    private final InsurancePlanItemMapper itemMapper;
    private final ProductMapper productMapper;

    public PlanService(InsurancePlanMapper planMapper,
                       InsurancePlanItemMapper itemMapper,
                       ProductMapper productMapper) {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.productMapper = productMapper;
    }

    /** 按用户隔离并按保存时间倒序，列表无需查询方案项。 */
    public List<PlanSummaryResponse> listPlans(Long userId) {
        return planMapper.selectList(new LambdaQueryWrapper<InsurancePlan>()
                        .select(InsurancePlan::getId, InsurancePlan::getPlanName, InsurancePlan::getSummary,
                                InsurancePlan::getAnnualPremiumBudget, InsurancePlan::getCreatedAt)
                        .eq(InsurancePlan::getUserId, userId)
                        .orderByDesc(InsurancePlan::getCreatedAt, InsurancePlan::getId))
                .stream()
                .map(plan -> new PlanSummaryResponse(plan.getId(), plan.getPlanName(), plan.getSummary(),
                        plan.getAnnualPremiumBudget(), plan.getCreatedAt()))
                .toList();
    }

    /** 查方案详情，带产品名称和险种；查不到或不属于当前用户都返回 404 */
    public PlanResponse getPlan(Long userId, String planId) {
        InsurancePlan plan = findOwnedPlan(userId, planId);

        List<InsurancePlanItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<InsurancePlanItem>()
                        .eq(InsurancePlanItem::getPlanId, plan.getId())
                        .orderByAsc(InsurancePlanItem::getPriority));

        // 产品信息一次性批量查出来，避免循环里逐个查库
        Map<Long, Product> productMap = loadProducts(items);

        PlanResponse response = new PlanResponse();
        response.setId(plan.getId().toString());
        response.setPlanName(plan.getPlanName());
        response.setSummary(plan.getSummary());
        response.setAnnualPremiumBudget(plan.getAnnualPremiumBudget());
        response.setItems(items.stream().map(item -> toItemResponse(item, productMap)).toList());
        return response;
    }

    private InsurancePlan findOwnedPlan(Long userId, String planId) {
        UUID id = parseUuid(planId, "方案不存在");
        InsurancePlan plan = planMapper.selectOne(
                new LambdaQueryWrapper<InsurancePlan>()
                        .eq(InsurancePlan::getId, id)
                        .eq(InsurancePlan::getUserId, userId));
        if (plan == null) {
            throw BizException.notFound("方案不存在");
        }
        return plan;
    }

    /** 把路径参数转成 UUID，格式不对时按"不存在"处理，不把解析异常抛给前端 */
    private UUID parseUuid(String value, String errorMessage) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw BizException.notFound(errorMessage);
        }
    }

    private Map<Long, Product> loadProducts(List<InsurancePlanItem> items) {
        List<Long> productIds = items.stream()
                .map(InsurancePlanItem::getProductId)
                .distinct()
                .toList();
        if (productIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return productMapper.selectList(
                        new LambdaQueryWrapper<Product>().in(Product::getId, productIds))
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private PlanItemResponse toItemResponse(InsurancePlanItem item, Map<Long, Product> productMap) {
        Product product = productMap.get(item.getProductId());
        PlanItemResponse response = new PlanItemResponse();
        response.setId(item.getId().toString());
        response.setProductId(item.getProductId());
        // 产品可能已下架，查不到时给占位文案，不让接口报错
        response.setProductName(product != null ? product.getName() : "未知产品");
        response.setCategory(product != null ? product.getCategory() : "");
        response.setPriority(item.getPriority());
        response.setRecommendationReason(item.getRecommendationReason());
        response.setAnnualPremiumBudget(item.getAnnualPremiumBudget());
        return response;
    }

}
