package cn.study.insurance.mapper;

import cn.study.insurance.entity.Policy;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PolicyMapper extends BaseMapper<Policy> {
}
