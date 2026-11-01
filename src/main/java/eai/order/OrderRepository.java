package eai.order;

import eai.db.DbInfo;
import eai.db.IdGenerator;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderRepository {

    private final DbInfo db;
    private final String applicantKey;

    // 저장 결과 (저장된 주문 ID -> 주문, 이미 저장되어 건너뛴 주문 목록)
    public record SaveResult(Map<String, Order> saved, List<Duplicate> duplicates) {

        // 저장된 주문 ID 목록
        public List<String> savedIds() {
            return List.copyOf(saved.keySet());
        }
    }

    // 건너뛴 주문 (이미 저장된 주문 ID, 받은 주문)
    public record Duplicate(String orderId, Order order) {
    }

    // 생성자, 주문 DB와 지원자 키 지정
    public OrderRepository(DbInfo db, String applicantKey) {
        this.db = db;
        this.applicantKey = applicantKey;
    }

    // 주문 목록 저장 (중복 제외, 한 요청은 하나의 트랜잭션)
    // ponytail: 프로세스 내 synchronized로 ID 채번 충돌 방지, 여러 프로세스가 동시에 저장하면 DB 시퀀스나 잠금 필요
    public synchronized SaveResult saveAll(List<Order> orders) throws SQLException {
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);  // 전부 저장 또는 전부 취소
            try {
                Map<String, String> savedKeys = existingKeys(conn);  // (USER_ID, ITEM_ID) -> ORDER_ID
                String lastId = maxOrderId(conn);
                Map<String, Order> saved = new LinkedHashMap<>();
                List<Duplicate> duplicates = new ArrayList<>();

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + db.table() + " (ORDER_ID, APPLICANT_KEY, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'N')")) {  // 신규 주문은 미배송(N)
                    for (Order order : orders) {
                        String key = key(order.userId(), order.itemId());
                        if (savedKeys.containsKey(key)) {  // 이미 저장된 주문
                            duplicates.add(new Duplicate(savedKeys.get(key), order));
                            continue;
                        }
                        lastId = IdGenerator.next(lastId);
                        savedKeys.put(key, lastId);  // 같은 요청 안의 중복도 막음
                        ps.setString(1, lastId);
                        ps.setString(2, applicantKey);
                        ps.setString(3, order.userId());
                        ps.setString(4, order.itemId());
                        ps.setString(5, order.name());
                        ps.setString(6, order.address());
                        ps.setString(7, order.itemName());
                        ps.setString(8, order.price());
                        ps.addBatch();
                        saved.put(lastId, order);
                    }
                    ps.executeBatch();  // 한 번에 전송
                }
                conn.commit();
                return new SaveResult(saved, duplicates);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();  // 일부만 저장되지 않도록 취소
                throw e;
            }
        }
    }

    // 내 키로 저장된 주문 전체 (ORDER_ID 순)
    public List<SavedOrder> findAll() throws SQLException {
        return find("");
    }

    // 배송 등록 전 주문 (STATUS='N', ORDER_ID 순)
    public List<SavedOrder> findPending() throws SQLException {
        return find(" AND STATUS = 'N'");
    }

    // 배송 등록 완료 표시 (N -> Y), 바뀐 행 수 반환
    public int markShipped(String orderId) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE " + db.table() + " SET STATUS = 'Y' WHERE ORDER_ID = ? AND APPLICANT_KEY = ? AND STATUS = 'N'")) {
            ps.setString(1, orderId);
            ps.setString(2, applicantKey);
            return ps.executeUpdate();
        }
    }

    // 조건에 맞는 내 주문 조회
    private List<SavedOrder> find(String condition) throws SQLException {
        List<SavedOrder> result = new ArrayList<>();
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT ORDER_ID, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, CREATE_TIME FROM " + db.table()
                             + " WHERE APPLICANT_KEY = ?" + condition + " ORDER BY ORDER_ID")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = new Order(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
                    result.add(new SavedOrder(rs.getString(1), order, rs.getTimestamp(8).toLocalDateTime()));
                }
            }
        }
        return result;
    }

    // 이미 저장된 주문 ((USER_ID, ITEM_ID) -> ORDER_ID)
    private Map<String, String> existingKeys(Connection conn) throws SQLException {
        Map<String, String> keys = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT USER_ID, ITEM_ID, ORDER_ID FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.put(key(rs.getString(1), rs.getString(2)), rs.getString(3));
                }
            }
        }
        return keys;
    }

    // 내 주문 중 가장 큰 ORDER_ID (없으면 null)
    private String maxOrderId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT MAX(ORDER_ID) FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    // 중복 판단 키 (같은 주문자의 같은 상품)
    private static String key(String userId, String itemId) {
        return userId + "|" + itemId;
    }
}
