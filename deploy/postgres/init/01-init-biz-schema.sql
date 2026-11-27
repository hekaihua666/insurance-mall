-- Initializes insurance_biz tables and demo data.
-- 精简版：只保留代码真正读写的表与列。
-- 已删除的表：app_metadata、claims、orders、rate_plans、rate_table_files、
--             product_pricing_schemas、insurer_api_adapters、product_pricing_bindings。
-- 已删除的列：policies 7 列、premium_quotes 6 列、rate_table_items 10 列。

-- ==== V2__core_schema.sql ====
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    email VARCHAR(160) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    clause_name VARCHAR(300) NOT NULL,
    category VARCHAR(50) NOT NULL,
    insurer VARCHAR(120) NOT NULL,
    image_url TEXT,
    description TEXT,
    min_premium DECIMAL(14,2),
    max_premium DECIMAL(14,2),
    target_group VARCHAR(300),
    highlights TEXT[],
    status VARCHAR(30) NOT NULL DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_products_category CHECK (category IN ('medical', 'critical_illness', 'accident', 'life'))
);

-- 费率明细：一个产品 × 一个年龄档 = 一行。
-- rate_value 给重疾/寿险用（每万元保额的费率，要乘保额），premium 给医疗/意外用（一口价）。
CREATE TABLE IF NOT EXISTS rate_table_items (
    id UUID PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    rate_type VARCHAR(50) NOT NULL,
    age_min INT,
    age_max INT,
    payment_period VARCHAR(50),
    coverage_period VARCHAR(50),
    coverage_amount DECIMAL(14,2),
    rate_value DECIMAL(18,6),
    premium DECIMAL(14,2),
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    CONSTRAINT chk_rate_table_items_status CHECK (status IN ('active', 'inactive')),
    CONSTRAINT chk_rate_table_items_non_negative CHECK (
        (rate_value IS NULL OR rate_value >= 0)
        AND (premium IS NULL OR premium >= 0)
    )
);

-- 试算报价：算一次留一条，下单时只引用这条的 id。
CREATE TABLE IF NOT EXISTS premium_quotes (
    id UUID PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    request_params JSONB NOT NULL,
    result JSONB NOT NULL,
    total_premium DECIMAL(14,2) NOT NULL,
    pricing_mode VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_premium_quotes_pricing_mode CHECK (pricing_mode IN ('table', 'demo_formula'))
);

CREATE INDEX IF NOT EXISTS idx_products_category ON products(category);
CREATE INDEX IF NOT EXISTS idx_products_status ON products(status);
CREATE INDEX IF NOT EXISTS idx_rate_items_lookup
    ON rate_table_items(product_id, age_min, age_max, status);
CREATE INDEX IF NOT EXISTS idx_premium_quotes_user ON premium_quotes(user_id);

