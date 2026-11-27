import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  message,
} from 'antd';
import { useEffect, useState } from 'react';
import {
  ArrowLeftOutlined,
  ArrowRightOutlined,
  CalendarOutlined,
  CheckCircleFilled,
  FileTextOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';

import {
  ApiError,
  createPolicy,
  getInsurancePlan,
  listInsurancePlans,
  quotePremium,
} from './api';
import { formatCategoryLabel } from './categoryLabels';
import type { PlanResponse, PlanSummary, QuoteResponse } from './types';
import './account-pages.css';

interface MyPlansPageProps {
  onHome: () => void;
  onConsult: () => void;
  onSessionExpired: () => void;
  onMyPolicies: () => void;
}

interface InsureForm {
  insuredName: string;
  insuredIdNo: string;
  insuredPhone: string;
  coverageAmount: number;
}

export function MyPlansPage({
  onHome,
  onConsult,
  onSessionExpired,
  onMyPolicies,
}: MyPlansPageProps) {
  const [plans, setPlans] = useState<PlanSummary[] | null>(null);
  const [error, setError] = useState<string>();
  const [selected, setSelected] = useState<PlanSummary | null>(null);
  const [detail, setDetail] = useState<PlanResponse | null>(null);
  const [detailError, setDetailError] = useState<string>();

  // 投保弹窗状态：target 是要投保的产品项，quote 是试算结果
  const [insureTarget, setInsureTarget] = useState<{
    productId: number;
    productName: string;
  } | null>(null);
  const [quote, setQuote] = useState<QuoteResponse | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm<InsureForm>();

  // 每次进入页面读取已保存的方案，返回咨询后再进入即可看到最新列表。
  useEffect(() => {
    const controller = new AbortController();
    listInsurancePlans(controller.signal)
      .then(setPlans)
      .catch((reason) => {
        if (controller.signal.aborted) return;
        if (reason instanceof ApiError && reason.status === 401)
          onSessionExpired();
        else
          setError(
            reason instanceof Error ? reason.message : '方案列表加载失败',
          );
      });
    return () => controller.abort();
  }, [onSessionExpired]);

  useEffect(() => {
    if (!selected) return;
    const controller = new AbortController();
    getInsurancePlan(selected.id, controller.signal)
      .then(setDetail)
      .catch((reason) => {
        if (controller.signal.aborted) return;
        if (reason instanceof ApiError && reason.status === 401)
          onSessionExpired();
        else
          setDetailError(
            reason instanceof Error ? reason.message : '方案详情加载失败',
          );
      });
    return () => controller.abort();
  }, [selected, onSessionExpired]);

  function openInsure(productId: number, productName: string) {
    setInsureTarget({ productId, productName });
    setQuote(null);
    form.resetFields();
  }

  // 第一步：填写被保人信息，后端按配置费率试算保费。
  async function doQuote(values: InsureForm) {
    if (!insureTarget) return;
    setSubmitting(true);
    try {
      const result = await quotePremium({
        productId: insureTarget.productId,
        ...values,
      });
      setQuote(result);
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) {
        onSessionExpired();
        return;
      }
      message.error(reason instanceof Error ? reason.message : '试算失败');
    } finally {
      setSubmitting(false);
    }
  }

  // 第二步：确认投保，出单即保单
  async function doIssue() {
    if (!quote) return;
    setSubmitting(true);
    try {
      const result = await createPolicy(quote.quoteId);
      message.success(`投保成功，保单号 ${result.policyNumber}`);
      setSelected(null);
      setInsureTarget(null);
      setQuote(null);
    } catch (reason) {
      if (reason instanceof ApiError && reason.status === 401) {
        onSessionExpired();
        return;
      }
      message.error(reason instanceof Error ? reason.message : '投保失败');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-shell account-page">
      <main className="account-layout">
        <nav className="account-topbar" aria-label="当前位置">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={onHome}>
            返回商城
          </Button>
          <span>
            个人中心 <span className="account-breadcrumb-separator">/</span>{' '}
            我的方案
          </span>
        </nav>
        <header className="account-heading">
          <div>
            <div className="account-heading__title">
              <h1>我的方案</h1>
              {plans && (
                <span className="account-count">{plans.length} 份方案</span>
              )}
            </div>
            <p>收藏适合自己的保障方案，查看产品明细，按需试算与投保。</p>
          </div>
          <Space wrap className="account-heading__actions">
            <Button icon={<SafetyCertificateOutlined />} onClick={onMyPolicies}>
              我的保单
            </Button>
            <Button type="primary" onClick={onConsult}>
              咨询保险顾问 <ArrowRightOutlined />
            </Button>
          </Space>
        </header>
        <div className="account-hint">
          <FileTextOutlined />
          <span>
            在顾问会话中说「保存这个方案」，即可在这里查看。起售价仅作参考，实际保费以试算结果为准。
          </span>
        </div>
        {error ? (
          <Alert showIcon type="error" message={error} />
        ) : plans === null ? (
          <div className="account-state">
            <Spin tip="正在加载方案…">
              <div />
            </Spin>
          </div>
        ) : plans.length === 0 ? (
          <div className="account-state">
            <Empty description="暂无保存的方案">
              <Button type="primary" onClick={onConsult}>
                去咨询，定制第一份方案
              </Button>
            </Empty>
          </div>
        ) : (
          <div className="account-plan-grid">
            {plans.map((plan) => (
              <article className="account-plan-card" key={plan.id}>
                <div className="account-plan-card__heading">
                  <span className="account-card-icon">
                    <FileTextOutlined />
                  </span>
                  <h2>{plan.planName}</h2>
                </div>
                <p className="account-plan-card__summary">
                  {plan.summary || '查看方案，了解推荐的保障产品。'}
                </p>
                <div className="account-plan-card__price">
                  <span>起售价合计</span>
                  {plan.annualPremiumBudget !== null ? (
                    <strong>
                      <small>¥</small>
                      {plan.annualPremiumBudget.toLocaleString('zh-CN', {
                        maximumFractionDigits: 2,
                      })}
                    </strong>
                  ) : (
                    <span>待确认</span>
                  )}
                </div>
                <footer className="account-plan-card__footer">
                  <time dateTime={plan.createdAt}>
                    <CalendarOutlined />
                    保存于{' '}
                    {new Date(plan.createdAt).toLocaleString('zh-CN', {
                      year: 'numeric',
                      month: '2-digit',
                      day: '2-digit',
                      hour: '2-digit',
                      minute: '2-digit',
                      hour12: false,
                    })}
                  </time>
                  <Button
                    type="text"
                    className="account-detail-button"
                    onClick={() => {
                      setDetail(null);
                      setDetailError(undefined);
                      setSelected(plan);
                    }}
                  >
                    查看详情 <ArrowRightOutlined />
                  </Button>
                </footer>
              </article>
            ))}
          </div>
        )}
      </main>

      {/* 详情与投保弹窗互斥显示；保留 selected，退出投保时可返回原方案。 */}
      <Modal
        open={selected !== null && insureTarget === null}
        title={
          <span className="account-modal-title">
            <FileTextOutlined />
            方案详情
          </span>
        }
        centered
        width={760}
        className="account-modal"
        footer={<Button onClick={() => setSelected(null)}>关闭详情</Button>}
        onCancel={() => setSelected(null)}
      >
        {detailError ? (
          <Alert showIcon type="error" message={detailError} />
        ) : detail === null ? (
          <div className="account-state">
            <Spin />
          </div>
        ) : (
          <>
            <div className="account-detail-overview">
              <div>
                <h2>{detail.planName}</h2>
                {detail.summary && <p>{detail.summary}</p>}
                <span className="account-detail-count">
                  共 {detail.items.length} 款保障产品
                </span>
              </div>
              {detail.annualPremiumBudget !== null && (
                <div className="account-detail-total">
                  <span>起售价合计</span>
                  <strong>
                    ¥
                    {detail.annualPremiumBudget.toLocaleString('zh-CN', {
                      maximumFractionDigits: 2,
                    })}
                  </strong>
                </div>
              )}
            </div>
            <div className="account-product-list">
              {detail.items.map((item) => (
                <article className="account-product-item" key={item.id}>
                  <div className="account-product-item__heading">
                    <h3>{item.productName}</h3>
                    {item.category && (
                      <span className="account-category">
                        {formatCategoryLabel(item.category)}
                      </span>
                    )}
                  </div>
                  {item.recommendationReason && (
                    <p>{item.recommendationReason}</p>
                  )}
                  <div className="account-product-item__footer">
                    <div className="account-product-price">
                      {item.annualPremiumBudget !== null && (
                        <>
                          <span>起售价</span>
                          <strong>
                            ¥
                            {item.annualPremiumBudget.toLocaleString('zh-CN', {
                              maximumFractionDigits: 2,
                            })}
                          </strong>
                        </>
                      )}
                    </div>
                    <Button
                      type="primary"
                      onClick={() =>
                        openInsure(item.productId, item.productName)
                      }
                    >
                      立即投保 <ArrowRightOutlined />
                    </Button>
                  </div>
                </article>
              ))}
            </div>
            <p className="account-modal-note">
              以上为产品起售价，填写被保人信息后可试算本次投保保费。
            </p>
          </>
        )}
      </Modal>

      <Modal
        open={insureTarget !== null}
        title={
          <span className="account-modal-title">
            <SafetyCertificateOutlined />
            {quote ? '确认投保' : '填写投保信息'}
          </span>
        }
        centered
        width={620}
        className="account-modal account-insure-modal"
        onCancel={() => setInsureTarget(null)}
        maskClosable={!submitting}
        closable={!submitting}
        keyboard={!submitting}
        footer={
          quote
            ? [
                <Button
                  key="back"
                  disabled={submitting}
                  onClick={() => setQuote(null)}
                >
                  返回修改
                </Button>,
                <Button
                  key="submit"
                  type="primary"
                  loading={submitting}
                  onClick={doIssue}
                >
                  确认投保
                </Button>,
              ]
            : null
        }
        confirmLoading={submitting}
      >
        <p className="account-insure-product">{insureTarget?.productName}</p>
        <ol className="account-steps" aria-label="投保进度">
          <li className={quote ? 'is-complete' : 'is-current'}>
            <span>{quote ? <CheckCircleFilled /> : '1'}</span>填写信息
          </li>
          <li className={quote ? 'is-current' : ''}>
            <span>2</span>确认保费
          </li>
        </ol>
        {quote ? (
          <div className="account-quote">
            <div className="account-quote__premium">
              <span>本次试算 · 年缴保费</span>
              <div>
                <small>¥</small>
                <strong>
                  {quote.totalPremium.toLocaleString('zh-CN', {
                    maximumFractionDigits: 2,
                  })}
                </strong>
                <span> / 年</span>
              </div>
            </div>
            <dl className="account-quote__details">
              <div>
                <dt>被保人</dt>
                <dd>{form.getFieldValue('insuredName')}</dd>
              </div>
              <div>
                <dt>保障额度</dt>
                <dd>
                  ¥
                  {form
                    .getFieldValue('coverageAmount')
                    ?.toLocaleString('zh-CN')}
                </dd>
              </div>
              <div>
                <dt>保障期限</dt>
                <dd>
                  {quote.coveragePeriod === 'lifetime'
                    ? '终身'
                    : quote.coveragePeriod === '1y'
                      ? '一年'
                      : '以报价为准'}
                </dd>
              </div>
              {quote.paymentPeriod && (
                <div>
                  <dt>缴费期限</dt>
                  <dd>
                    {quote.paymentPeriod === 'annual'
                      ? '按年缴费'
                      : quote.paymentPeriod.replace(/y$/, '年')}
                  </dd>
                </div>
              )}
              <div>
                <dt>生效日期</dt>
                <dd>投保当日</dd>
              </div>
            </dl>
            <div className="account-hint account-hint--compact">
              <SafetyCertificateOutlined />
              <span>
                请核对以上信息。确认投保后，将生成保单，可在「我的保单」中查看。
              </span>
            </div>
          </div>
        ) : (
          <Form
            className="account-insure-form"
            form={form}
            layout="vertical"
            onFinish={doQuote}
            initialValues={{ coverageAmount: 500000 }}
          >
            <div className="account-form-grid">
              <Form.Item
                name="insuredName"
                label="被保人姓名"
                rules={[{ required: true, message: '请输入被保人姓名' }]}
              >
                <Input placeholder="请输入真实姓名" size="large" />
              </Form.Item>
              <Form.Item
                name="insuredPhone"
                label="联系电话"
                rules={[{ required: true, message: '请输入联系电话' }]}
              >
                <Input placeholder="请输入手机号" maxLength={11} size="large" />
              </Form.Item>
              <Form.Item
                className="account-form-wide"
                name="insuredIdNo"
                label="身份证号"
                rules={[{ required: true, message: '请输入身份证号' }]}
              >
                <Input
                  placeholder="请输入 18 位身份证号"
                  maxLength={18}
                  size="large"
                />
              </Form.Item>
              <Form.Item
                className="account-form-wide"
                name="coverageAmount"
                label="保障额度"
                extra="不同产品支持的保额档位不同，填错时下方会提示可选金额"
                rules={[{ required: true, message: '请输入保额' }]}
              >
                <InputNumber
                  min={10000}
                  step={10000}
                  addonAfter="元"
                  size="large"
                  placeholder="请选择或输入保额"
                  style={{ width: '100%' }}
                />
              </Form.Item>
              {/* 常见档位一键填入，免得用户对着提示一格一格点 */}
              <div className="account-coverage-presets">
                {[300000, 500000, 1000000].map((amount) => (
                  <Button
                    key={amount}
                    size="small"
                    onClick={() => form.setFieldValue('coverageAmount', amount)}
                  >
                    {amount / 10000} 万
                  </Button>
                ))}
              </div>
            </div>
            <p className="account-modal-note">
              请填写与证件一致的信息，试算后可核对保费再确认投保。
            </p>
            <Button
              type="primary"
              htmlType="submit"
              loading={submitting}
              size="large"
              block
            >
              试算保费，下一步 <ArrowRightOutlined />
            </Button>
          </Form>
        )}
      </Modal>
    </div>
  );
}
