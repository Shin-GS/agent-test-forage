package com.testforge.service.execution;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ResultTemplateRenderer 단위 테스트. Spring 컨텍스트 없이 순수 렌더 로직만 검증한다.
 * 결과 메시지 템플릿(⑤)의 값치환/반복/조건/헬퍼/마크다운/500 상한/이스케이프 정책을 못 박아둔다.
 */
class ResultTemplateRendererTest {

    private final ResultTemplateRenderer renderer = new ResultTemplateRenderer();

    private Map<String, Object> values(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    // ── 기본 값 치환 ──

    @Test
    void render_scalarSubstitution() {
        String out = renderer.render("{{ownerName}}님의 잔액", values("ownerName", "이영희"), Map.of());
        assertThat(out).isEqualTo("이영희님의 잔액");
    }

    @Test
    void render_userInputReference() {
        String out = renderer.render("수량 {{userInput.quantity}}개", Map.of(), Map.of("quantity", 3));
        assertThat(out).isEqualTo("수량 3개");
    }

    @Test
    void render_blankTemplate_returnsEmpty() {
        assertThat(renderer.render("", values("a", 1), Map.of())).isEmpty();
        assertThat(renderer.render(null, values("a", 1), Map.of())).isEmpty();
    }

    // ── formatNumber 헬퍼 ──

    @Test
    void formatNumber_addsThousandSeparator() {
        assertThat(renderer.render("{{formatNumber balance}}", values("balance", 500000), Map.of()))
                .isEqualTo("500,000");
        // 숫자형 문자열도 포맷
        assertThat(renderer.render("{{formatNumber balance}}", values("balance", "1900000"), Map.of()))
                .isEqualTo("1,900,000");
    }

    @Test
    void formatNumber_nonNumber_returnsRaw() {
        assertThat(renderer.render("{{formatNumber name}}", values("name", "abc"), Map.of()))
                .isEqualTo("abc");
    }

    // ── eq/gt/lt + if 조건 ──

    @Test
    void condition_gt_true() {
        String tpl = "{{#if (gt balance 0)}}잔액 있음{{else}}잔액 없음{{/if}}";
        assertThat(renderer.render(tpl, values("balance", 100), Map.of())).isEqualTo("잔액 있음");
        assertThat(renderer.render(tpl, values("balance", 0), Map.of())).isEqualTo("잔액 없음");
    }

    @Test
    void condition_eq() {
        String tpl = "{{#if (eq status \"ACTIVE\")}}활성{{/if}}";
        assertThat(renderer.render(tpl, values("status", "ACTIVE"), Map.of())).isEqualTo("활성");
        assertThat(renderer.render(tpl, values("status", "INACTIVE"), Map.of())).isEmpty();
    }

    // ── default 헬퍼 ──

    @Test
    void default_replacesBlank() {
        assertThat(renderer.render("{{default nickname \"익명\"}}", values("nickname", ""), Map.of()))
                .isEqualTo("익명");
        assertThat(renderer.render("{{default nickname \"익명\"}}", values("nickname", "홍길동"), Map.of()))
                .isEqualTo("홍길동");
    }

    // ── each 목록 렌더 (마크다운 표) ──

    @Test
    void each_rendersMarkdownTableRows() {
        List<Map<String, Object>> depts = List.of(
                values("name", "개발팀", "count", 12),
                values("name", "영업팀", "count", 8));
        String tpl = "| 부서 | 인원 |\n|------|------|\n{{#each departments}}| {{this.name}} | {{this.count}} |\n{{/each}}";
        String out = renderer.render(tpl, values("departments", depts), Map.of());

        assertThat(out).contains("| 개발팀 | 12 |");
        assertThat(out).contains("| 영업팀 | 8 |");
        // 표 헤더/구분선 유지
        assertThat(out).contains("| 부서 | 인원 |");
        assertThat(out).contains("|------|------|");
    }

    @Test
    void each_scalarArray() {
        String tpl = "{{#each names}}- {{this}}\n{{/each}}";
        String out = renderer.render(tpl, values("names", List.of("a", "b", "c")), Map.of());
        assertThat(out).contains("- a").contains("- b").contains("- c");
    }

    // ── 500 하드 상한 + "외 N건" ──

    @Test
    void listCap_truncatesAt500_andAppendsNotice() {
        List<Integer> big = IntStream.rangeClosed(1, 620).boxed().collect(Collectors.toList());
        String tpl = "{{#each items}}{{this}},{{/each}}";
        String out = renderer.render(tpl, values("items", big), Map.of());

        // 500번째까지만 렌더(501은 없음)
        assertThat(out).contains("500,");
        assertThat(out).doesNotContain("501,");
        // 초과 120건 안내
        assertThat(out).contains("외 120건 생략됨");
    }

    @Test
    void listCap_underLimit_noNotice() {
        String tpl = "{{#each items}}{{this}},{{/each}}";
        String out = renderer.render(tpl, values("items", List.of(1, 2, 3)), Map.of());
        assertThat(out).doesNotContain("생략됨");
    }

    // ── 이스케이프 OFF (마크다운 보존) ──

    @Test
    void render_doesNotHtmlEscape() {
        // 값에 < > & 가 있어도 HTML 엔티티로 변환되지 않아야 마크다운/텍스트가 보존된다.
        String out = renderer.render("조건: {{expr}}", values("expr", "a < b & c > d"), Map.of());
        assertThat(out).isEqualTo("조건: a < b & c > d");
        assertThat(out).doesNotContain("&lt;").doesNotContain("&amp;").doesNotContain("&gt;");
    }

    @Test
    void render_preservesMarkdownEmphasis() {
        String out = renderer.render("**{{name}}**님", values("name", "이영희"), Map.of());
        assertThat(out).isEqualTo("**이영희**님");
    }

    // ── 렌더 실패 폴백 ──

    @Test
    void render_invalidTemplate_returnsRaw() {
        // 닫히지 않은 블록 → 컴파일/렌더 실패 → 원문 폴백(결과 유실 방지)
        String broken = "{{#each items}}{{this}}";
        String out = renderer.render(broken, values("items", List.of(1)), Map.of());
        assertThat(out).isEqualTo(broken);
    }
}
