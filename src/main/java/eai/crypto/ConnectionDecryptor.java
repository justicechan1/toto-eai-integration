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

/**
 * 과제 API가 내려준 접속 정보를 복호화한다.
 * 키: 전화번호 문자열(UTF-8)의 SHA-1 해시 앞 16바이트
 * 암호문: Base64 디코드 후 AES/ECB/PKCS5Padding
 */
public class ConnectionDecryptor {

    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";  // 알고리즘/블록 방식/패딩 규칙

    private final SecretKeySpec key;

    public ConnectionDecryptor(String phone) {
        this.key = new SecretKeySpec(deriveKey(phone), "AES");  // 전화번호로 키 생성(JDK 클래스)
    }

    static byte[] deriveKey(String phone) {  // 테스트에서 검증하도록 static
        try {
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(phone.getBytes(StandardCharsets.UTF_8));  // 전화번호 -> SHA-1 해시(20바이트)
            return Arrays.copyOf(sha1, 16);  // AES-128 키 길이(16바이트)만큼 앞에서 자름
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-1 not available", e);
        }
    }

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

    /** ORDER_TB_CONN 같은 접속 정보 객체는 필드마다 따로 암호화되어 있어 필드 단위로 복호화한다. */
    public Map<String, String> decryptFields(JsonNode encrypted) {
        Map<String, String> result = new LinkedHashMap<>();  // 응답의 필드 순서 유지
        encrypted.fields().forEachRemaining(field -> result.put(field.getKey(), decrypt(field.getValue().asText())));
        return result;
    }
}
