plugins {
    `java-library`
    `maven-publish`
}

group = "com.testforge"
version = "0.0.1-SNAPSHOT"

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
