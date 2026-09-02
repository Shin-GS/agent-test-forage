package com.testforge.demo.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * demo-shop 데모 서버 (이커머스). 1단계: 라이브러리 등록 동작 검증용 최소 구성.
 * 상품 조회 API 하나로 OpenAPI 스펙을 노출하고, AI Test Forge 클라이언트 라이브러리가
 * 기동 시 이 스펙을 에이전트 서버에 자동 등록한다.
 */
@SpringBootApplication
public class DemoShopApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoShopApplication.class, args);
    }
}
