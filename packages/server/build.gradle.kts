plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.testforge"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    // Spring AI가 milestone 단계인 경우 필요 (GA면 제거 가능)
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Spring AI — 실제 AI 기능 구현 시 활성화 (버전은 구현 시점 최신 확인)
    // implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    // implementation("org.springframework.ai:spring-ai-starter-model-openai")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
