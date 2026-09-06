package com.testforge.repository.recipe;

import com.testforge.entity.recipe.Recipe;
import com.testforge.entity.recipe.enums.Visibility;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    /** ID로 미삭제 레시피 조회 (상세/수정/삭제 시 삭제 레시피 배제용) */
    Optional<Recipe> findByIdAndDeletedAtIsNull(Long id);

    /** 특정 서비스(스펙)의 미삭제 레시피 목록 (스펙 단위 전체 — 소유 격리 없음, 실행 스냅샷 등 내부용) */
    List<Recipe> findByApiSpecIdAndDeletedAtIsNull(Long apiSpecId);

    /**
     * 특정 서비스(스펙)의 미삭제 레시피 중 <b>요청자에게 보이는 것만</b> 로드 (AI 후보 로드용).
     * 소유 격리(auth.md): "COMMON 전체 + 요청자 본인의 PRIVATE"로 범위를 강제해, 남의 PRIVATE가
     * AI 후보로 노출되지 않도록 한다. {@link RecipeAccessPolicy#canView}와 동일한 규칙을 쿼리로 표현한다.
     *
     * @param apiSpecId 대상 서비스(스펙) ID
     * @param actorId   요청자 ID (PRIVATE 소유 격리용)
     */
    @Query("""
            SELECT r FROM Recipe r
            WHERE r.deletedAt IS NULL
              AND r.apiSpecId = :apiSpecId
              AND (r.visibility = com.testforge.entity.recipe.enums.Visibility.COMMON
                   OR r.ownerUserId = :actorId)
            """)
    List<Recipe> findVisibleByApiSpecId(@Param("apiSpecId") Long apiSpecId,
                                        @Param("actorId") Long actorId);

    /**
     * 목록 조회 (미삭제만) — 소유 격리 + 다중 필터(서비스/공개범위) + keyword + 동적 정렬.
     *
     * <p><b>소유 격리(auth.md 목록 규칙)</b>: 반환 범위를 "COMMON 전체 + 요청자 본인의 PRIVATE"로 강제한다.
     * 남의 PRIVATE는 어떤 필터로도 노출되지 않는다.
     *
     * <p><b>다중 필터</b>: apiSpecId/visibility는 리스트로 받아 IN으로 매칭한다. 각 필터는 {@code null}이면
     * (서비스에서 빈 리스트를 null로 정규화) 해당 축을 무시한다. apiSpecId는 메타 대상 서비스
     * (Recipe.apiSpecId) 기준으로 거른다(스텝 내부 호출 서비스로는 필터하지 않음).
     * <b>태그 다중 필터는 TAGS가 JSON 문자열이라 서비스 레이어에서 LIKE(OR) 후처리</b>한다.
     *
     * <p><b>keyword</b>: name/description에 대소문자 무시 LIKE.
     *
     * <p><b>정렬</b>: {@code Sort}로 주입한다(name/usageCount/updatedAt). recent(lastUsedAt) 정렬의
     * null 후행은 이 메서드로 표현하기 어려워, 서비스가 {@link #searchRecent}를 별도로 호출한다.
     *
     * @param apiSpecIds   대상 서비스 ID 목록 (null이면 무시)
     * @param visibilities 공개범위 목록 (null이면 무시)
     * @param keyword      name/description LIKE 키워드 (null이면 무시)
     * @param actorId      요청자 ID (PRIVATE 소유 격리용)
     * @param sort         정렬 (name/usageCount/updatedAt)
     */
    @Query("""
            SELECT r FROM Recipe r
            WHERE r.deletedAt IS NULL
              AND (r.visibility = com.testforge.entity.recipe.enums.Visibility.COMMON
                   OR r.ownerUserId = :actorId)
              AND (:apiSpecIds IS NULL OR r.apiSpecId IN :apiSpecIds)
              AND (:visibilities IS NULL OR r.visibility IN :visibilities)
              AND (:keyword IS NULL
                   OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    List<Recipe> search(@Param("apiSpecIds") List<Long> apiSpecIds,
                        @Param("visibilities") List<Visibility> visibilities,
                        @Param("keyword") String keyword,
                        @Param("actorId") Long actorId,
                        Sort sort);

    /**
     * recent 정렬 전용 조회. 필터/소유 격리는 {@link #search}와 동일하되, 정렬을
     * {@code lastUsedAt} 기준 + <b>null 항상 후행</b>으로 강제한다(auth.md/editor 정렬 규칙).
     * DB별 NULLS LAST 문법 차이를 피하기 위해 "null 여부 플래그(0/1)"를 1차 정렬 키로 둔다.
     *
     * @param descending true면 lastUsedAt DESC, false면 ASC (null은 방향과 무관하게 항상 뒤)
     */
    @Query("""
            SELECT r FROM Recipe r
            WHERE r.deletedAt IS NULL
              AND (r.visibility = com.testforge.entity.recipe.enums.Visibility.COMMON
                   OR r.ownerUserId = :actorId)
              AND (:apiSpecIds IS NULL OR r.apiSpecId IN :apiSpecIds)
              AND (:visibilities IS NULL OR r.visibility IN :visibilities)
              AND (:keyword IS NULL
                   OR LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY CASE WHEN r.lastUsedAt IS NULL THEN 1 ELSE 0 END ASC,
                     CASE WHEN :descending = TRUE THEN r.lastUsedAt END DESC,
                     CASE WHEN :descending = FALSE THEN r.lastUsedAt END ASC,
                     r.id DESC
            """)
    List<Recipe> searchRecent(@Param("apiSpecIds") List<Long> apiSpecIds,
                              @Param("visibilities") List<Visibility> visibilities,
                              @Param("keyword") String keyword,
                              @Param("actorId") Long actorId,
                              @Param("descending") boolean descending);
}
