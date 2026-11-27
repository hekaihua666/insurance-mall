-- 精简迁移：删除零代码引用的表与列。
-- 执行前已备份：deploy/postgres/backup_before_slim_20261121.sql
-- 01/03 初始化脚本已同步改为精简后的结构，本脚本只用于把已有库改成同样结构。

BEGIN;

-- 1) premium_quotes：先摘掉指向待删表的外键列，再删其余无用的列
ALTER TABLE premium_quotes
    DROP COLUMN IF EXISTS pricing_schema_id,
    DROP COLUMN IF EXISTS schema_version,
    DROP COLUMN IF EXISTS adapter_id,
    DROP COLUMN IF EXISTS answers,
    DROP COLUMN IF EXISTS external_request,
    DROP COLUMN IF EXISTS external_response;

-- 2) 依次删表（按外键从属关系）
DROP TABLE IF EXISTS product_pricing_bindings;
DROP TABLE IF EXISTS insurer_api_adapters;
DROP TABLE IF EXISTS product_pricing_schemas;

-- 3) rate_table_items：先摘掉指向 rate_table_files 的外键列，再删无用列
ALTER TABLE rate_table_items
    DROP COLUMN IF EXISTS rate_file_id,
    DROP COLUMN IF EXISTS plan_code,
    DROP COLUMN IF EXISTS gender,
    DROP COLUMN IF EXISTS occupation_class,
    DROP COLUMN IF EXISTS social_security,
    DROP COLUMN IF EXISTS deductible,
    DROP COLUMN IF EXISTS dimensions,
    DROP COLUMN IF EXISTS source_page,
    DROP COLUMN IF EXISTS source_table,
    DROP COLUMN IF EXISTS created_at;

DROP TABLE IF EXISTS rate_table_files;
DROP TABLE IF EXISTS rate_plans;

-- 4) 删除已并入 Agent 问答的理赔表，以及无人使用的元数据表
DROP TABLE IF EXISTS claims;
DROP TABLE IF EXISTS app_metadata;

-- 5) orders：整张表删除。订单从没被任何接口读过，保单本身就是投保凭证
DROP TABLE IF EXISTS orders;

-- 6) policies：去掉从未读写的列，并补一列出单依据的报价（一份报价只能出一张保单）
ALTER TABLE policies
    ADD COLUMN IF NOT EXISTS quote_id UUID UNIQUE REFERENCES premium_quotes(id);

ALTER TABLE policies
    DROP COLUMN IF EXISTS application_no,
    DROP COLUMN IF EXISTS holder_name,
    DROP COLUMN IF EXISTS payment_frequency,
    DROP COLUMN IF EXISTS beneficiary,
    DROP COLUMN IF EXISTS coverage_snapshot,
    DROP COLUMN IF EXISTS paid_at,
    DROP COLUMN IF EXISTS updated_at;

-- 7) 索引：重建按年龄查费率用的索引
DROP INDEX IF EXISTS idx_rate_items_lookup;
CREATE INDEX IF NOT EXISTS idx_rate_items_lookup
    ON rate_table_items(product_id, age_min, age_max, status);

COMMIT;
