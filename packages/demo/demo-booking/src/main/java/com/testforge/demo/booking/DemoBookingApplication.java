package com.testforge.demo.booking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo-booking 데모 서버 (예약 관리). demo-blog 와 동일한 구조의 인메모리 데모 서버로,
 * 좌석/예약 API 를 OpenAPI 스펙으로 노출한다. AI Test Forge 클라이언트 라이브러리가
 * 기동 시 이 스펙을 에이전트 서버에 자동 등록한다(실제 DB 없음).
 */
@SpringBootApplication
public class DemoBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoBookingApplication.class, args);
    }
}
