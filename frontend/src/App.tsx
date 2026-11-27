import { useCallback, useEffect, useState } from 'react';

import { AdvisorPage } from './AdvisorPage';
import { clearSession, readSession, verifySession } from './auth';
import { HomePage } from './HomePage';
import { MyPlansPage } from './MyPlansPage';
import { MyPoliciesPage } from './MyPoliciesPage';
import type { AuthSession } from './types';

function navigate(path: string) {
  window.history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export default function App() {
  const [path, setPath] = useState(window.location.pathname);
  const [session, setSession] = useState<AuthSession | null>(() => readSession());

  useEffect(() => {
    const updatePath = () => setPath(window.location.pathname);
    window.addEventListener('popstate', updatePath);
    return () => window.removeEventListener('popstate', updatePath);
  }, []);

  // 启动时向服务端确认本地 token 是否还有效，过期就清掉，避免页面显示已登录但请求全 401
  useEffect(() => {
    const controller = new AbortController();
    void verifySession(controller.signal).then((verified) => {
      if (verified) setSession(verified);
      else setSession(null);
    });
    return () => controller.abort();
  }, []);

  // AuthModal 内部已经把 token 和用户信息写入 localStorage，这里只更新内存状态
  function authenticated(nextSession: AuthSession) {
    setSession(nextSession);
  }

  function logout() {
    clearSession();
    setSession(null);
    if (path === '/advisor' || path === '/plans' || path === '/policies') navigate('/');
  }

  const sessionExpired = useCallback(() => {
    clearSession();
    setSession(null);
    navigate('/');
  }, []);

  if (path === '/advisor' && session) {
    return (
      <AdvisorPage
        session={session}
        onBack={() => navigate('/')}
        onSessionExpired={sessionExpired}
        onMyPlans={() => navigate('/plans')}
      />
    );
  }

  if (path === '/plans' && session) {
    return (
      <MyPlansPage
        onHome={() => navigate('/')}
        onConsult={() => navigate('/advisor')}
        onSessionExpired={sessionExpired}
        onMyPolicies={() => navigate('/policies')}
      />
    );
  }

  if (path === '/policies' && session) {
    return (
      <MyPoliciesPage
        onHome={() => navigate('/')}
        onSessionExpired={sessionExpired}
      />
    );
  }

  return (
    <HomePage
      session={session}
      initialAuthOpen={path === '/advisor' || path === '/plans' || path === '/policies'}
      onAuthenticated={authenticated}
      onLogout={logout}
      onConsult={() => navigate('/advisor')}
      onMyPlans={() => navigate('/plans')}
      onMyPolicies={() => navigate('/policies')}
    />
  );
}
