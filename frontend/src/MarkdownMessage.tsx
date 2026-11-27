import type { ReactNode } from 'react';

interface MarkdownMessageProps {
  content: string;
}

/** 把大模型吐出的 markdown 渲染成 HTML，只支持标题/段落/引用/列表/代码/表格这几样 */
export function MarkdownMessage({ content }: MarkdownMessageProps) {
  const blocks = parseBlocks(content);
  return (
    <div className="markdown-message">
      {blocks.map((block, index) => renderBlock(block, index))}
    </div>
  );
}

type MarkdownBlock =
  | { type: 'heading'; level: 1 | 2 | 3 | 4 | 5 | 6; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'quote'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; text: string }
  | { type: 'table'; headers: string[]; rows: string[][] };

function parseBlocks(markdown: string): MarkdownBlock[] {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const blocks: MarkdownBlock[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; items: string[] } | null = null;
  let code: string[] | null = null;

  function flushParagraph() {
    if (paragraph.length === 0) return;
    blocks.push({ type: 'paragraph', text: paragraph.join('\n') });
    paragraph = [];
  }

  function flushList() {
    if (!list) return;
    blocks.push({ type: 'list', ordered: list.ordered, items: list.items });
    list = null;
  }

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    if (line.trim().startsWith('```')) {
      flushParagraph();
      flushList();
      if (code) {
        blocks.push({ type: 'code', text: code.join('\n') });
        code = null;
      } else {
        code = [];
      }
      continue;
    }
    if (code) {
      code.push(line);
      continue;
    }

    if (!line.trim()) {
      flushParagraph();
      flushList();
      continue;
    }

    const table = parseTable(lines, index);
    if (table) {
      flushParagraph();
      flushList();
      blocks.push({ type: 'table', headers: table.headers, rows: table.rows });
      index = table.nextIndex - 1;
      continue;
    }

    const heading = /^(#{1,6})\s+(.+)$/.exec(line);
    if (heading) {
      flushParagraph();
      flushList();
      blocks.push({
        type: 'heading',
        level: heading[1].length as 1 | 2 | 3 | 4 | 5 | 6,
        text: heading[2],
      });
      continue;
    }

    const unordered = /^\s*[-*]\s+(.+)$/.exec(line);
    const ordered = /^\s*\d+[.)]\s+(.+)$/.exec(line);
    if (unordered || ordered) {
      flushParagraph();
      const isOrdered = Boolean(ordered);
      if (!list || list.ordered !== isOrdered) {
        flushList();
        list = { ordered: isOrdered, items: [] };
      }
      list.items.push((unordered ?? ordered)?.[1] ?? '');
      continue;
    }

    const quote = /^\s*>\s?(.+)$/.exec(line);
    if (quote) {
      flushParagraph();
      flushList();
      blocks.push({ type: 'quote', text: quote[1] });
      continue;
    }

    flushList();
    paragraph.push(line);
  }

  if (code) blocks.push({ type: 'code', text: code.join('\n') });
  flushParagraph();
  flushList();
  return blocks;
}

function renderBlock(block: MarkdownBlock, index: number) {
  switch (block.type) {
    case 'heading': {
      const Tag = `h${Math.min(block.level + 2, 6)}` as 'h3' | 'h4' | 'h5' | 'h6';
      return <Tag key={index}>{renderInline(block.text)}</Tag>;
    }
    case 'paragraph':
      return <p key={index}>{renderInline(block.text)}</p>;
    case 'quote':
      return <blockquote key={index}>{renderInline(block.text)}</blockquote>;
    case 'list': {
      const Tag = block.ordered ? 'ol' : 'ul';
      return (
        <Tag key={index}>
          {block.items.map((item, itemIndex) => (
            <li key={`${index}-${itemIndex}`}>{renderInline(item)}</li>
          ))}
        </Tag>
      );
    }
    case 'code':
      return (
        <pre key={index}>
          <code>{block.text}</code>
        </pre>
      );
    case 'table':
      return (
        <div className="markdown-table-wrap" key={index}>
          <table>
            <thead>
              <tr>
                {block.headers.map((header, headerIndex) => (
                  <th key={`${index}-h-${headerIndex}`}>{renderInline(header)}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {block.rows.map((row, rowIndex) => (
                <tr key={`${index}-r-${rowIndex}`}>
                  {block.headers.map((_, cellIndex) => (
                    <td key={`${index}-r-${rowIndex}-${cellIndex}`}>
                      {renderInline(row[cellIndex] ?? '')}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

function parseTable(lines: string[], startIndex: number) {
  const header = splitTableRow(lines[startIndex]);
  const separator = splitTableRow(lines[startIndex + 1] ?? '');
  if (!header || !separator || header.length === 0 || separator.length !== header.length) {
    return null;
  }
  if (!separator.every((cell) => /^:?-{3,}:?$/.test(cell.trim()))) {
    return null;
  }

  const rows: string[][] = [];
  let nextIndex = startIndex + 2;
  while (nextIndex < lines.length) {
    const row = splitTableRow(lines[nextIndex]);
    if (!row || row.length === 0) break;
    rows.push(row);
    nextIndex += 1;
  }
  return { headers: header, rows, nextIndex };
}

function splitTableRow(line: string): string[] | null {
  const trimmed = line.trim();
  if (!trimmed.includes('|')) return null;
  return trimmed
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim());
}

/** 行内只认三种：链接、加粗、行内代码 */
function renderInline(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const pattern = /(\[[^\]]+\]\([^)]+\)|\*\*[^*]+\*\*|`[^`]+`)/g;
  let lastIndex = 0;
  for (const match of text.matchAll(pattern)) {
    if (match.index > lastIndex) {
      nodes.push(text.slice(lastIndex, match.index));
    }
    const token = match[0];
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
    if (link) {
      nodes.push(
        <a key={match.index} href={link[2]} target="_blank" rel="noreferrer">
          {link[1]}
        </a>,
      );
    } else if (token.startsWith('**')) {
      nodes.push(<strong key={match.index}>{renderInline(token.slice(2, -2))}</strong>);
    } else {
      nodes.push(<code key={match.index}>{token.slice(1, -1)}</code>);
    }
    lastIndex = match.index + token.length;
  }
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex));
  return nodes;
}
