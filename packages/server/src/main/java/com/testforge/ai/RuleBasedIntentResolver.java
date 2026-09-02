package com.testforge.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * IntentResolver의 규칙 기반 목(mock) 구현. OpenAI 키 없이도 전체 채팅 흐름(락·SSE·상태전이·tool
 * 핸들러)을 검증하기 위한 임시 구현이다. 실제 Spring AI 구현이 준비되면 교체된다
 * (ai-config.md의 "구조는 모델에 종속적이지 않게").
 *
 * <p><b>규칙 개요</b> (intent-classification.md 정책을 키워드 매칭으로 근사):
 * <ol>
 *   <li>서비스 미지정 → 인사/잡담이면 chat, 그 외에는 select_service(추천은 서비스명 부분일치)</li>
 *   <li>서비스 지정 →
 *     <ul>
 *       <li>referenceId가 레시피와 매칭되면 execute_recipe (우선 매칭)</li>
 *       <li>인사/잡담이면 chat</li>
 *       <li>발화가 너무 짧으면(의미 토큰 없음) clarify</li>
 *       <li>레시피 이름/태그 매칭 0개 → no_match</li>
 *       <li>매칭 1개 → execute_recipe</li>
 *       <li>매칭 2개 이상 → show_candidates (최대 5개)</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * <p>plan/propose_plan과 investigate는 이번 조각의 목 규칙에서 다루지 않는다(단일 매칭 위주).
 * 복합 플랜/정보 조회는 실제 AI 또는 다음 조각에서 확장한다.
 *
 * <p><b>fallback 전용:</b> 빈 등록은 {@code AiResolverConfig}가 관리한다. 실제 AI 구현
 * ({@link OpenAiCompatibleIntentResolver})이 없을 때(= {@code ai-test-forge.ai.api-key} 미설정)만
 * 이 목이 {@code IntentResolver} 빈으로 등록된다.
 */
public class RuleBasedIntentResolver implements IntentResolver {

    private static final Logger log = LoggerFactory.getLogger(RuleBasedIntentResolver.class);

    /** 후보 최대 노출 수 (intent-classification.md: 최대 5개) */
    private static final int MAX_CANDIDATES = 5;

    /** 인사/잡담으로 간주하는 키워드 (chat tool로 분기) */
    private static final List<String> SMALL_TALK = List.of(
            "안녕", "하이", "hello", "hi", "반가", "고마", "감사", "thanks", "잘 지내", "뭐해");

    /** 의미 없는 발화로 간주하는 최소 길이(공백 제거 후) */
    private static final int MIN_MEANINGFUL_LENGTH = 2;

    @Override
    public IntentResult resolve(IntentContext context) {
        String utterance = context.utterance() == null ? "" : context.utterance().trim();
        String normalized = utterance.toLowerCase(Locale.ROOT);

        // 서비스 미지정: 레시피 정보가 없으므로 select_service 또는 chat만 가능
        if (!context.hasService()) {
            if (isSmallTalk(normalized)) {
                return IntentResult.chat(smallTalkReply());
            }
            List<ServiceOption> suggested = suggestServices(normalized, context.services());
            log.debug("Resolved select_service: suggestedCount={}", suggested.size());
            return IntentResult.selectService(suggested);
        }

        // 서비스 지정: referenceId 우선 매칭
        IntentResult byReference = matchByReference(context);
        if (byReference != null) {
            return byReference;
        }

        if (isSmallTalk(normalized)) {
            return IntentResult.chat(smallTalkReply());
        }

        // 의미 토큰이 거의 없는 발화 → 재질문
        if (normalized.replaceAll("\\s+", "").length() < MIN_MEANINGFUL_LENGTH) {
            return IntentResult.clarify("어떤 작업을 진행할까요? 예: \"회원가입 테스트 계정 만들어줘\"");
        }

        // 레시피 이름/태그 매칭
        List<RecipeCandidate> matches = matchRecipes(normalized, context.recipes());
        if (matches.isEmpty()) {
            log.debug("Resolved no_match: no recipe matched utterance");
            return IntentResult.noMatch();
        }
        if (matches.size() == 1) {
            RecipeCandidate only = matches.get(0);
            log.debug("Resolved execute_recipe: recipeId={}", only.id());
            return IntentResult.executeRecipe(only.id());
        }
        List<RecipeCandidate> limited = matches.size() > MAX_CANDIDATES
                ? matches.subList(0, MAX_CANDIDATES)
                : matches;
        log.debug("Resolved show_candidates: candidateCount={}", limited.size());
        return IntentResult.showCandidates(List.copyOf(limited));
    }