-- ==== V3__seed_products.sql ====
INSERT INTO products (id, name, clause_name, category, insurer, image_url, description, min_premium, max_premium, target_group, highlights)
VALUES
(1, '达尔文宝贝计划12号', '信美相互星辰守护B款少儿重疾险条款.pdf', 'critical_illness', '信美人寿相互保险社', 'https://files.huizecdn.com/file3/M00/91/12/rBYA3Gi1gZ2AXBL7AAYFJxRhbPg411.png', '117种重疾+28种中症+45种轻症，少儿重疾保障灵活。', 285.30, 2501.83, '0-17周岁', ARRAY['117种重疾','少儿保障','可选投保人豁免']),
(2, '达尔文12号重大疾病保险', '复星联合优选K重疾险(达尔文12号)条款.pdf', 'critical_illness', '复星联合健康保险', 'https://files.huizecdn.com/file3/M00/90/E5/rBYA3Gi1BnWALIBtAAYFAsRjzwk030.png', '120种重疾+30种中症+45种轻症，可选多项责任。', 561.00, 4815.00, '28天-35周岁（1-4类职业）', ARRAY['120种重疾','中轻症保障','可选责任丰富']),
(3, '复星联合医联有盟重疾险', '复星联合医联有盟重疾险条款.pdf', 'critical_illness', '复星联合健康保险', 'https://files.huizecdn.com/file3/M00/95/DE/rBYA3GjL0m-AN888AAEX8uDe8d4708.png', '重疾保障产品，含主险及长期医疗附加保障。', 3185.00, 5111.00, '18-59周岁（1-4类职业）', ARRAY['重疾保障','可附加医疗']),
(4, '复星联合星相守2号长期医疗险（个人版）', '复星联合优选二号长期住院医疗险(星相守2号)条款.pdf', 'medical', '复星联合健康保险', 'https://files.huizecdn.com/file3/M00/AC/B9/rBYA3GlFEUWAWcX8AAO1o6m75Qg526.png', '免赔额灵活可选，保证续保20年特需，外购药不限清单。', 148.00, 3605.00, '1-55周岁（1-4类职业）', ARRAY['长期医疗','门急诊可选','家庭保障']),
(5, '复星联合星相守2号长期医疗险（转保版）', '复星联合优选二号长期住院医疗险(星相守2号)条款.pdf', 'medical', '复星联合健康保险', 'https://files.huizecdn.com/file3/M00/AC/B9/rBYA3GlFEUWAWcX8AAO1o6m75Qg526.png', '免赔额灵活可选，保证续保20年特需，外购药不限清单。', 180.00, 4409.00, '1-70周岁（1-4类职业）', ARRAY['转保场景','0免赔','特需医疗']),
(6, '众安尊享e生百万医疗险2026版', '众安尊享e生百万医疗险2026版条款.pdf', 'medical', '众安保险', 'https://files.huizecdn.com/file3/M00/B0/02/rBYA3GlTLeGAGS9sAAEwUtrU9VE280.png', '百万医疗险产品，覆盖住院、特殊门诊等医疗责任。', 255.00, 1420.00, '30天-70周岁（1-6类职业）', ARRAY['百万医疗','医疗保障','责任免除清晰']),
(7, '中英人寿福满佳C款（悦享版）', '中英人寿福满佳C款(悦享版)终身寿险分红型条款.pdf', 'life', '中英人寿', 'https://files.huizecdn.com/file3/M00/C1/9A/rBYA3GmJR8qAMR8dAAEJXoRqbAU476.png', '成长型分红险，更高预期收益。', 10000.00, NULL, '30天-57周岁（1-6类职业）', ARRAY['终身寿险','分红型','长期保障']),
(8, '太保福有余2025', '太平洋福有余2025终身寿险分红型条款.pdf', 'life', '太平洋人寿', 'https://files.huizecdn.com/file3/M00/AA/6B/rBYA3Gk34AKAZAQCAAEf3uIE2EQ251.png', '终身寿险分红型产品。', 5000.00, NULL, '5天-68周岁（1-6类职业）', ARRAY['终身寿险','分红型']),
(9, '新华人寿E增福优享版', '新华人寿E增福优享版终身寿险互联网条款.pdf', 'life', '新华人寿', 'https://files.huizecdn.com/file3/M00/8E/92/rBYA3GitLDaAPo2fAADxu5ximjs300.png', '互联网终身寿险产品。', 10000.00, NULL, '0-70周岁（1-6类职业）', ARRAY['终身寿险','互联网产品']),
(10, '太平洋小蜜蜂6号综合意外险', '太平洋小蜜蜂6号综合意外险条款.pdf', 'accident', '太平洋财险', 'https://files.huizecdn.com/file3/M00/97/98/rBYA3GjUtNOASrCtAAP85DM8wvE838.png', '综合意外险，含意外伤害及可选附加医疗、猝死责任。', 136.00, 298.00, '18-50周岁（1-3类职业）', ARRAY['综合意外','意外医疗','猝死附加']),
(11, '太平洋小蜜蜂6号玫瑰版综合意外险', '太平洋小蜜蜂6号综合意外险条款.pdf', 'accident', '太平洋财险', 'https://files.huizecdn.com/file3/M00/97/9A/rBYA3GjUuyyAHuBnAAQ7pqK0eTo763.png', '女性场景综合意外险，与标准版共享基础条款。', 169.00, 439.00, '18-50周岁（1-3类职业）', ARRAY['综合意外','女性场景','共享条款']),
(12, '人保小学童2号Pro学平险', '人保小学童2号Pro学平险学生幼儿意外伤害条款.pdf', 'accident', '中国人保财险', 'https://files.huizecdn.com/file3/M00/8A/2C/rBYA3Gia2NCAPoeMAAOJtnYK38s337.png', '学生幼儿综合保障，含意外、住院、重疾等附加责任。', 240.00, 335.00, '3-24周岁', ARRAY['学平险','意外保障','住院医疗','重疾附加'])
ON CONFLICT (id) DO NOTHING;

