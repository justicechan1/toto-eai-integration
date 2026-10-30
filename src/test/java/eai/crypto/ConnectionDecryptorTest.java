package eai.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/** ConnectionDecryptor 테스트. 키 생성 규칙과 복호화가 과제 가이드대로 동작하는지 확인한다. */
class ConnectionDecryptorTest {

    private static final String PHONE = "010-1234-5678";

    private static String encrypt(String phone, String plain) throws Exception {  // 서버와 같은 규칙으로 테스트용 암호문 생성
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(ConnectionDecryptor.deriveKey(phone), "AES"));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void keyIsFirst16BytesOfSha1OfPhone() {  // 키 = SHA-1 앞 16바이트 (기대값은 Python으로 계산)
        assertEquals("658dd4d695d829188d6b27ed3358a00c",
                HexFormat.of().formatHex(ConnectionDecryptor.deriveKey(PHONE)));
    }

    @Test
    void decryptsValueEncryptedWithSameRule() throws Exception {  // 암호화 -> 복호화 시 원래 값
        String url = "jdbc:oracle:thin:@example.com:1521:ORCL";
        assertEquals(url, new ConnectionDecryptor(PHONE).decrypt(encrypt(PHONE, url)));
    }

    @Test
    void decryptsEveryFieldOfConnectionObject() throws Exception {  // 필드별 복호화
        ObjectNode conn = new ObjectMapper().createObjectNode()
                .put("ID", encrypt(PHONE, "user"))
                .put("TABLE", encrypt(PHONE, "ORDER_TB"));
        assertEquals(Map.of("ID", "user", "TABLE", "ORDER_TB"), new ConnectionDecryptor(PHONE).decryptFields(conn));
    }

    @Test
    void wrongPhoneFailsWithHint() throws Exception {  // 다른 전화번호면 실패
        String cipherText = encrypt(PHONE, "secret");
        assertThrows(IllegalStateException.class, () -> new ConnectionDecryptor("010-0000-0000").decrypt(cipherText));
    }
}
