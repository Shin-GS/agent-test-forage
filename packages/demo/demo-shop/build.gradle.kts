plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.testforge.demo"
version = "0.0.1-SNAPSHOT"

java {
    // 메인 서버(packages/server)와 동일한 Java 25로 통일
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // OpenAPI 스펙 노출 (/v3/api-docs) — 라이브러리가 여기서 스펙을 수집한다
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    // AI Test Forge 클라이언트 라이브러리 (composite build로 로컬 참조)
    implementation("com.testforge:testforge-client-java21")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
