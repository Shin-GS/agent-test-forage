plugins {
    `java-library`
    `maven-publish`
}

group = "com.testforge"
version = "0.0.1-SNAPSHOT"

// 프로젝트 경로에 비ASCII 문자(Windows)가 있으면 클래스 파일 쓰기가 손상된다
// (error while writing ...$Profile.class.class). 빌드 출력을 ASCII 경로로 우회한다.
// -PasciiBuildDir 로 opt-in (일반/CI 체크아웃은 영향 없음). composite build 에서
// 데모/서버가 이 프로퍼티를 넘기면 이 프로젝트에도 전파되어 함께 우회된다.
// 프로젝트별 하위 폴더로 분리해 다른 프로젝트의 출력과 충돌하지 않게 한다.
if (project.hasProperty("asciiBuildDir")) {
    val base = project.property("asciiBuildDir").toString()
    layout.buildDirectory.set(file("$base/library-java-21"))
}

java {
    // Java 21 호환 바이트코드 생성.
    // 로컬에 Java 21이 있으면 toolchain 사용 권장이나,
    // 없을 경우 상위 JDK로 컴파일하되 release=21로 타깃 고정.
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.release = 21
}

repositories {
    mavenCentral()
}

dependencies {
    // 외부 서버(Spring Boot)에 탑재되는 라이브러리.
    // compileOnly로 두어 호스트 앱의 스프링/springdoc 버전을 따르게 한다.
    // (버전은 컴파일 기준일 뿐, 런타임엔 호스트 앱 버전 사용)
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:3.4.0")
    compileOnly("org.springframework:spring-web:6.2.0")
    compileOnly("org.springframework:spring-context:6.2.0")
    // OpenAPI 어노테이션 커스터마이저용 (springdoc). 호스트 앱에 있을 때만 동작.
    compileOnly("org.springdoc:springdoc-openapi-starter-webmvc-api:2.7.0")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
