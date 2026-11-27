import { Alert, Button, Empty, Spin } from 'antd';
import {
  ArrowLeftOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { useEffect, useState } from 'react';

import { ApiError, listPolicies } from './api';
import type { PolicyResponse } from './types';
import './account-pages.css';

interface MyPoliciesPageProps {
  onHome: () => void;
  onSessionExpired: () => void;
}

const STATUS_LABELS: Record<string, string> = {
  active: '保障中',
  pending: '待生效',
  expired: '已到期',
  terminated: '已终止',
};

export function MyPoliciesPage({
  onHome,
  onSessionExpired,
}: MyPoliciesPageProps) {
  const [policies, setPolicies] = useState<PolicyResponse[] | null>(null);
  const [error, setError] = useState<string>();

  useEffect(() => {
    const controller = new AbortController();
    listPolicies(controller.signal)
      .then(setPolicies)
      .catch((reason) => {
        if (controller.signal.aborted) return;
        if (reason instanceof ApiError && reason.status === 401)
          onSessionExpired();
        else
          setError(
            reason instanceof Error ? reason.message : '保单列表加载失败',
          );
      });
    return () => controller.abort();
  }, [onSessionExpired]);

  return (
    <div className="page-shell account-page">
      <main className="account-layout">
        <nav className="account-topbar" aria-label="当前位置">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={onHome}>
            返回商城
          </Button>
          <span>
            个人中心 <span className="account-breadcrumb-separator">/</span>{' '}
            我的保单
          </span>
        </nav>
        <header className="account-heading">
          <div>
            <div className="account-heading__title">
              <h1>我的保单</h1>
              {policies && (
                <span className="account-count">{policies.length} 份保单</span>
              )}
            </div>
            <p>每一份保障，都有迹可循。在这里查看被保人、保障额度与有效期。</p>
          </div>
        </header>
        <div className="account-hint">
          <SafetyCertificateOutlined />
          <span>在「我的方案」中选择产品并完成投保后，保单会显示在这里。</span>
        </div>
        {error ? (
          <Alert showIcon type="error" message={error} />
        ) : policies === null ? (
          <div className="account-state">
            <Spin tip="正在加载保单…">
              <div />
            </Spin>
          </div>
        ) : policies.length === 0 ? (
          <div className="account-state">
            <Empty description="暂无保单" />
          </div>
        ) : (
          <div className="account-policy-list">
            {policies.map((policy) => (
              <article
                className="account-policy-card"
                key={policy.policyNumber}
              >
                <header className="account-policy-card__heading">
                  <div className="account-policy-card__title">
                    <span className="account-card-icon">
                      <SafetyCertificateOutlined />
                    </span>
                    <h2>{policy.productName}</h2>
                  </div>
                  <span
                    className={`account-policy-status ${policy.status === 'active' ? 'is-active' : policy.status === 'pending' ? 'is-pending' : ''}`}
                  >
                    <i />
                    {STATUS_LABELS[policy.status] ?? policy.status}
                  </span>
                </header>
                <div className="account-policy-card__body">
                  <dl className="account-policy-fields">
                    <div>
                      <dt>被保人</dt>
                      <dd>
                        {policy.insuredName}
                        <small>{policy.insuredIdNoMasked}</small>
                      </dd>
                    </div>
                    <div>
                      <dt>保障额度</dt>
                      <dd className="account-policy-coverage">
                        {/* 早期导入的保单没有记录保额，显示占位符而不是让页面崩掉 */}
                        {policy.coverageAmount === null
                          ? '—'
                          : `¥${policy.coverageAmount.toLocaleString('zh-CN')}`}
                      </dd>
                    </div>
                    <div className="account-policy-dates">
                      <dt>保障期间</dt>
                      <dd>
                        {new Date(policy.effectiveAt).toLocaleDateString(
                          'zh-CN',
                        )}
                        {policy.expiresAt
                          ? ` — ${new Date(policy.expiresAt).toLocaleDateString('zh-CN')}`
                          : ' 起 · 长期有效'}
                      </dd>
                    </div>
                  </dl>
                  <div className="account-policy-premium">
                    <span>年缴保费</span>
                    <div>
                      <small>¥</small>
                      <strong>
                        {/* 同上：老保单保费为空时给占位符 */}
                        {policy.premiumAmount === null
                          ? '—'
                          : policy.premiumAmount.toLocaleString('zh-CN', {
                              maximumFractionDigits: 2,
                            })}
                      </strong>
                      <span> / 年</span>
                    </div>
                  </div>
                </div>
                <footer className="account-policy-card__footer">
                  <span>保单号</span>
                  <span>{policy.policyNumber}</span>
                </footer>
              </article>
            ))}
          </div>
        )}
      </main>
    </div>
  );
}
