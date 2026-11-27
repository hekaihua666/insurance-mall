import {
  ArrowLeftOutlined,
  CustomerServiceOutlined,
  DeleteOutlined,
  EditOutlined,
  MessageOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { Alert, Button, Empty, Input, Modal, Popconfirm, Spin } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';

import {
  ApiError,
  createChatThread,
  deleteChatThread,
  getChatHistory,
  listChatThreads,
  renameChatThread,
  streamChat,
} from './api';
import { MarkdownMessage } from './MarkdownMessage';
import type { ChatMessage, ChatThread, AuthSession } from './types';

interface AdvisorPageProps {
  session: AuthSession;
  onBack: () => void;
  onSessionExpired: () => void;
  onMyPlans: () => void;
}

export function AdvisorPage({
  session,
  onBack,
  onSessionExpired,
  onMyPlans,
}: AdvisorPageProps) {
  const [threads, setThreads] = useState<ChatThread[]>([]);
  const [activeThreadId, setActiveThreadId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [loadingThreads, setLoadingThreads] = useState(true);
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [renamingThread, setRenamingThread] = useState<ChatThread | null>(null);
  const [renameTitle, setRenameTitle] = useState('');
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const historyRequest = useRef<AbortController | null>(null);
  const streamRequest = useRef<AbortController | null>(null);

  // 离开页面时取消请求，避免旧响应继续写入已切换的会话。
  useEffect(
    () => () => {
      historyRequest.current?.abort();
      streamRequest.current?.abort();
    },
    [],
  );

  const loadMessages = useCallback(
    async (threadId: string) => {
      historyRequest.current?.abort();
      const controller = new AbortController();
      historyRequest.current = controller;
      setLoadingMessages(true);
      setError(null);
      try {
        const history = await getChatHistory(threadId, controller.signal);
        if (!controller.signal.aborted) setMessages(history.messages);
      } catch (reason) {
        if (controller.signal.aborted) return;
        if (isUnauthorized(reason)) {
          onSessionExpired();
          return;
        }
        setError(reason instanceof Error ? reason.message : '会话历史加载失败');
      } finally {
        if (!controller.signal.aborted) setLoadingMessages(false);
      }
    },
    [onSessionExpired],
  );

  const openThread = useCallback(
    async (threadId: string) => {
      streamRequest.current?.abort();
      setSending(false);
      setActiveThreadId(threadId);
      await loadMessages(threadId);
    },
    [loadMessages],
  );

  const createThread = useCallback(async () => {
    historyRequest.current?.abort();
    streamRequest.current?.abort();
    setLoadingMessages(false);
    const thread = await createChatThread();
    setThreads((current) => [thread, ...current]);
    setActiveThreadId(thread.id);
    setMessages([]);
    return thread;
  }, []);

  async function startNewThread() {
    setSending(false);
    setError(null);
    try {
      await createThread();
    } catch (reason) {
      if (isUnauthorized(reason)) {
        onSessionExpired();
        return;
      }
      setError(reason instanceof Error ? reason.message : '新建会话失败');
    }
  }

  async function renameThread() {
    if (!renamingThread) return;
    const title = renameTitle.trim();
    if (!title) return;
    setError(null);
    try {
      const updated = await renameChatThread(renamingThread.id, title);
      setThreads((current) =>
        current.map((thread) => (thread.id === updated.id ? updated : thread)),
      );
      setRenamingThread(null);
      setRenameTitle('');
    } catch (reason) {
      if (isUnauthorized(reason)) {
        onSessionExpired();
        return;
      }
      setError(reason instanceof Error ? reason.message : '重命名会话失败');
    }
  }

  async function deleteThread(threadId: string) {
    if (activeThreadId === threadId) {
      streamRequest.current?.abort();
      historyRequest.current?.abort();
      setSending(false);
      setLoadingMessages(false);
    }
    setError(null);
    try {
      await deleteChatThread(threadId);
      const nextThreads = threads.filter((thread) => thread.id !== threadId);
      setThreads(nextThreads);
      if (activeThreadId === threadId) {
        const nextActive = nextThreads[0];
        setActiveThreadId(nextActive?.id ?? null);
        setMessages([]);
        if (nextActive) await loadMessages(nextActive.id);
      }
    } catch (reason) {
      if (isUnauthorized(reason)) {
        onSessionExpired();
        return;
      }
      setError(reason instanceof Error ? reason.message : '删除会话失败');
    }
  }

  useEffect(() => {
    let ignore = false;
    async function loadThreads() {
      setLoadingThreads(true);
      setError(null);
      try {
        const loaded = await listChatThreads();
        if (ignore) return;
        if (loaded.length === 0) {
          const thread = await createChatThread();
          if (ignore) return;
          setThreads([thread]);
          setActiveThreadId(thread.id);
          setMessages([]);
          return;
        }
        setThreads(loaded);
        setActiveThreadId(loaded[0].id);
        await loadMessages(loaded[0].id);
      } catch (reason) {
        if (!ignore) {
          if (isUnauthorized(reason)) {
            onSessionExpired();
            return;
          }
          setError(reason instanceof Error ? reason.message : '会话加载失败');
        }
      } finally {
        if (!ignore) setLoadingThreads(false);
      }
    }
    void loadThreads();
    return () => {
      ignore = true;
    };
  }, [loadMessages, onSessionExpired]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, sending]);

  async function sendMessage() {
    const content = input.trim();
    if (!content || sending) return;
    setInput('');
    setSending(true);
    setError(null);
    const controller = new AbortController();
    try {
      const threadId = activeThreadId ?? (await createThread()).id;
      // 新建会话会清理旧请求，本轮流重新归属到当前控制器。
      streamRequest.current = controller;
      setMessages((current) => [
        ...current,
        { role: 'user', content },
        { role: 'assistant', content: '' },
      ]);
      await streamChat(
        threadId,
        content,
        (chunk) => {
          if (!controller.signal.aborted) appendAssistantChunk(chunk);
        },
        controller.signal,
      );
      setThreads((current) =>
        current.map((thread) =>
          thread.id === threadId
            ? {
                ...thread,
                title:
                  thread.title === '保险咨询'
                    ? content.slice(0, 20)
                    : thread.title,
              }
            : thread,
        ),
      );
    } catch (reason) {
      if (controller.signal.aborted) return;
      if (isUnauthorized(reason)) {
        onSessionExpired();
        return;
      }
      setError(reason instanceof Error ? reason.message : '消息发送失败');
      setMessages((current) => current.filter((item) => item.content !== ''));
    } finally {
      if (streamRequest.current === controller) setSending(false);
    }
  }

  function appendAssistantChunk(chunk: string) {
    setMessages((current) => {
      const next = [...current];
      const last = next[next.length - 1];
      if (last?.role === 'assistant') {
        next[next.length - 1] = { ...last, content: last.content + chunk };
      }
      return next;
    });
  }

  return (
    <div className="advisor-page">
      <header className="advisor-header">
        <div className="brand">
          <span className="brand__mark">
            <SafetyCertificateOutlined />
          </span>
          <span>
            <strong>安心保</strong>
            <small>智能顾问</small>
          </span>
        </div>
        <div>
          <span>{session.user.displayName || session.user.username}</span>
          <Button onClick={onMyPlans}>我的方案</Button>
          <Button icon={<ArrowLeftOutlined />} onClick={onBack}>
            返回商城
          </Button>
        </div>
      </header>
      <main className="advisor-workspace">
        <aside className="thread-panel">
          <Button
            block
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => void startNewThread()}
          >
            新建咨询
          </Button>
          <div className="thread-list">
            {loadingThreads ? <Spin /> : null}
            {!loadingThreads && threads.length === 0 ? (
              <Empty description="暂无会话" />
            ) : null}
            {threads.map((thread) => (
              <div
                className={
                  thread.id === activeThreadId
                    ? 'thread-item is-active'
                    : 'thread-item'
                }
                key={thread.id}
              >
                <button
                  type="button"
                  onClick={() => void openThread(thread.id)}
                >
                  <MessageOutlined />
                  <span className="thread-text">
                    <strong>{thread.title}</strong>
                    <small>{formatThreadTime(thread.created_at)}</small>
                  </span>
                </button>
                <div className="thread-actions">
                  <Button
                    type="text"
                    size="small"
                    icon={<EditOutlined />}
                    onClick={() => {
                      setRenamingThread(thread);
                      setRenameTitle(thread.title);
                    }}
                  />
                  <Popconfirm
                    title="删除会话"
                    description="删除后会同时清理该会话的历史状态。"
                    okText="删除"
                    cancelText="取消"
                    onConfirm={() => void deleteThread(thread.id)}
                  >
                    <Button
                      type="text"
                      danger
                      size="small"
                      icon={<DeleteOutlined />}
                    />
                  </Popconfirm>
                </div>
              </div>
            ))}
          </div>
        </aside>

        <section className="chat-panel">
          {error ? (
            <Alert
              className="chat-alert"
              type="error"
              showIcon
              message={error}
            />
          ) : null}
          <div className="chat-scroll">
            {loadingMessages ? (
              <div className="chat-loading">
                <Spin />
              </div>
            ) : messages.length === 0 ? (
              <div className="chat-empty">
                <CustomerServiceOutlined />
                <h1>你好，我是安心保智能顾问</h1>
                <p>
                  告诉我你的年龄、职业、预算和关注的保障类型，我会协助你梳理保险方案。
                </p>
              </div>
            ) : (
              messages.map((message, index) => (
                <div
                  className={`chat-message chat-message--${message.role}`}
                  key={`${message.role}-${index}`}
                >
                  <div>
                    {message.content ? (
                      <MarkdownMessage content={message.content} />
                    ) : (
                      '正在整理回复...'
                    )}
                  </div>
                </div>
              ))
            )}
            <div ref={bottomRef} />
          </div>
          <div className="chat-composer">
            <Input.TextArea
              value={input}
              autoSize={{ minRows: 1, maxRows: 4 }}
              placeholder="描述你的保险需求，例如：30岁，有医保，预算每年3000元，想配置医疗险和重疾险"
              disabled={sending}
              onChange={(event) => setInput(event.target.value)}
              onPressEnter={(event) => {
                if (!event.shiftKey) {
                  event.preventDefault();
                  void sendMessage();
                }
              }}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={sending}
              onClick={() => void sendMessage()}
            >
              发送
            </Button>
          </div>
        </section>
      </main>
      <Modal
        open={renamingThread !== null}
        title="重命名会话"
        okText="保存"
        cancelText="取消"
        onOk={() => void renameThread()}
        onCancel={() => {
          setRenamingThread(null);
          setRenameTitle('');
        }}
      >
        <Input
          value={renameTitle}
          maxLength={80}
          placeholder="请输入会话名称"
          onChange={(event) => setRenameTitle(event.target.value)}
          onPressEnter={() => void renameThread()}
        />
      </Modal>
    </div>
  );
}

function isUnauthorized(reason: unknown): boolean {
  return reason instanceof ApiError && reason.status === 401;
}

function formatThreadTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  const pad = (num: number) => String(num).padStart(2, '0');
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
}
