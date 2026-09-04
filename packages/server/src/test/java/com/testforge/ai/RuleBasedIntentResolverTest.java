package com.testforge.ai;

import com.testforge.ai.enums.ToolName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleBasedIntentResolver 목 규칙 단위 테스트. Spring 컨텍스트 없이 순수 로직만 검증한다.
 * 실제 AI 구현으로 교체되기 전까지 전체 채팅 흐름이 이 규칙대로 구동되므로, 분기별로 못을 박아둔다.
 */
class RuleBasedIntentResolverTest {

    private final RuleBasedIntentResolver resolver = new RuleBasedIntentResolver();

    /** 컨텍스트 빌더 헬퍼 (서비스 지정 여부/후보/서비스 참조를 케이스별로 조립) */
    private IntentContext ctx(String utterance, Long apiSpecId,
                              List<RecipeCandidate> recipes, List<ServiceOption> services,
                              String referenceId) {
        return new IntentContext(1L, 100L, utterance, apiSpecId, recipes, services, referenceId, List.of());
    }

    // ── 서비스 미지정 케이스 ──

    @Test
    void noService_smallTalk_returnsChat() {
        IntentContext context = ctx("안녕하세요", null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "커머스")), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CHAT);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void noService_taskUtterance_returnsSelectService() {
        IntentContext context = ctx("회원가입 테스트 계정 만들어줘", null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "커머스")), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SELECT_SERVICE);
    }

    @Test
    void noService_utteranceMentionsServiceName_suggestsThatService() {
        IntentContext context = ctx("demo-shop 회원가입 해줘", null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "커머스"),
                        ServiceOption.of(2L, "billing", "결제")), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SELECT_SERVICE);
        assertThat(result.suggestedServices()).extracting(ServiceOption::name).contains("demo-shop");
    }

    // ── 서비스 지정 케이스 ──

    @Test
    void withService_noRecipeMatch_returnsNoMatch() {
        IntentContext context = ctx("존재하지않는작업xyz", 1L,
                List.of(RecipeCandidate.of(10L, "회원가입", "회원가입 레시피", List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.NO_MATCH);
    }

    @Test
    void withService_singleRecipeMatch_returnsExecuteRecipe() {
        IntentContext context = ctx("회원가입 해줘", 1L,
                List.of(RecipeCandidate.of(10L, "회원가입", "회원가입 레시피", List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(10L);
    }

    @Test
    void withService_multipleRecipeMatch_returnsShowCandidates() {
        IntentContext context = ctx("주문 관련 작업", 1L,
                List.of(RecipeCandidate.of(10L, "주문 생성", "주문 생성", List.of()),
                        RecipeCandidate.of(11L, "주문 취소", "주문 취소", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SHOW_CANDIDATES);
        assertThat(result.candidates()).extracting(RecipeCandidate::id).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void withService_tagMatch_returnsExecuteRecipe() {
        // 이름은 안 겹치지만 태그(signup)가 발화에 포함되면 매칭
        IntentContext context = ctx("signup 진행", 1L,
                List.of(RecipeCandidate.of(10L, "계정 생성", "설명", List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(10L);
    }

    @Test
    void withService_referenceId_prioritizesThatRecipe() {
        // 참조 태그가 있으면 발화 매칭과 무관하게 해당 레시피 우선 실행
        IntentContext context = ctx("아무거나", 1L,
                List.of(RecipeCandidate.of(10L, "회원가입", "설명", List.of()),
                        RecipeCandidate.of(20L, "로그인", "설명", List.of())),
                List.of(), "recipe_20");

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(20L);
    }

    @Test
    void withService_tooShortUtterance_returnsClarify() {
        IntentContext context = ctx("가", 1L,
                List.of(RecipeCandidate.of(10L, "회원가입", "설명", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CLARIFY);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void withService_smallTalk_returnsChat() {
        IntentContext context = ctx("고마워요", 1L,
                List.of(RecipeCandidate.of(10L, "회원가입", "설명", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CHAT);
    }
}
