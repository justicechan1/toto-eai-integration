package eai.order;

import eai.db.DbInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class OrderRepository {

    private final DbInfo db;
    private final String applicantKey;

    // 저장 결과 (저장된 주문 ID 목록, 이미 저장되어 건너뛴 주문 목록)
    public record SaveResult(List<String> savedIds, List<Order> duplicates) {
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
                Set<String> savedKeys = existingKeys(conn);
                String lastId = maxOrderId(conn);
                List<String> savedIds = new ArrayList<>();
                List<Order> duplicates = new ArrayList<>();

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO " + db.table() + " (ORDER_ID, APPLICANT_KEY, USER_ID, ITEM_ID, NAME, ADDRESS, ITEM_NAME, PRICE, STATUS)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'N')")) {  // 신규 주문은 미배송(N)
                    for (Order order : orders) {
                        if (!savedKeys.add(key(order.userId(), order.itemId()))) {  // 이미 저장된 주문
                            duplicates.add(order);
                            continue;
                        }
                        lastId = OrderIdGenerator.next(lastId);
                        ps.setString(1, lastId);
                        ps.setString(2, applicantKey);
                        ps.setString(3, order.userId());
                        ps.setString(4, order.itemId());
                        ps.setString(5, order.name());
                        ps.setString(6, order.address());
                        ps.setString(7, order.itemName());
                        ps.setString(8, order.price());
                        ps.addBatch();
                        savedIds.add(lastId);
                    }
                    ps.executeBatch();  // 한 번에 전송
                }
                conn.commit();
                return new SaveResult(savedIds, duplicates);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();  // 일부만 저장되지 않도록 취소
                throw e;
            }
        }
    }

    // 이미 저장된 주문의 (USER_ID, ITEM_ID) 목록
    private Set<String> existingKeys(Connection conn) throws SQLException {
        Set<String> keys = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT USER_ID, ITEM_ID FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    keys.add(key(rs.getString(1), rs.getString(2)));
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
