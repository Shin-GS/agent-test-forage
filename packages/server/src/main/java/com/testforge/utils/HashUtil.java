package com.testforge.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * yml 메타 변경 감지 해시 계산용 SHA-256 헬퍼.
 */
public final class HashUtil {

    private HashUtil() {
    }

    /** 입력 문자열의 SHA-256 16진 문자열 반환 (null은 빈 문자열로 처리) */
    public static String sha256(String content) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
