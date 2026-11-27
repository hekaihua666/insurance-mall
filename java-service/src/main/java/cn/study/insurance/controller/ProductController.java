package cn.study.insurance.controller;

import cn.study.insurance.dto.ProductResponse;
import cn.study.insurance.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 保险产品接口。
 *
 * 只做两件事：接住请求参数、把结果交给 Service 并返回。
 * 提供商城浏览和顾问使用的在售产品目录。
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 按分类查上架产品；category 为空则查全部 */
    @GetMapping
    public List<ProductResponse> products(@RequestParam(required = false) String category) {
        return productService.listProducts(category);
    }
}
