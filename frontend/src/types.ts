export type InsuranceCategory = 'medical' | 'critical_illness' | 'life' | 'accident';

export interface Product {
  id: number;
  name: string;
  clause_name: string;
  category: InsuranceCategory;
  insurer: string;
  image_url: string | null;
  description: string;
  min_premium: number;
  max_premium: number | null;
  target_group: string;
  highlights: string[];
  status: string;
}

export interface UserSummary {
  id: number;
  username: string;
  email: string;
  displayName: string | null;
}

export interface AuthSession {
  user: UserSummary;
}

/** 登录/注册接口返回：token 单独存 localStorage，user 放进 session */
export interface AuthResponse {
  token: string;
  user: UserSummary;
}

/** 方案项 */
export interface PlanItem {
  id: string;
  productId: number;
  productName: string;
  category: string;
  priority: number;
  recommendationReason: string | null;
  annualPremiumBudget: number | null;
}

export interface PlanSummary {
  id: string;
  planName: string;
  summary: string | null;
  annualPremiumBudget: number | null;
  createdAt: string;
}

/** 详情按需加载推荐产品。 */
export interface PlanResponse extends Omit<PlanSummary, 'createdAt'> {
  items: PlanItem[];
}

export interface ChatThread {
  id: string;
  title: string;
  created_at: string;
  updated_at: string;
}

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
}

export interface ChatHistory {
  thread_id: string;
  messages: ChatMessage[];
}

// ===== 投保 / 下单 / 保单 =====

/** 保费试算出参：金额及期限均来自本次命中的配置费率。 */
export interface QuoteResponse {
  quoteId: string;
  productId: number;
  productName: string;
  totalPremium: number;
  pricingMode: string;
  rateValue: number | null;
  coveragePeriod: string;
  paymentPeriod: string;
}

/** 保单列表项。出单即保单，没有单独的订单类型。 */
export interface PolicyResponse {
  policyNumber: string;
  productId: number;
  productName: string;
  status: string;
  effectiveAt: string;
  expiresAt: string | null;
  insuredName: string;
  insuredIdNoMasked: string;
  coverageAmount: number | null;
  premiumAmount: number | null;
}
