import type {
  AuthResponse,
  AuthSession,
  ChatHistory,
  ChatThread,
  InsuranceCategory,
  PlanResponse,
  PlanSummary,
  PolicyResponse,
  Product,
  QuoteResponse,
} from './types';

export const API_BASE = '/api/v1';

const TOKEN_KEY = 'insurance-mall-token';

export class ApiError extends Error {
  constructor(
    message: string,
    readonly status: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

/** token 单独存取，不放进 session 里，避免每次读用户信息都要拆 token */
export function readToken(): string | null {
  return window.localStorage.getItem(TOKEN_KEY);
}

export function saveToken(token: string): void {
  window.localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  window.localStorage.removeItem(TOKEN_KEY);
}

/** 统一拼接鉴权头；没登录时不带，后端会返回 401 */
function authHeaders(): Record<string, string> {
  const token = readToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

/**
 * 后端统一响应是 {code, message, data}，这里拆开只把 data 给业务代码。
 * code 非 0 时抛 ApiError，让调用方按普通错误处理。
 */
async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
      ...init?.headers,
    },
  });

  if (response.status === 204) {
    return undefined as T;
  }

  const payload = await response.json().catch(() => null);
  if (!response.ok) {
    throw new ApiError(
      payload?.message ?? payload?.detail ?? '请求失败，请稍后重试',
      response.status,
    );
  }
  // 兼容裸 JSON（如产品接口）和包装后的响应
  if (payload && typeof payload === 'object' && 'code' in payload) {
    if (payload.code !== 0) {
      throw new ApiError(payload.message ?? '请求失败', response.status);
    }
    return payload.data as T;
  }
  return payload as T;
}

// ===== 认证 =====

export function login(
  username: string,
  password: string,
): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password }),
  });
}

export function register(input: {
  username: string;
  email: string;
  password: string;
  displayName?: string;
}): Promise<AuthResponse> {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 用本地 token 换当前用户信息，顺便验证 token 是否还有效 */
export function fetchCurrentUser(
  signal?: AbortSignal,
): Promise<AuthSession['user']> {
  return request<AuthSession['user']>('/auth/me', { signal });
}

// ===== 产品 =====

export function listProducts(
  category: InsuranceCategory | 'all',
  signal?: AbortSignal,
): Promise<Product[]> {
  const params = new URLSearchParams();
  if (category !== 'all') {
    params.set('category', category);
  }
  const query = params.toString();
  // Java/Python 的 Decimal 商品字段以字符串传输，只在接口边界转为前端数字。
  return request<
    Array<
      Omit<Product, 'min_premium' | 'max_premium'> & {
        min_premium: string | number;
        max_premium: string | number | null;
      }
    >
  >(`/products${query ? `?${query}` : ''}`, { signal }).then((products) =>
    products.map((product) => ({
      ...product,
      min_premium: Number(product.min_premium),
      max_premium:
        product.max_premium === null ? null : Number(product.max_premium),
    })),
  );
}

// ===== 保险方案 =====

export function listInsurancePlans(
  signal?: AbortSignal,
): Promise<PlanSummary[]> {
  return request<PlanSummary[]>('/insurance-plans', { signal });
}

export function getInsurancePlan(
  planId: string,
  signal?: AbortSignal,
): Promise<PlanResponse> {
  return request<PlanResponse>(`/insurance-plans/${planId}`, { signal });
}

// ===== 投保 / 下单 / 保单 =====

export function quotePremium(input: {
  productId: number;
  insuredName: string;
  insuredIdNo: string;
  insuredPhone: string;
  coverageAmount: number;
}): Promise<QuoteResponse> {
  return request<QuoteResponse>('/quotes', {
    method: 'POST',
    body: JSON.stringify(input),
  });
}

/** 出单：拿试算得到的报价直接生成保单，没有单独的订单。 */
export function createPolicy(quoteId: string): Promise<PolicyResponse> {
  return request<PolicyResponse>('/policies', {
    method: 'POST',
    body: JSON.stringify({ quoteId }),
  });
}

export function listPolicies(signal?: AbortSignal): Promise<PolicyResponse[]> {
  return request<PolicyResponse[]>('/policies', { signal });
}

// ===== 会话（由 Python Agent 服务处理，Java 转发） =====

export function listChatThreads(): Promise<ChatThread[]> {
  return request<ChatThread[]>('/chat-threads');
}

export function createChatThread(title = '保险咨询'): Promise<ChatThread> {
  return request<ChatThread>('/chat-threads', {
    method: 'POST',
    body: JSON.stringify({ title }),
  });
}

export function getChatHistory(
  threadId: string,
  signal?: AbortSignal,
): Promise<ChatHistory> {
  return request<ChatHistory>(`/chat-threads/${threadId}/messages`, { signal });
}

export function renameChatThread(
  threadId: string,
  title: string,
): Promise<ChatThread> {
  return request<ChatThread>(`/chat-threads/${threadId}`, {
    method: 'PATCH',
    body: JSON.stringify({ title }),
  });
}

export async function deleteChatThread(threadId: string): Promise<void> {
  await request<void>(`/chat-threads/${threadId}`, { method: 'DELETE' });
}

// ===== SSE 流式对话 =====

export async function streamChat(
  threadId: string,
  message: string,
  onMessage: (chunk: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const response = await fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...authHeaders(),
    },
    body: JSON.stringify({ thread_id: threadId, message }),
    signal,
  });
  if (!response.ok || !response.body) {
    const error = await response.json().catch(() => null);
    throw new ApiError(
      error?.detail ?? error?.message ?? '消息发送失败，请稍后重试',
      response.status,
    );
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let completed = false;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const parts = buffer.split(/\r?\n\r?\n/);
      buffer = parts.pop() ?? '';
      for (const eventText of parts)
        completed = handleSseEvent(eventText, onMessage) || completed;
      if (completed) return;
    }
    if (buffer.trim()) {
      completed = handleSseEvent(buffer, onMessage);
    }
    // HTTP 200 之后也可能中途断流；必须收到 done 才算本轮正常完成。
    if (!completed) throw new Error('回复中断，请稍后重试或重新加载会话');
  } finally {
    reader.releaseLock();
  }
}

/** 后端只发 message 和 done 两种事件，其余忽略 */
function handleSseEvent(eventText: string, onMessage: (chunk: string) => void) {
  const lines = eventText.split(/\r?\n/);
  const event = lines
    .find((line) => line.startsWith('event:'))
    ?.slice(6)
    .trim();
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n');
  if (event === 'message' && data) {
    onMessage(parseSseMessage(data));
  }
  if (event === 'error')
    throw new Error(parseSseMessage(data) || '回复生成失败，请稍后重试');
  return event === 'done';
}

function parseSseMessage(data: string): string {
  try {
    const value = JSON.parse(data) as unknown;
    return typeof value === 'string' ? value : JSON.stringify(value);
  } catch {
    return data;
  }
}
