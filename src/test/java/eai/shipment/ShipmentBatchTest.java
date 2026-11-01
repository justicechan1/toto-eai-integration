package eai.shipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eai.db.DbInfo;
import eai.log.ProcessLog;
import eai.order.Order;
import eai.order.OrderRepository;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ShipmentBatch 테스트. 메모리 DB(H2)에서 배송 등록, Y 변경, 중복 방지, 부분 실패 복구를 확인한다. */
class ShipmentBatchTest {

    @TempDir
    Path tempDir;

    private DbInfo orderDb;
    private DbInfo shipmentDb;
    private OrderRepository orders;
    private ShipmentBatch batch;
    private ProcessLog log;

    @BeforeEach
    void createTables() throws SQLException {  // 제공 DB와 같은 PK, CHECK 제약 (SHIPMENT_TB.ADDRESS는 실패 테스트용으로 짧게)
        String url = "jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        orderDb = new DbInfo(url, "sa", "", "ORDER_TB");
        shipmentDb = new DbInfo(url, "sa", "", "SHIPMENT_TB");
        try (Connection conn = orderDb.connect(); Statement st = conn.createStatement()) {
            st.execute("""
                    CREATE TABLE ORDER_TB (
                        ORDER_ID VARCHAR(100) NOT NULL, APPLICANT_KEY VARCHAR(100) NOT NULL,
                        USER_ID VARCHAR(100), ITEM_ID VARCHAR(100), NAME VARCHAR(100), ADDRESS VARCHAR(100),
                        ITEM_NAME VARCHAR(100), PRICE VARCHAR(100), STATUS VARCHAR(100),
                        CREATE_TIME TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (ORDER_ID, APPLICANT_KEY), CHECK (STATUS IN ('N', 'Y')))""");
            st.execute("""
                    CREATE TABLE SHIPMENT_TB (
                        SHIPMENT_ID VARCHAR(100) NOT NULL, APPLICANT_KEY VARCHAR(100) NOT NULL,
                        ORDER_ID VARCHAR(100), ITEM_ID VARCHAR(100), ADDRESS VARCHAR(20),
                        CREATE_DATE TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        PRIMARY KEY (SHIPMENT_ID, APPLICANT_KEY),
                        CHECK (REGEXP_LIKE(SHIPMENT_ID, '^[A-Z][0-9]{3}$')))""");
        }
        orders = new OrderRepository(orderDb, "KEY1");
        log = new ProcessLog(tempDir.resolve("process.log"));
        batch = new ShipmentBatch(orders, new ShipmentRepository(shipmentDb, "KEY1"), log);
    }

    private static Order order(String userId, String itemId, String address) {
        return new Order(userId, itemId, "김철수", address, "청바지", "33000");
    }

    private List<String> rows(String sql) throws SQLException {  // 조회 결과를 문자열 목록으로
        try (Connection conn = orderDb.connect(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String> result = new ArrayList<>();
            while (rs.next()) {
                result.add(rs.getString(1));
            }
            return result;
        }
    }

    @Test
    void shipsPendingOrdersAndMarksY() throws SQLException {  // N 주문을 등록하고 Y로 변경
        orders.saveAll(List.of(order("USER01", "ITEM01", "서울 강남구"), order("USER02", "ITEM02", "서울 서초구")));

        ShipmentBatch.Result result = batch.runOnce();

        assertEquals(Map.of("A001", "A001", "A002", "A002"), result.shipped());
        assertEquals(List.of("A001|A001|ITEM01|서울 강남구", "A002|A002|ITEM02|서울 서초구"),
                rows("SELECT SHIPMENT_ID || '|' || ORDER_ID || '|' || ITEM_ID || '|' || ADDRESS FROM SHIPMENT_TB ORDER BY SHIPMENT_ID"));
        assertEquals(List.of("Y", "Y"), rows("SELECT STATUS FROM ORDER_TB ORDER BY ORDER_ID"));
    }

    @Test
    void doesNotShipTwice() throws SQLException {  // 다시 실행해도 Y 주문은 처리하지 않음, 새 주문만 이어서 채번
        orders.saveAll(List.of(order("USER01", "ITEM01", "서울")));
        batch.runOnce();
        orders.saveAll(List.of(order("USER02", "ITEM02", "부산")));

        assertEquals(Map.of("A002", "A002"), batch.runOnce().shipped());
        assertEquals(List.of("2"), rows("SELECT COUNT(*) FROM SHIPMENT_TB"));
        assertTrue(batch.runOnce().shipped().isEmpty());
    }

    @Test
    void recoversWhenShipmentExistsButOrderStillN() throws Exception {  // 지난 실행에서 등록 후 Y 변경만 실패한 경우
        orders.saveAll(List.of(order("USER01", "ITEM01", "서울")));
        try (Connection conn = shipmentDb.connect(); Statement st = conn.createStatement()) {
            st.execute("INSERT INTO SHIPMENT_TB (SHIPMENT_ID, APPLICANT_KEY, ORDER_ID, ITEM_ID, ADDRESS) VALUES ('A001', 'KEY1', 'A001', 'ITEM01', '서울')");
        }

        assertEquals(Map.of("A001", "A001"), batch.runOnce().shipped());  // 기존 배송을 찾아 Y만 변경
        assertEquals(List.of("1"), rows("SELECT COUNT(*) FROM SHIPMENT_TB"));
        assertEquals(List.of("Y"), rows("SELECT STATUS FROM ORDER_TB"));
        assertTrue(log.find("A001").get(0).endsWith("SHIPMENT_ID=A001, STATUS=Y (already shipped, status only)"));
    }

    @Test
    void continuesWhenOneOrderFails() throws Exception {  // 한 건 실패해도 나머지는 처리, 실패 건은 N 유지
        orders.saveAll(List.of(order("USER01", "ITEM01", "서울"), order("USER02", "ITEM02", "주소".repeat(20)), order("USER03", "ITEM03", "부산")));

        ShipmentBatch.Result result = batch.runOnce();

        assertEquals(Map.of("A001", "A001", "A003", "A002"), result.shipped());
        assertEquals(List.of("A002"), List.copyOf(result.failed().keySet()));
        assertEquals(List.of("Y", "N", "Y"), rows("SELECT STATUS FROM ORDER_TB ORDER BY ORDER_ID"));
        assertTrue(log.find("A002").get(0).contains("| A002 | SHIP | FAIL | "));  // 실패 원인이 이력에 남음
    }

    @Test
    void ignoresOtherApplicants() throws SQLException {  // 다른 지원자 주문은 처리하지 않음
        new OrderRepository(orderDb, "OTHER").saveAll(List.of(order("USER09", "ITEM09", "서울")));

        assertTrue(batch.runOnce().shipped().isEmpty());
        assertEquals(List.of("N"), rows("SELECT STATUS FROM ORDER_TB"));
    }
}
