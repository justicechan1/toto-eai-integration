package eai.crypto;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class ConnectionDecryptor {

    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";  // 알고리즘/블록 방식/패딩 규칙

    private final SecretKeySpec key;

    // 생성자, 전화번호로 AES 키를 한 번만 생성
    public ConnectionDecryptor(String phone) {
        this.key = new SecretKeySpec(deriveKey(phone), "AES");  // 전화번호로 키 생성(JDK 클래스)
    }

    // 키 생성: SHA-1(전화번호) 앞 16바이트 (테스트에서 검증하도록 static)
    static byte[] deriveKey(String phone) {
        try {
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(phone.getBytes(StandardCharsets.UTF_8));  // 전화번호 -> SHA-1 해시(20바이트)
            return Arrays.copyOf(sha1, 16);  // AES-128 키 길이(16바이트)만큼 앞에서 자름
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

    // 암호문 1개 복호화
    public String decrypt(String base64) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key);  // 복호화 모드로 키 설정
            byte[] plain = cipher.doFinal(Base64.getDecoder().decode(base64));  // Base64 -> 암호문 바이트 -> 복호화(패딩 제거 포함)
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {  // 키가 틀린 경우 (전화번호 불일치)
            throw new IllegalStateException("Decryption failed. Check applicant.phone matches the number sent to the API.", e);
        }
    }

    // 접속 정보 객체의 모든 필드 복호화 (필드마다 따로 암호화되어 있음)
    public Map<String, String> decryptFields(JsonNode encrypted) {
        Map<String, String> result = new LinkedHashMap<>();  // 응답의 필드 순서 유지
        encrypted.fields().forEachRemaining(field -> result.put(field.getKey(), decrypt(field.getValue().asText())));
        return result;
    }
}
