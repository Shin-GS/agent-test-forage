package com.testforge.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 * BaseEntity의 createdAt/updatedAt이 자동으로 채워지도록 한다.
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
