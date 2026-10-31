package eai.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** DbInfo 테스트. 테이블명 검증과 비밀번호 노출 방지를 확인한다. */
class DbInfoTest {

    private static Map<String, String> fields(String table) {
        return Map.of("URL", "jdbc:oracle:thin:@host:1521:ORCL", "ID", "user", "PASSWORD", "secret", "TABLE", table);
    }

    @Test
    void createsFromDecryptedFields() {  // 복호화된 필드로 생성
        assertEquals(new DbInfo("jdbc:oracle:thin:@host:1521:ORCL", "user", "secret", "ORDER_TB"), DbInfo.from(fields("ORDER_TB")));
    }

    @Test
    void rejectsTableNameWithSql() {  // SQL이 섞인 테이블명은 실패
        assertThrows(IllegalStateException.class, () -> DbInfo.from(fields("ORDER_TB; DROP TABLE X")));
    }

    @Test
    void toStringHidesPassword() {  // 로그에 비밀번호가 찍히지 않음
        assertFalse(DbInfo.from(fields("ORDER_TB")).toString().contains("secret"));
    }
}
