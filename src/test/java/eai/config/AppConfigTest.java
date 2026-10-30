package eai.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Properties;
import org.junit.jupiter.api.Test;

/** AppConfig 테스트. 빈 값 거부와 전화번호 형식 검사를 확인한다. */
class AppConfigTest {

    private static AppConfig config(String key, String value) {  // 파일 없이 키 하나짜리 설정 생성
        Properties props = new Properties();
        props.setProperty(key, value);
        return new AppConfig(props);
    }

    @Test
    void requireRejectsBlankValue() {  // 공백만 있는 값은 실패
        assertThrows(IllegalStateException.class, () -> config("applicant.name", "  ").require("applicant.name"));
    }

    @Test
    void phoneAcceptsHyphenFormat() {  // 010-1234-5678 형식은 통과
        assertEquals("010-1234-5678", config("applicant.phone", "010-1234-5678").phone());
    }

    @Test
    void phoneRejectsNumberWithoutHyphens() {  // 하이픈 없으면 실패
        assertThrows(IllegalStateException.class, () -> config("applicant.phone", "01012345678").phone());
    }
}
