package com.testforge.ai;

import com.testforge.ai.enums.ToolName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RuleBasedIntentResolver ëª?ê·œì¹™ ?¨ìœ„ ?ŒìŠ¤?? Spring ì»¨í…?¤íŠ¸ ?†ì´ ?œìˆ˜ ë¡œì§ë§?ê²€ì¦í•œ??
 * ?¤ì œ AI êµ¬í˜„?¼ë¡œ êµì²´?˜ê¸° ?„ê¹Œì§€ ?„ì²´ ì±„íŒ… ?ë¦„????ê·œì¹™??êµ¬ë™?˜ë?ë¡? ë¶„ê¸°ë³„ë¡œ ëª»ì„ ë°•ì•„?”ë‹¤.
 */
class RuleBasedIntentResolverTest {

    private final RuleBasedIntentResolver resolver = new RuleBasedIntentResolver();

    /** ì»¨í…?¤íŠ¸ ë¹Œë” ?¬í¼ (?œë¹„??ì§€???¬ë?/?ˆì‹œ???œë¹„??ì°¸ì¡°ë¥?ì¼€?´ìŠ¤ë³„ë¡œ ì¡°ë¦½) */
    private IntentContext ctx(String utterance, Long apiSpecId,
                              List<RecipeCandidate> recipes, List<ServiceOption> services,
                              String referenceId) {
        return new IntentContext(1L, 100L, utterance, apiSpecId, recipes, services, referenceId, List.of());
    }

    // ?€?€ ?œë¹„??ë¯¸ì????€?€

    @Test
    void noService_smallTalk_returnsChat() {
        IntentContext context = ctx("?ˆë…•?˜ì„¸??, null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "ì»¤ë¨¸??)), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CHAT);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void noService_taskUtterance_returnsSelectService() {
        IntentContext context = ctx("?Œì›ê°€???ŒìŠ¤??ê³„ì • ë§Œë“¤?´ì¤˜", null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "ì»¤ë¨¸??)), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SELECT_SERVICE);
    }

    @Test
    void noService_utteranceMentionsServiceName_suggestsThatService() {
        IntentContext context = ctx("demo-shop ?Œì›ê°€???´ì¤˜", null, List.of(),
                List.of(ServiceOption.of(1L, "demo-shop", "ì»¤ë¨¸??),
                        ServiceOption.of(2L, "billing", "ê²°ì œ")), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SELECT_SERVICE);
        assertThat(result.suggestedServices()).extracting(ServiceOption::name).contains("demo-shop");
    }

    // ?€?€ ?œë¹„??ì§€???€?€

    @Test
    void withService_noRecipeMatch_returnsNoMatch() {
        IntentContext context = ctx("ì¡´ì¬?˜ì??ŠëŠ”?‘ì—…xyz", 1L,
                List.of(RecipeCandidate.of(10L, "?Œì›ê°€??, "?Œì›ê°€???ˆì‹œ??, List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.NO_MATCH);
    }

    @Test
    void withService_singleRecipeMatch_returnsExecuteRecipe() {
        IntentContext context = ctx("?Œì›ê°€???´ì¤˜", 1L,
                List.of(RecipeCandidate.of(10L, "?Œì›ê°€??, "?Œì›ê°€???ˆì‹œ??, List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(10L);
    }

    @Test
    void withService_multipleRecipeMatch_returnsShowCandidates() {
        IntentContext context = ctx("ì£¼ë¬¸ ê´€???‘ì—…", 1L,
                List.of(RecipeCandidate.of(10L, "ì£¼ë¬¸ ?ì„±", "ì£¼ë¬¸ ?ì„±", List.of()),
                        RecipeCandidate.of(11L, "ì£¼ë¬¸ ì·¨ì†Œ", "ì£¼ë¬¸ ì·¨ì†Œ", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.SHOW_CANDIDATES);
        assertThat(result.candidates()).extracting(RecipeCandidate::id).containsExactlyInAnyOrder(10L, 11L);
    }

    @Test
    void withService_tagMatch_returnsExecuteRecipe() {
        // ?´ë¦„?€ ??ê²¹ì¹˜ì§€ë§??œê·¸(signup)ê°€ ë°œí™”???¬í•¨?˜ë©´ ë§¤ì¹­
        IntentContext context = ctx("signup ì§„í–‰", 1L,
                List.of(RecipeCandidate.of(10L, "ê³„ì • ?ì„±", "?¤ëª…", List.of("signup"))),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(10L);
    }

    @Test
    void withService_referenceId_prioritizesThatRecipe() {
        // ì°¸ì¡° ?œê·¸ê°€ ?ˆìœ¼ë©?ë°œí™” ë§¤ì¹­ê³?ë¬´ê??˜ê²Œ ?´ë‹¹ ?ˆì‹œ???°ì„  ?¤í–‰
        IntentContext context = ctx("?„ë¬´ë§?, 1L,
                List.of(RecipeCandidate.of(10L, "?Œì›ê°€??, "?¤ëª…", List.of()),
                        RecipeCandidate.of(20L, "ë¡œê·¸??, "?¤ëª…", List.of())),
                List.of(), "recipe_20");

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.EXECUTE_RECIPE);
        assertThat(result.recipeId()).isEqualTo(20L);
    }

    @Test
    void withService_tooShortUtterance_returnsClarify() {
        IntentContext context = ctx("??, 1L,
                List.of(RecipeCandidate.of(10L, "?Œì›ê°€??, "?¤ëª…", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CLARIFY);
        assertThat(result.message()).isNotBlank();
    }

    @Test
    void withService_smallTalk_returnsChat() {
        IntentContext context = ctx("ê³ ë§ˆ?Œìš”", 1L,
                List.of(RecipeCandidate.of(10L, "?Œì›ê°€??, "?¤ëª…", List.of())),
                List.of(), null);

        IntentResult result = resolver.resolve(context);

        assertThat(result.tool()).isEqualTo(ToolName.CHAT);
    }
}
