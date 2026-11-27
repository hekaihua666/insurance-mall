package cn.study.insurance.controller;

import cn.study.insurance.common.GlobalExceptionHandler;
import cn.study.insurance.security.AuthInterceptor;
import cn.study.insurance.service.PolicyService;
import cn.study.insurance.service.PremiumQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 验证真实 MVC 参数绑定及校验链，业务依赖全部模拟。 */
class RequestValidationTest {
    private final PremiumQuoteService quotes = mock(PremiumQuoteService.class);
    private final PolicyService policies = mock(PolicyService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PolicyController(quotes, policies))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/v1/quotes").requestAttr(AuthInterceptor.USER_ID_ATTR, 9L)
                .contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(quotes);
    }

    @Test
    void negativeCoverageNeverReachesTheQuoteService() throws Exception {
        mvc.perform(post("/api/v1/quotes").requestAttr(AuthInterceptor.USER_ID_ATTR, 9L)
                .contentType("application/json").content("""
                    {"productId":2,"insuredName":"测试用户","insuredIdNo":"110101199001011234",
                     "insuredPhone":"13800000000","coverageAmount":-10000}
                    """))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(quotes);
    }
}
