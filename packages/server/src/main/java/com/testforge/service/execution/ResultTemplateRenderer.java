package com.testforge.service.execution;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.context.JavaBeanValueResolver;
import com.github.jknack.handlebars.context.MapValueResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 레시피 결과 메시지 템플릿(⑤) 렌더러. Handlebars(로직리스)로 값 치환/반복/조건 + 등록된 헬퍼만 지원한다.
 * 임의 코드 실행은 불가하며, 렌더 컨텍스트는 <b>④ 결과 정의 값(resultValues) + ② 사용자 입력(userInput)</b>으로 한정한다.
 *
 * <p>렌더는 실행 완료 시 서버에서 1회 수행되어 <b>완성된 마크다운 문자열</b>을 만든다(authoring.md ⑤ / execution.md).
 * FE는 이 마크다운을 렌더만 한다(문법 비의존). 목록은 전량 렌더하되, 배열당 최대 {@value #MAX_LIST_SIZE}개
 * 하드 상한을 렌더러가 강제하고 초과 시 결과 끝에 "…외 N건" 안내를 자동으로 덧붙인다(시스템 보호선).
 *
 * <p><b>보안/이스케이프</b>: 이 렌더러는 <b>마크다운 문자열</b>을 만드는 단계다. Handlebars 기본 HTML
 * 이스케이프를 켜두면 값의 {@code < > & " '}가 {@code &lt;} 등으로 바뀌어 마크다운 텍스트를 깨뜨리므로
 * {@link EscapingStrategy#NOOP}로 이스케이프를 끈다. XSS 방어(위험 태그/속성 제거)는 FE의 rehype-sanitize가
 * 최종 담당한다(authoring.md ⑤ 마크다운 렌더). 임의 코드 실행 차단은 로직리스 + 등록 헬퍼 화이트리스트로 보장한다.
 */
@Component
public class ResultTemplateRenderer {

    private static final Logger log = LoggerFactory.getLogger(ResultTemplateRenderer.class);

    /** 배열당 렌더 하드 상한(시스템 보호선). 초과분은 잘라내고 "…외 N건"으로 안내. */
    static final int MAX_LIST_SIZE = 500;

    private final Handlebars handlebars;

    public ResultTemplateRenderer() {
        // HTML 이스케이프 OFF: 결과는 마크다운 문자열이며, XSS는 FE rehype-sanitize가 최종 차단한다.
        this.handlebars = new Handlebars().with(EscapingStrategy.NOOP);
        registerHelpers(this.handlebars);
    }

    /**
     * ⑤ 템플릿을 Handlebars로 렌더해 마크다운 문자열을 만든다.
     *
     * @param template     결과 메시지 템플릿(Handlebars + 마크다운). null/blank면 빈 문자열.
     * @param resultValues ④ 결과 정의로 산출된 값(스칼라/배열/객체 허용). 최상위 key로 참조된다.
     * @param userInput    ② 사용자 입력. {@code {{userInput.key}}}로 참조된다.
     * @return 렌더된 마크다운 문자열. 렌더 실패 시 템플릿 원문을 그대로 반환(결과 유실 방지).
     */
    public String render(String template, Map<String, Object> resultValues, Map<String, Object> userInput) {
        if (template == null || template.isBlank()) {
            return "";
        }

        Map<String, Object> safeValues = resultValues == null ? Map.of() : resultValues;
        Map<String, Object> safeInput = userInput == null ? Map.of() : userInput;

        // 배열 하드 상한 적용: 각 배열 값을 최대 MAX_LIST_SIZE개로 잘라내고 초과 건수를 합산한다.
        int[] overflow = {0};
        Map<String, Object> model = new LinkedHashMap<>(applyListCap(safeValues, overflow));
        model.put("userInput", safeInput);

        try {
            Template compiled = handlebars.compileInline(template);
            Context context = Context.newBuilder(model)
                    // Map 우선 + JavaBean 순으로만 값을 해석한다(메서드 임의 호출 경로 제한).
                    .resolver(MapValueResolver.INSTANCE, JavaBeanValueResolver.INSTANCE)
                    .build();
            String rendered = compiled.apply(context);
            return appendOverflowNotice(rendered, overflow[0]);
        } catch (IOException | RuntimeException e) {
            // 템플릿 문법 오류/렌더 실패가 실행 완료를 막지 않도록 원문을 폴백으로 반환한다.
            log.warn("Result template render failed; falling back to raw template. reason={}", e.getMessage());
            return template;
        }
    }

    /**
     * resultValues의 배열 값을 최대 {@link #MAX_LIST_SIZE}개로 잘라낸다. 잘린 초과 건수는 {@code overflow[0]}에
     * 누적된다. 배열이 아닌 값(스칼라/객체)은 그대로 둔다. 원본 맵은 변경하지 않는다(방어적 복사).
     */
    private Map<String, Object> applyListCap(Map<String, Object> values, int[] overflow) {
        Map<String, Object> capped = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : values.entrySet()) {
            Object v = e.getValue();
            if (v instanceof List<?> list && list.size() > MAX_LIST_SIZE) {
                overflow[0] += list.size() - MAX_LIST_SIZE;
                capped.put(e.getKey(), new ArrayList<>(list.subList(0, MAX_LIST_SIZE)));
            } else {
                capped.put(e.getKey(), v);
            }
        }
        return capped;
    }

    /** 배열 하드 상한으로 잘린 항목이 있으면 렌더 결과 끝에 "…외 N건" 안내를 덧붙인다. */
    private String appendOverflowNotice(String rendered, int overflow) {
        if (overflow <= 0) {
            return rendered;
        }
        String base = rendered == null ? "" : rendered;
        return base + "\n\n_…외 " + overflow + "건 생략됨_";
    }

    /**
     * 헬퍼 화이트리스트 등록: formatNumber(천단위 콤마), eq/gt/lt(비교), default(빈값 대체).
     * 등록된 것만 동작하므로 임의 함수 실행은 불가하다(authoring.md ⑤ 헬퍼).
     */
    private void registerHelpers(Handlebars hb) {
        // 천단위 콤마. 숫자/숫자형 문자열만 포맷하고, 파싱 불가하면 원값을 그대로 낸다.
        hb.registerHelper("formatNumber", (Helper<Object>) (value, options) -> {
            Number number = toNumber(value);
            if (number == null) {
                return value == null ? "" : String.valueOf(value);
            }
            return NumberFormat.getNumberInstance(Locale.US).format(number);
        });

        // eq/gt/lt: 블록 헬퍼 없이 subexpression으로 boolean을 반환한다({{#if (gt a b)}} 형태).
        hb.registerHelper("eq", (Helper<Object>) (a, options) -> {
            Object b = options.param(0, null);
            return equalsLoose(a, b);
        });
        hb.registerHelper("gt", (Helper<Object>) (a, options) -> compare(a, options.param(0, null)) > 0);
        hb.registerHelper("lt", (Helper<Object>) (a, options) -> compare(a, options.param(0, null)) < 0);

        // default: 값이 null/빈 문자열이면 대체값을 낸다.
        hb.registerHelper("default", (Helper<Object>) (value, options) -> {
            Object fallback = options.param(0, "");
            if (value == null) {
                return fallback;
            }
            if (value instanceof CharSequence cs && cs.toString().isBlank()) {
                return fallback;
            }
            return value;
        });
    }

    // ── helpers (Java) ──

    private Number toNumber(Object value) {
        if (value instanceof Number n) {
            return n;
        }
        if (value instanceof CharSequence cs) {
            String s = cs.toString().trim().replace(",", "");
            if (s.isEmpty()) {
                return null;
            }
            try {
                if (s.contains(".")) {
                    return Double.parseDouble(s);
                }
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 느슨한 동등 비교: 숫자로 파싱 가능하면 숫자로, 아니면 문자열로 비교. */
    private boolean equalsLoose(Object a, Object b) {
        if (a == null || b == null) {
            return a == b;
        }
        Number na = toNumber(a);
        Number nb = toNumber(b);
        if (na != null && nb != null) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return String.valueOf(a).equals(String.valueOf(b));
    }

    /** 숫자 비교(양쪽 숫자 파싱 가능 시). 파싱 불가하면 문자열 사전순 비교로 폴백. */
    private int compare(Object a, Object b) {
        Number na = toNumber(a);
        Number nb = toNumber(b);
        if (na != null && nb != null) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }
}
