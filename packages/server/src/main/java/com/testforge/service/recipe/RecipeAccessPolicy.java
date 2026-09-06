package com.testforge.service.recipe;

import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.Visibility;
import com.testforge.entity.user.enums.UserRole;
import org.springframework.stereotype.Component;

/**
 * 레시피 접근/변경 권한 규칙의 단일 집중 지점 (auth.md 레시피 권한 매트릭스).
 *
 * <p>규칙 요약:
 * <ul>
 *   <li>조회(canView): COMMON은 전원, PRIVATE은 소유자 본인만</li>
 *   <li>변경(canModify): COMMON은 ADMIN만, PRIVATE은 소유자 본인만 (수정·삭제·복원 공통)</li>
 *   <li>공통 설정(canSetCommon): 공개범위=COMMON 생성/전환은 ADMIN만</li>
 * </ul>
 *
 * <p>404 vs 403 원칙(auth.md)은 호출측(서비스)에서 이 정책의 결과를 근거로 적용한다:
 * <ul>
 *   <li>남의 PRIVATE 접근(조회/수정/삭제/복원/버전) = 404 (존재 은폐)</li>
 *   <li>소프트 삭제(deletedAt != null) = 404</li>
 *   <li>공통을 non-admin이 변경 시도 = 403 (리소스는 보이되 권한 부족)</li>
 * </ul>
 *
 * <p>userId/role은 항상 세션(CurrentUser)에서 도출된 값을 넘겨야 한다(요청 바디 신뢰 금지).
 */
@Component
public class RecipeAccessPolicy {

    /** 조회 가능 여부: COMMON=전원 / PRIVATE=소유자 본인만 */
    public boolean canView(Recipe recipe, Long actorId, UserRole actorRole) {
        if (recipe.getVisibility() == Visibility.COMMON) {
            return true;
        }
        return actorId != null && actorId.equals(recipe.getOwnerUserId());
    }

    /** 변경(수정·삭제·복원) 가능 여부: COMMON=ADMIN만 / PRIVATE=소유자 본인만 */
    public boolean canModify(Recipe recipe, Long actorId, UserRole actorRole) {
        if (recipe.getVisibility() == Visibility.COMMON) {
            return actorRole == UserRole.ADMIN;
        }
        return actorId != null && actorId.equals(recipe.getOwnerUserId());
    }

    /** 공개범위=공통(COMMON) 생성/전환 가능 여부: ADMIN만 */
    public boolean canSetCommon(UserRole actorRole) {
        return actorRole == UserRole.ADMIN;
    }
}
