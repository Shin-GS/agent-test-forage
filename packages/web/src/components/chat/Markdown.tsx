// AI 답변(TEXT 파트) 마크다운 렌더러.
// react-markdown + remark-gfm(GFM: 표/취소선/체크박스/자동링크) + rehype-sanitize(XSS 방어).
//
// 보안 정책:
// - sanitize 스키마는 rehype-sanitize 기본(defaultSchema)을 확장한다.
// - 링크(a): href 스킴 화이트리스트(http/https/mailto)만 허용. javascript:/data: 등은 스키마 protocol
//   검증에서 제거된다. 실제 렌더는 아래 components.a 커스텀에서 target/rel 을 강제한다.
// - 이미지(img): 스키마 tagNames 에서 제외 → 외부 이미지 삽입/트래킹 픽셀 차단.
//
// 성능: content 파싱 비용이 있으므로 React.memo 로 감싼다(같은 content 면 재파싱 안 함).

import { memo } from "react";
import ReactMarkdown from "react-markdown";
import type { Components } from "react-markdown";
import rehypeSanitize, { defaultSchema } from "rehype-sanitize";
import remarkGfm from "remark-gfm";

// 기본 스키마 확장: 이미지 제거 + 링크 스킴 화이트리스트.
const sanitizeSchema = {
  ...defaultSchema,
  // img 태그를 허용 목록에서 제거(외부 이미지 차단).
  tagNames: (defaultSchema.tagNames ?? []).filter((tag) => tag !== "img"),
  protocols: {
    ...defaultSchema.protocols,
    // href 는 안전한 스킴만 허용(javascript:/data: 등 차단).
    href: ["http", "https", "mailto"],
  },
};

const components: Components = {
  // 링크: 새 탭 + rel 강제(탭 네비게이션 하이재킹/leak 방지).
  a({ children, href, ...rest }) {
    return (
      <a href={href} target="_blank" rel="noopener noreferrer" {...rest}>
        {children}
      </a>
    );
  },
  // 코드블록: 지금은 기본 <pre><code> 렌더로 충분.
  // TODO: 후속으로 복사 버튼 / 신택스 하이라이팅을 이 커스텀 렌더에 얹을 수 있다.
  code({ children, ...rest }) {
    return <code {...rest}>{children}</code>;
  },
};

interface Props {
  /** 렌더할 마크다운 원본 텍스트 */
  content: string;
}

function MarkdownBase({ content }: Props) {
  return (
    <div className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        rehypePlugins={[[rehypeSanitize, sanitizeSchema]]}
        components={components}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
}

export const Markdown = memo(MarkdownBase);
