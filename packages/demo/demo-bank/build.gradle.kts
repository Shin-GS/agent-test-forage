plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.testforge.demo"
version = "0.0.1-SNAPSHOT"

// 프로젝트 경로에 비ASCII 문자(Windows)가 있으면 클래스 파일 쓰기가 손상된다.
// 빌드 출력을 ASCII 경로로 우회한다. -PasciiBuildDir 로 opt-in (일반/CI 체크아웃은 영향 없음).
if (project.hasProperty("asciiBuildDir")) {
    val base = project.property("asciiBuildDir").toString()
    layout.buildDirectory.set(file("$base/demo-bank"))
}

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

// 소스 인코딩 UTF-8 명시. 명시하지 않으면 컴파일러가 플랫폼 기본 인코딩(Windows=MS949)으로
// 읽어 한글 주석이 깨지고, 드물게 주석 종료가 유실돼 컴파일이 실패할 수 있다.
// withType 등록만으로 특정 태스크에 안 먹는 경우가 있어, compileJava/compileTestJava 에
// 직접 지정하고 컴파일러 인자로도 -encoding 을 명시한다.
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-encoding", "UTF-8"))
}

tasks.withType<Test> {
    useJUnitPlatform()
}
