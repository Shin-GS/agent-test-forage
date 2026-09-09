package com.testforge.demo.blog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 태그 API. 인증 없이 조회만 노출한다. 태그는 게시글 작성 시 자동 등록되며
 * {@link BlogStore}에서 인메모리로 관리한다.
 */
@RestController
@RequestMapping("/tags")
@Tag(name = "태그", description = "태그 조회 API")
public class TagController {

    private final BlogStore store;

    public TagController(BlogStore store) {
        this.store = store;
    }

    @Operation(summary = "태그 목록 조회", description = "등록된 태그 목록을 조회한다.")
    @GetMapping
    public List<BlogStore.Tag> list() {
        return store.listTags();
    }
}
