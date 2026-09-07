package com.testforge.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TestForgePropertiesTest {

    @Test
    void bindsServiceAndConfluence() {
        TestForgeProperties props = new TestForgeProperties();
        props.setServerUrl("https://ai-test-forge.example.com");
        props.getService().setDescription("채용 서비스");
        props.getService().setCapabilities(List.of("회원가입", "공고등록"));
        props.getConfluence().setSpaceKey("RECRUIT");

        assertEquals("https://ai-test-forge.example.com", props.getServerUrl());
        assertEquals("채용 서비스", props.getService().getDescription());
        assertEquals(2, props.getService().getCapabilities().size());
        assertEquals("RECRUIT", props.getConfluence().getSpaceKey());
    }
}
