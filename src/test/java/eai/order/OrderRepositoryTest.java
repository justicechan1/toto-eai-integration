package eai.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eai.db.DbInfo;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** OrderRepository 테스트. 제공 DB와 같은 제약을 가진 메모리 DB(H2)에서 저장 동작을 확인한다. */
class OrderRepositoryTest {

    private DbInfo db;

    @BeforeEach
    void createTable() throws SQLException {  // 테스트마다 새 메모리 DB, 제공 DB와 같은 PK와 CHECK 제약
        db = new DbInfo("jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "", "ORDER_TB");
        try (Connection conn = db.connect(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE ORDER_TB (
                        ORDER_ID VARCHAR(100) NOT NULL, APPLICANT_KEY VARCHAR(100) NOT NULL,
                        USER_ID VARCHAR(100), ITEM_ID VARCHAR(100), NAME VARCHAR(100), ADDRESS VARCHAR(100),
                        ITEM_NAME VARCHAR(100), PRICE VARCHAR(100), STATUS VARCHAR(100),
                        CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT PK_ORDER_TB PRIMARY KEY (ORDER_ID, APPLICANT_KEY),
                        CONSTRAINT CHK_ORDER_TB_ORDER_ID CHECK (REGEXP_LIKE(ORDER_ID, '^[A-Z][0-9]{3}$')),
                        CONSTRAINT CHK_ORDER_TB_STATUS CHECK (STATUS IN ('N', 'Y')))""");
        }
    }

    private static Order order(String userId, String itemId) {
        return new Order(userId, itemId, "김철수", "서울특별시 강남구", "청바지", "33000");
    }

    private List<String> rows(String sql) throws SQLException {  // 조회 결과를 문자열 목록으로
        try (Connection conn = db.connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String> result = new java.util.ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(1));
            }
            return result;
        }
    }

    @Test
    void savesWithGeneratedIdsAndStatusN() throws SQLException {  // A001부터 채번, STATUS는 N
        OrderRepository.SaveResult result = new OrderRepository(db, "KEY1").saveAll(List.of(order("USER01", "ITEM01"), order("USER02", "ITEM02")));

        assertEquals(List.of("A001", "A002"), result.savedIds());
        assertEquals(List.of("A001|N|김철수", "A002|N|김철수"),
                rows("SELECT ORDER_ID || '|' || STATUS || '|' || NAME FROM ORDER_TB ORDER BY ORDER_ID"));
    }

    @Test
    void continuesIdAndSkipsAlreadySavedOrder() throws SQLException {  // 재수신 시 기존 주문은 건너뛰고 ID 이어서 채번
        OrderRepository repository = new OrderRepository(db, "KEY1");
        repository.saveAll(List.of(order("USER01", "ITEM01")));

        OrderRepository.SaveResult result = repository.saveAll(List.of(order("USER01", "ITEM01"), order("USER02", "ITEM02")));

        assertEquals(List.of("A002"), result.savedIds());
        assertEquals(List.of(order("USER01", "ITEM01")), result.duplicates());
    }

    @Test
    void skipsDuplicateInSameRequest() throws SQLException {  // 한 요청 안의 같은 주문도 한 번만 저장
        OrderRepository.SaveResult result = new OrderRepository(db, "KEY1").saveAll(List.of(order("USER01", "ITEM01"), order("USER01", "ITEM01")));

        assertEquals(List.of("A001"), result.savedIds());
        assertEquals(1, result.duplicates().size());
    }

    @Test
    void separatesApplicants() throws SQLException {  // 다른 지원자 데이터는 중복·채번에 영향 없음
        new OrderRepository(db, "OTHER").saveAll(List.of(order("USER01", "ITEM01")));

        assertEquals(List.of("A001"), new OrderRepository(db, "KEY1").saveAll(List.of(order("USER01", "ITEM01"))).savedIds());
    }

    @Test
    void rollsBackWholeRequestOnFailure() throws SQLException {  // 한 건이라도 실패하면 전부 취소
        Order tooLong = new Order("USER02", "ITEM02", "김".repeat(101), "주소", "상품", "1000");  // 컬럼 길이 초과

        assertThrows(SQLException.class, () -> new OrderRepository(db, "KEY1").saveAll(List.of(order("USER01", "ITEM01"), tooLong)));
        assertEquals(List.of("0"), rows("SELECT COUNT(*) FROM ORDER_TB"));
    }
}