SELECT setval(pg_get_serial_sequence('products', 'id'), 12, true);

-- ==== V7__insurance_plans.sql ====
CREATE TABLE IF NOT EXISTS insurance_plans (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    plan_name VARCHAR(120) NOT NULL,
    summary TEXT,
    annual_premium_budget DECIMAL(14, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_insurance_plans_budget
        CHECK (annual_premium_budget IS NULL OR annual_premium_budget >= 0)
);

CREATE TABLE IF NOT EXISTS insurance_plan_items (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES insurance_plans(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id),
    priority INT NOT NULL DEFAULT 1 CHECK (priority >= 1),
    recommendation_reason TEXT,
    annual_premium_budget DECIMAL(14, 2),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_insurance_plan_items_budget
        CHECK (annual_premium_budget IS NULL OR annual_premium_budget >= 0),
    CONSTRAINT uk_insurance_plan_items_plan_product
        UNIQUE (plan_id, product_id)
);

CREATE INDEX IF NOT EXISTS idx_insurance_plans_user
    ON insurance_plans(user_id);

CREATE INDEX IF NOT EXISTS idx_insurance_plan_items_plan
    ON insurance_plan_items(plan_id);

-- ==== V12__policies.sql ====
-- 保单：出单即保单，一行 = 一份合同。
CREATE TABLE IF NOT EXISTS policies (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    product_id BIGINT NOT NULL REFERENCES products(id),
    quote_id UUID UNIQUE REFERENCES premium_quotes(id),
    policy_number VARCHAR(60) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'active',
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    insured_name VARCHAR(80),
    insured_id_no_masked VARCHAR(40),
    insured_phone VARCHAR(40),
    coverage_amount NUMERIC(14, 2),
    premium_amount NUMERIC(14, 2),
    policy_snapshot JSONB,
    issued_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_policies_status
        CHECK (status IN ('pending', 'active', 'expired', 'terminated'))
);

CREATE INDEX IF NOT EXISTS idx_policies_user_status_effective
    ON policies(user_id, status, effective_at DESC);

-- ==== 表与列注释 ====
COMMENT ON TABLE users IS '保险商城用户账户';
COMMENT ON COLUMN users.id IS '用户主键';
COMMENT ON COLUMN users.username IS '登录用户名';
COMMENT ON COLUMN users.email IS '用户邮箱';
COMMENT ON COLUMN users.password_hash IS '加密后的登录密码';
COMMENT ON COLUMN users.display_name IS '用户展示名称';
COMMENT ON COLUMN users.status IS '账户状态';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '最后更新时间';

COMMENT ON TABLE products IS '保险商城在售及历史保险产品';
COMMENT ON COLUMN products.id IS '产品主键';
COMMENT ON COLUMN products.name IS '商城展示名称';
COMMENT ON COLUMN products.clause_name IS '主条款文件名，包含 .pdf 后缀';
COMMENT ON COLUMN products.category IS '险种分类：医疗、重疾、意外或寿险';
COMMENT ON COLUMN products.insurer IS '承保保险公司';
COMMENT ON COLUMN products.image_url IS '产品展示图片地址';
COMMENT ON COLUMN products.description IS '产品简介';
COMMENT ON COLUMN products.min_premium IS '产品公开的最低年缴保费参考';
COMMENT ON COLUMN products.max_premium IS '产品公开的最高保费参考，可为空';
COMMENT ON COLUMN products.target_group IS '适用人群说明';
COMMENT ON COLUMN products.highlights IS '产品亮点列表';
COMMENT ON COLUMN products.status IS '产品状态';
COMMENT ON COLUMN products.created_at IS '创建时间';
COMMENT ON COLUMN products.updated_at IS '最后更新时间';

COMMENT ON TABLE rate_table_items IS '费率明细：一个产品 × 一个年龄档 = 一行';
COMMENT ON COLUMN rate_table_items.id IS '费率明细主键';
COMMENT ON COLUMN rate_table_items.product_id IS '所属产品';
COMMENT ON COLUMN rate_table_items.rate_type IS '费率计算类型';
COMMENT ON COLUMN rate_table_items.age_min IS '适用最小年龄';
COMMENT ON COLUMN rate_table_items.age_max IS '适用最大年龄';
COMMENT ON COLUMN rate_table_items.payment_period IS '缴费期限';
COMMENT ON COLUMN rate_table_items.coverage_period IS '保障期限';
COMMENT ON COLUMN rate_table_items.coverage_amount IS '保险金额，为空表示不限';
COMMENT ON COLUMN rate_table_items.rate_value IS '每万元保额的费率，用于重疾与寿险';
COMMENT ON COLUMN rate_table_items.premium IS '固定保费值，用于医疗与意外';
COMMENT ON COLUMN rate_table_items.status IS '费率明细状态';

COMMENT ON TABLE premium_quotes IS '单产品保费试算结果';
COMMENT ON COLUMN premium_quotes.id IS '试算结果主键';
COMMENT ON COLUMN premium_quotes.user_id IS '发起试算的用户';
COMMENT ON COLUMN premium_quotes.product_id IS '试算产品';
COMMENT ON COLUMN premium_quotes.request_params IS '原始试算请求参数，身份证已脱敏';
COMMENT ON COLUMN premium_quotes.result IS '命中的费率摘要，保证下单时规则不漂移';
COMMENT ON COLUMN premium_quotes.total_premium IS '试算年缴保费';
COMMENT ON COLUMN premium_quotes.pricing_mode IS '试算实现方式';
COMMENT ON COLUMN premium_quotes.expires_at IS '试算结果失效时间';
COMMENT ON COLUMN premium_quotes.created_at IS '创建时间';

COMMENT ON TABLE insurance_plans IS '用户保存的保险产品组合方案';
COMMENT ON COLUMN insurance_plans.id IS '方案主键';
COMMENT ON COLUMN insurance_plans.user_id IS '方案所属用户';
COMMENT ON COLUMN insurance_plans.plan_name IS '方案名称';
COMMENT ON COLUMN insurance_plans.summary IS '方案整体说明';
COMMENT ON COLUMN insurance_plans.annual_premium_budget IS '组合年缴预算参考';
COMMENT ON COLUMN insurance_plans.created_at IS '创建时间';
COMMENT ON COLUMN insurance_plans.updated_at IS '最后更新时间';

COMMENT ON TABLE insurance_plan_items IS '保险组合方案中的产品项';
COMMENT ON COLUMN insurance_plan_items.id IS '方案项主键';
COMMENT ON COLUMN insurance_plan_items.plan_id IS '所属保险方案';
COMMENT ON COLUMN insurance_plan_items.product_id IS '推荐产品';
COMMENT ON COLUMN insurance_plan_items.priority IS '方案内展示顺序';
COMMENT ON COLUMN insurance_plan_items.recommendation_reason IS '推荐该产品的理由';
COMMENT ON COLUMN insurance_plan_items.annual_premium_budget IS '产品年缴预算参考';
COMMENT ON COLUMN insurance_plan_items.created_at IS '创建时间';
COMMENT ON COLUMN insurance_plan_items.updated_at IS '最后更新时间';

COMMENT ON TABLE policies IS '用户已承保的教学保单';
COMMENT ON COLUMN policies.id IS '保单主键';
COMMENT ON COLUMN policies.user_id IS '保单所属用户';
COMMENT ON COLUMN policies.product_id IS '承保产品';
COMMENT ON COLUMN policies.quote_id IS '出单依据的试算报价，一份报价只能出一张保单';
COMMENT ON COLUMN policies.policy_number IS '保险公司保单号';
COMMENT ON COLUMN policies.status IS '保单状态';
COMMENT ON COLUMN policies.effective_at IS '保单生效时间';
COMMENT ON COLUMN policies.expires_at IS '保单到期时间，为空表示终身';
COMMENT ON COLUMN policies.insured_name IS '被保人姓名';
COMMENT ON COLUMN policies.insured_id_no_masked IS '被保人证件号脱敏展示';
COMMENT ON COLUMN policies.insured_phone IS '被保人联系电话';
COMMENT ON COLUMN policies.coverage_amount IS '本保单保额';
COMMENT ON COLUMN policies.premium_amount IS '本保单保费';
COMMENT ON COLUMN policies.policy_snapshot IS '投保当时产品与规则快照';
COMMENT ON COLUMN policies.issued_at IS '承保出单时间';
COMMENT ON COLUMN policies.created_at IS '创建时间';
