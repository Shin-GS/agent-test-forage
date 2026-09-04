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
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // 인증/인가 (세션 쿠키 + BCrypt). 세션은 인메모리 HttpSession, JWT/OAuth 미사용.
    implementation("org.springframework.boot:spring-boot-starter-security")

    // OpenAPI 3.0/3.1 파싱 (specJson의 paths → endpoint 분해)
    implementation("io.swagger.parser.v3:swagger-parser:2.1.22")

    // AI는 OpenAI 호환 API를 RestClient(spring-boot-starter-web 포함)로 직접 호출한다.
    // Spring AI는 미채택 (IntentResolver 추상화가 이미 확장 경계라 프로바이더 추상화가 중복).

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
