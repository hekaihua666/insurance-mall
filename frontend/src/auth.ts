import { clearToken, fetchCurrentUser, readToken, saveToken } from './api';
import type { AuthSession } from './types';

const STORAGE_KEY = 'insurance-mall-auth-v2';

/**
 * 读取登录态。
 *
 * token 和用户信息要同时存在才算登录成功；
 * 只有其中一个时视为无效，清掉避免半登录状态。
 */
export function readSession(): AuthSession | null {
  try {
    const token = readToken();
    const value = window.localStorage.getItem(STORAGE_KEY);
    if (!token || !value) {
      return null;
    }
    return JSON.parse(value) as AuthSession;
  } catch {
    clearSession();
    return null;
  }
}

/**
 * 启动时校验本地 token 是否还有效。
 *
 * localStorage 里的登录态可能是过期或伪造的，光看有没有数据不够。
 * 用 token 请求 /auth/me：成功则以服务端返回的用户信息为准刷新缓存，
 * 失败就清掉登录态，让用户重新登录。
 */
export async function verifySession(signal?: AbortSignal): Promise<AuthSession | null> {
  if (!readToken()) {
    return null;
  }
  try {
    const user = await fetchCurrentUser(signal);
    const session: AuthSession = { user };
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    return session;
  } catch {
    // 被取消的请求不算"登录失效"，保留原状态，避免快速切页时误清
    if (signal?.aborted) {
      return readSession();
    }
    clearSession();
    return null;
  }
}

/** 保存登录态：token 单独存，用户信息放 session，方便页面直接读用户名 */
export function saveLogin(token: string, session: AuthSession): void {
  saveToken(token);
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
}

export function clearSession(): void {
  clearToken();
  window.localStorage.removeItem(STORAGE_KEY);
}
