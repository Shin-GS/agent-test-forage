package com.testforge.demo.ticket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo-ticket 데모 서버 (이슈 트래커). demo-blog 와 동일한 구조의 인메모리 데모 서버로,
 * 티켓/상태변경/코멘트 API 를 OpenAPI 스펙으로 노출한다. AI Test Forge 클라이언트 라이브러리가
 * 기동 시 이 스펙을 에이전트 서버에 자동 등록한다(실제 DB 없음).
 */
@SpringBootApplication
public class DemoTicketApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoTicketApplication.class, args);
    }
}
