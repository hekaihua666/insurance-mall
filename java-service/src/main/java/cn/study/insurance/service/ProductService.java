package cn.study.insurance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import cn.study.insurance.dto.ProductResponse;
import cn.study.insurance.entity.Product;
import cn.study.insurance.mapper.ProductMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * 产品业务层。
 *
 * 使用 MyBatis-Plus 查询在售商品，按产品 ID 升序返回，可按分类筛选。
 */
@Service
public class ProductService {

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    /** 按分类查上架产品；category 为空则查全部 */
    public List<ProductResponse> listProducts(String category) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .eq(Product::getStatus, "active")
                .eq(category != null && !category.isEmpty(), Product::getCategory, category)
                .orderByAsc(Product::getId);

        return toResponses(productMapper.selectList(wrapper));
    }

    private List<ProductResponse> toResponses(List<Product> products) {
        return products.stream().map(this::toResponse).toList();
    }

    private ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        // 普通字段批量复制，highlights 类型不同（String[] → List<String>）单独处理
        BeanUtils.copyProperties(product, response, "highlights");
        String[] highlights = product.getHighlights();
        response.setHighlights(highlights == null ? List.of() : Arrays.asList(highlights));
        return response;
    }
}
