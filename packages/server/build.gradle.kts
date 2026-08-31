plugins {
    java
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.testforge"
version = "0.0.1-SNAPSHOT"

// The project path contains non-ASCII characters, which corrupt the test
// worker classpath on Windows. Redirect the build output to an ASCII path.
// Opt-in via -PasciiBuildDir so normal (ASCII-path) checkouts are unaffected.
if (project.hasProperty("asciiBuildDir")) {
    layout.buildDirectory.set(file(project.property("asciiBuildDir").toString()))
}

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
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")

    // OpenAPI 3.0/3.1 파싱 (specJson의 paths → endpoint 분해)
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")

    // Spring AI — 실제 AI 기능 구현 시 활성화 (버전은 구현 시점 최신 확인)
    // implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    // implementation("org.springframework.ai:spring-ai-starter-model-openai")

    runtimeOnly("com.mysql:mysql-connector-j")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("com.h2database:h2")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Opt-in workaround for local dev on a non-ASCII project path (Windows):
    // route the test worker temp to an ASCII path to avoid worker jar corruption.
    // Enable together with -PasciiBuildDir; CI on ASCII paths is unaffected.
    if (project.hasProperty("asciiBuildDir")) {
        val asciiTmp = "C:\\gradle-tmp"
        jvmArgs("-Djava.io.tmpdir=$asciiTmp", "-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
        systemProperty("java.io.tmpdir", asciiTmp)
    }
}
