-- 补全 12 个产品的费率明细，让所有产品都能真实算保费。
-- 三类算法：重疾/寿险用 per_10000_sum_assured（premium = rate_value × 保额/10000），
--          医疗用 age_band_plan_premium（按年龄区间取固定 premium），
--          意外用 fixed_plan_premium（按保额档取固定 premium）。
-- 年龄从身份证解出，只按「年龄 + 保额」两个维度算；rate_value 与 premium 互斥，一行只有一个有效值。

-- 费率明细：一个产品 × 一个年龄档 = 一行，共 49 行。
DELETE FROM rate_table_items;
INSERT INTO rate_table_items (
    id, product_id, rate_type,
    age_min, age_max,
    payment_period, coverage_period, coverage_amount,
    rate_value, premium, status
)
VALUES
    -- ===== 重疾 per_10000_sum_assured：premium = rate_value × 保额/10000 =====
    -- 产品1 达尔文宝贝计划12号（少儿，min 285.30）
    ('30000000-0000-0000-0000-000000000101', 1, 'per_10000_sum_assured', 0, 5, '20y', 'lifetime', NULL, 4.900000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000102', 1, 'per_10000_sum_assured', 6, 17, '20y', 'lifetime', NULL, 5.710000, NULL, 'active'),
    -- 产品2 达尔文12号（min 561）
    ('30000000-0000-0000-0000-000000000201', 2, 'per_10000_sum_assured', 18, 30, '20y', 'lifetime', NULL, 8.200000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000202', 2, 'per_10000_sum_assured', 31, 40, '20y', 'lifetime', NULL, 11.220000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000203', 2, 'per_10000_sum_assured', 41, 50, '20y', 'lifetime', NULL, 15.600000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000204', 2, 'per_10000_sum_assured', 51, 60, '20y', 'lifetime', NULL, 20.800000, NULL, 'active'),
    -- 产品3 医联有盟（min 3185）
    ('30000000-0000-0000-0000-000000000301', 3, 'per_10000_sum_assured', 18, 30, '20y', 'lifetime', NULL, 46.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000302', 3, 'per_10000_sum_assured', 31, 40, '20y', 'lifetime', NULL, 63.700000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000303', 3, 'per_10000_sum_assured', 41, 50, '20y', 'lifetime', NULL, 88.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000304', 3, 'per_10000_sum_assured', 51, 60, '20y', 'lifetime', NULL, 116.000000, NULL, 'active'),
    -- ===== 医疗 age_band_plan_premium：按年龄区间取固定 premium（固定 100 万保额） =====
    -- 产品4 星相守2号个人（min 148）
    ('30000000-0000-0000-0000-000000000401', 4, 'age_band_plan_premium', 0, 17, 'annual', '1y', 1000000.00, NULL, 148.00, 'active'),
    ('30000000-0000-0000-0000-000000000402', 4, 'age_band_plan_premium', 18, 30, 'annual', '1y', 1000000.00, NULL, 260.00, 'active'),
    ('30000000-0000-0000-0000-000000000403', 4, 'age_band_plan_premium', 31, 40, 'annual', '1y', 1000000.00, NULL, 420.00, 'active'),
    ('30000000-0000-0000-0000-000000000404', 4, 'age_band_plan_premium', 41, 50, 'annual', '1y', 1000000.00, NULL, 720.00, 'active'),
    ('30000000-0000-0000-0000-000000000405', 4, 'age_band_plan_premium', 51, 60, 'annual', '1y', 1000000.00, NULL, 1180.00, 'active'),
    ('30000000-0000-0000-0000-000000000406', 4, 'age_band_plan_premium', 61, 70, 'annual', '1y', 1000000.00, NULL, 1680.00, 'active'),
    -- 产品5 星相守2号转保（min 180）
    ('30000000-0000-0000-0000-000000000501', 5, 'age_band_plan_premium', 0, 17, 'annual', '1y', 1000000.00, NULL, 180.00, 'active'),
    ('30000000-0000-0000-0000-000000000502', 5, 'age_band_plan_premium', 18, 30, 'annual', '1y', 1000000.00, NULL, 300.00, 'active'),
    ('30000000-0000-0000-0000-000000000503', 5, 'age_band_plan_premium', 31, 40, 'annual', '1y', 1000000.00, NULL, 480.00, 'active'),
    ('30000000-0000-0000-0000-000000000504', 5, 'age_band_plan_premium', 41, 50, 'annual', '1y', 1000000.00, NULL, 820.00, 'active'),
    ('30000000-0000-0000-0000-000000000505', 5, 'age_band_plan_premium', 51, 60, 'annual', '1y', 1000000.00, NULL, 1350.00, 'active'),
    ('30000000-0000-0000-0000-000000000506', 5, 'age_band_plan_premium', 61, 70, 'annual', '1y', 1000000.00, NULL, 1900.00, 'active'),
    -- 产品6 众安尊享e生（min 255）
    ('30000000-0000-0000-0000-000000000601', 6, 'age_band_plan_premium', 0, 17, 'annual', '1y', 1000000.00, NULL, 255.00, 'active'),
    ('30000000-0000-0000-0000-000000000602', 6, 'age_band_plan_premium', 18, 30, 'annual', '1y', 1000000.00, NULL, 350.00, 'active'),
    ('30000000-0000-0000-0000-000000000603', 6, 'age_band_plan_premium', 31, 40, 'annual', '1y', 1000000.00, NULL, 600.00, 'active'),
    ('30000000-0000-0000-0000-000000000604', 6, 'age_band_plan_premium', 41, 50, 'annual', '1y', 1000000.00, NULL, 800.00, 'active'),
    ('30000000-0000-0000-0000-000000000605', 6, 'age_band_plan_premium', 51, 60, 'annual', '1y', 1000000.00, NULL, 1100.00, 'active'),
    ('30000000-0000-0000-0000-000000000606', 6, 'age_band_plan_premium', 61, 70, 'annual', '1y', 1000000.00, NULL, 1400.00, 'active'),
    -- ===== 寿险 per_10000_sum_assured =====
    -- 产品7 中英福满佳（min 10000）
    ('30000000-0000-0000-0000-000000000701', 7, 'per_10000_sum_assured', 18, 30, '20y', 'lifetime', NULL, 160.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000702', 7, 'per_10000_sum_assured', 31, 40, '20y', 'lifetime', NULL, 200.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000703', 7, 'per_10000_sum_assured', 41, 50, '20y', 'lifetime', NULL, 260.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000704', 7, 'per_10000_sum_assured', 51, 60, '20y', 'lifetime', NULL, 340.000000, NULL, 'active'),
    -- 产品8 太保福有余（min 5000）
    ('30000000-0000-0000-0000-000000000801', 8, 'per_10000_sum_assured', 18, 30, '20y', 'lifetime', NULL, 80.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000802', 8, 'per_10000_sum_assured', 31, 40, '20y', 'lifetime', NULL, 100.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000803', 8, 'per_10000_sum_assured', 41, 50, '20y', 'lifetime', NULL, 130.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000804', 8, 'per_10000_sum_assured', 51, 60, '20y', 'lifetime', NULL, 170.000000, NULL, 'active'),
    -- 产品9 新华E增福（min 10000）
    ('30000000-0000-0000-0000-000000000901', 9, 'per_10000_sum_assured', 18, 30, '20y', 'lifetime', NULL, 160.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000902', 9, 'per_10000_sum_assured', 31, 40, '20y', 'lifetime', NULL, 200.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000903', 9, 'per_10000_sum_assured', 41, 50, '20y', 'lifetime', NULL, 260.000000, NULL, 'active'),
    ('30000000-0000-0000-0000-000000000904', 9, 'per_10000_sum_assured', 51, 60, '20y', 'lifetime', NULL, 340.000000, NULL, 'active'),
    -- ===== 意外 fixed_plan_premium：按保额档取固定 premium =====
    -- 产品10 小蜜蜂6号（min 136）
    ('30000000-0000-0000-0000-000000001001', 10, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 300000.00, NULL, 96.00, 'active'),
    ('30000000-0000-0000-0000-000000001002', 10, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 500000.00, NULL, 136.00, 'active'),
    ('30000000-0000-0000-0000-000000001003', 10, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 1000000.00, NULL, 238.00, 'active'),
    -- 产品11 小蜜蜂玫瑰（min 169）
    ('30000000-0000-0000-0000-000000001101', 11, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 300000.00, NULL, 119.00, 'active'),
    ('30000000-0000-0000-0000-000000001102', 11, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 500000.00, NULL, 169.00, 'active'),
    ('30000000-0000-0000-0000-000000001103', 11, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 1000000.00, NULL, 296.00, 'active'),
    -- 产品12 人保学童（min 240）
    ('30000000-0000-0000-0000-000000001201', 12, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 300000.00, NULL, 168.00, 'active'),
    ('30000000-0000-0000-0000-000000001202', 12, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 500000.00, NULL, 240.00, 'active'),
    ('30000000-0000-0000-0000-000000001203', 12, 'fixed_plan_premium', NULL, NULL, 'annual', '1y', 1000000.00, NULL, 335.00, 'active')
ON CONFLICT (id) DO NOTHING;
