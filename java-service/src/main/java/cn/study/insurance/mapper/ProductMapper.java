package cn.study.insurance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.study.insurance.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 产品 Mapper。
 *
 * 继承 BaseMapper 后，单表增删改查（selectById / selectList / insert ...）全部自带，
 * 不需要写 SQL。复杂查询用条件构造器在 Service 里表达。
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
