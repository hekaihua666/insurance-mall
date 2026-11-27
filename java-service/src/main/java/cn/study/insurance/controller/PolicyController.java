package cn.study.insurance.controller;

import cn.study.insurance.common.ApiResponse;
import cn.study.insurance.dto.CreatePolicyRequest;
import cn.study.insurance.dto.PolicyResponse;
import cn.study.insurance.dto.QuoteRequest;
import cn.study.insurance.dto.QuoteResponse;
import cn.study.insurance.security.AuthInterceptor;
import cn.study.insurance.service.PolicyService;
import cn.study.insurance.service.PremiumQuoteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 投保接口：试算 + 出单 + 保单列表。
 *
 * 两段式：POST /quotes 试算拿报价，POST /policies 拿这个报价直接出单。
 * 没有单独的「下单」动作——买保险的终点就是拿到保单。
 */
@RestController
@RequestMapping("/api/v1")
public class PolicyController {

    private final PremiumQuoteService premiumQuoteService;
    private final PolicyService policyService;

    public PolicyController(PremiumQuoteService premiumQuoteService, PolicyService policyService) {
        this.premiumQuoteService = premiumQuoteService;
        this.policyService = policyService;
    }
    // 试算
    @PostMapping("/quotes")
    public ApiResponse<QuoteResponse> quote(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                            @Valid @RequestBody QuoteRequest request) {
        return ApiResponse.ok(premiumQuoteService.quote(userId, request));
    }
    // 出单
    @PostMapping("/policies")
    public ApiResponse<PolicyResponse> issuePolicy(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId,
                                                   @Valid @RequestBody CreatePolicyRequest request) {
        return ApiResponse.ok(policyService.issue(userId, request));
    }
    // 保单列表
    @GetMapping("/policies")
    public ApiResponse<List<PolicyResponse>> listPolicies(@RequestAttribute(AuthInterceptor.USER_ID_ATTR) Long userId) {
        return ApiResponse.ok(policyService.listPolicies(userId));
    }
}