    /** referenceId가 레시피 목록에 있으면 그 레시피를 우선 실행 (referenceId는 "recipe_123" 또는 "123") */
    private IntentResult matchByReference(IntentContext context) {
        String referenceId = context.referenceId();
        if (referenceId == null || referenceId.isBlank()) {
            return null;
        }
        Long refRecipeId = parseRecipeId(referenceId);
        if (refRecipeId == null) {
            return null;
        }
        boolean present = context.recipes().stream().anyMatch(r -> refRecipeId.equals(r.id()));
        if (present) {
            log.debug("Resolved execute_recipe by referenceId: recipeId={}", refRecipeId);
            return IntentResult.executeRecipe(refRecipeId);
        }
        return null;
    }

    /** "recipe_123" / "123" → 123L. 형식이 아니면 null */
    private Long parseRecipeId(String referenceId) {
        String digits = referenceId.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 발화 토큰이 레시피 이름 또는 태그에 부분 포함되면 매칭으로 본다 */
    private List<RecipeCandidate> matchRecipes(String normalizedUtterance, List<RecipeCandidate> recipes) {
        List<RecipeCandidate> matched = new ArrayList<>();
        for (RecipeCandidate recipe : recipes) {
            if (matchesRecipe(normalizedUtterance, recipe)) {
                matched.add(recipe);
            }
        }
        return matched;
    }

    private boolean matchesRecipe(String normalizedUtterance, RecipeCandidate recipe) {
        String name = recipe.name() == null ? "" : recipe.name().toLowerCase(Locale.ROOT);
        // 이름 전체 또는 이름의 각 단어가 발화에 포함되면 매칭
        if (!name.isBlank() && (normalizedUtterance.contains(name) || anyWordContained(normalizedUtterance, name))) {
            return true;
        }
        if (recipe.tags() != null) {
            for (String tag : recipe.tags()) {
                if (tag != null && !tag.isBlank()
                        && normalizedUtterance.contains(tag.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 이름을 공백으로 쪼갠 각 단어가 발화에 포함되는지 (2글자 이상 단어만 검사) */
    private boolean anyWordContained(String normalizedUtterance, String name) {
        for (String word : name.split("\\s+")) {
            if (word.length() >= MIN_MEANINGFUL_LENGTH && normalizedUtterance.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /** 서비스명이 발화에 부분 포함되면 추천. 없으면 빈 리스트(유추 불가) */
    private List<ServiceOption> suggestServices(String normalizedUtterance, List<ServiceOption> services) {
        List<ServiceOption> suggested = new ArrayList<>();
        for (ServiceOption service : services) {
            String name = service.name() == null ? "" : service.name().toLowerCase(Locale.ROOT);
            if (!name.isBlank() && normalizedUtterance.contains(name)) {
                suggested.add(service);
            }
        }
        // 추천 최대 3개 (intent-classification.md: select_service suggestedServices 최대 3개)
        return suggested.size() > 3 ? List.copyOf(suggested.subList(0, 3)) : List.copyOf(suggested);
    }

    private boolean isSmallTalk(String normalizedUtterance) {
        if (normalizedUtterance.isBlank()) {
            return false;
        }
        return SMALL_TALK.stream().anyMatch(normalizedUtterance::contains);
    }

    private String smallTalkReply() {
        return "안녕하세요! API 워크플로우 실행을 도와드립니다. 어떤 작업을 진행할까요?";
    }
}
