import { LockOutlined, MailOutlined, UserOutlined } from '@ant-design/icons';
import { Alert, Form, Input, Modal, Segmented } from 'antd';
import { useState } from 'react';

import { ApiError, login, register } from './api';
import { saveLogin } from './auth';
import type { AuthSession } from './types';

interface AuthModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: (session: AuthSession) => void;
}

type Mode = 'login' | 'register';

interface AuthFormValues {
  username: string;
  password: string;
  email?: string;
  displayName?: string;
}

export function AuthModal({ open, onCancel, onSuccess }: AuthModalProps) {
  const [mode, setMode] = useState<Mode>('login');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [form] = Form.useForm<AuthFormValues>();

  async function submit(values: AuthFormValues) {
    setSubmitting(true);
    setError(null);
    try {
      // 登录和注册返回结构一致，都是 { token, user }
      const result =
        mode === 'login'
          ? await login(values.username, values.password)
          : await register({
              username: values.username,
              email: values.email ?? '',
              password: values.password,
              displayName: values.displayName,
            });
      saveLogin(result.token, { user: result.user });
      onSuccess({ user: result.user });
      form.resetFields();
    } catch (reason) {
      if (reason instanceof ApiError) {
        setError(reason.message);
      } else {
        setError(reason instanceof Error ? reason.message : '操作失败，请稍后重试');
      }
    } finally {
      setSubmitting(false);
    }
  }

  function switchMode(next: Mode) {
    setMode(next);
    setError(null);
    form.resetFields();
  }

  return (
    <Modal
      open={open}
      title={null}
      footer={null}
      width={420}
      onCancel={onCancel}
      destroyOnHidden
      centered
    >
      <div className="auth-modal__header">
        <h2>欢迎来到安心保</h2>
        <p>登录后可使用智能顾问服务</p>
      </div>

      <Segmented<Mode>
        block
        value={mode}
        onChange={switchMode}
        options={[
          { label: '登录', value: 'login' },
          { label: '注册', value: 'register' },
        ]}
        style={{ marginBottom: 16 }}
      />

      {error ? (
        <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />
      ) : null}

      <Form<AuthFormValues>
        form={form}
        layout="vertical"
        onFinish={submit}
        requiredMark={false}
      >
        <Form.Item
          label="用户名"
          name="username"
          rules={[{ required: true, message: '请输入用户名' }]}
        >
          <Input prefix={<UserOutlined />} size="large" placeholder="请输入用户名" />
        </Form.Item>

        {mode === 'register' ? (
          <>
            <Form.Item
              label="邮箱"
              name="email"
              rules={[
                { required: true, message: '请输入邮箱' },
                { type: 'email', message: '邮箱格式不正确' },
              ]}
            >
              <Input prefix={<MailOutlined />} size="large" placeholder="用于找回账号" />
            </Form.Item>
            <Form.Item label="昵称" name="displayName">
              <Input size="large" placeholder="不填则用用户名" />
            </Form.Item>
          </>
        ) : null}

        <Form.Item
          label="密码"
          name="password"
          rules={[{ required: true, min: 6, message: '密码至少需要 6 位' }]}
        >
          <Input.Password prefix={<LockOutlined />} size="large" placeholder="请输入密码" />
        </Form.Item>

        <button className="auth-submit" type="submit" disabled={submitting}>
          {submitting ? '处理中…' : mode === 'login' ? '登录' : '注册并登录'}
        </button>
      </Form>
    </Modal>
  );
}
