package cn.study.insurance.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import org.apache.ibatis.type.ArrayTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 保险产品实体，映射 products 表。
 *
 * 只负责数据库映射，JSON 输出格式交给 ProductResponse，避免实体和接口契约耦合。
 * highlights 是 PostgreSQL 的 TEXT[]，用 ArrayTypeHandler 让驱动在查询时就转成 String[]，
 * 避免手写解析 PostgreSQL 数组字面量。
 */
@Getter
@Setter
@TableName(value = "products", autoResultMap = true)
public class Product {

    @TableId
    private Long id;

    private String name;

    @TableField("clause_name")
    private String clauseName;

    private String category;

    private String insurer;

    @TableField("image_url")
    private String imageUrl;

    private String description;

    @TableField("min_premium")
    private BigDecimal minPremium;

    @TableField("max_premium")
    private BigDecimal maxPremium;

    @TableField("target_group")
    private String targetGroup;

    /** PostgreSQL TEXT[]，由 JDBC 驱动在读取时转成 String[] */
    @TableField(jdbcType = JdbcType.ARRAY, typeHandler = ArrayTypeHandler.class)
    private String[] highlights;

    private String status;

    @TableField("created_at")
    private OffsetDateTime createdAt;

    @TableField("updated_at")
    private OffsetDateTime updatedAt;
}
