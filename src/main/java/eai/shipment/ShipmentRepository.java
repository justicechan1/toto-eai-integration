package eai.shipment;

import eai.db.DbInfo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class ShipmentRepository {

    private final DbInfo db;
    private final String applicantKey;

    // 생성자, 운송 DB와 지원자 키 지정
    public ShipmentRepository(DbInfo db, String applicantKey) {
        this.db = db;
        this.applicantKey = applicantKey;
    }

    // 이미 등록된 배송 (ORDER_ID -> SHIPMENT_ID)
    public Map<String, String> shipmentIdsByOrder(Connection conn) throws SQLException {
        Map<String, String> result = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT ORDER_ID, SHIPMENT_ID FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        }
        return result;
    }

    // 내 배송 중 가장 큰 SHIPMENT_ID (없으면 null)
    public String maxShipmentId(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT MAX(SHIPMENT_ID) FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
            ps.setString(1, applicantKey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    // 배송 1건 등록
    public void insert(Connection conn, String shipmentId, String orderId, String itemId, String address) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + db.table() + " (SHIPMENT_ID, APPLICANT_KEY, ORDER_ID, ITEM_ID, ADDRESS) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, shipmentId);
            ps.setString(2, applicantKey);
            ps.setString(3, orderId);
            ps.setString(4, itemId);
            ps.setString(5, address);
            ps.executeUpdate();
        }
    }

    // 운송 DB 연결
    public Connection connect() throws SQLException {
        return db.connect();
    }
}
