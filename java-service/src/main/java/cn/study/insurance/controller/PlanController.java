package cn.study.insurance.controller;

import cn.study.insurance.common.ApiResponse;
import cn.study.insurance.dto.PlanResponse;
import cn.study.insurance.dto.PlanSummaryResponse;
import cn.study.insurance.security.AuthInterceptor;
import cn.study.insurance.service.PlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保险方案接口。
 *
 * Python Agent 保存推荐方案，Java 按登录用户查询列表和详情。
 */
@RestController
@RequestMapping("/api/v1/insurance-plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    public ApiResponse<List<PlanSummaryResponse>> listPlans(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId) {
        return ApiResponse.ok(planService.listPlans(userId));
    }

    /** 查方案详情，路径与前端原有调用保持一致 */
    @GetMapping("/{planId}")
    public ApiResponse<PlanResponse> getPlan(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                             @PathVariable String planId) {
        return ApiResponse.ok(planService.getPlan(userId, planId));
    }

}
