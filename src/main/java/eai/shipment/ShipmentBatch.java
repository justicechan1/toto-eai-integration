package eai.shipment;

import eai.db.IdGenerator;
import eai.log.ProcessLog;
import eai.log.ProcessLog.Stage;
import eai.order.OrderRepository;
import eai.order.SavedOrder;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ShipmentBatch {

    private final OrderRepository orders;
    private final ShipmentRepository shipments;
    private final ProcessLog log;

    // 실행 결과 (등록한 주문 ID -> SHIPMENT_ID, 실패한 주문 ID -> 원인)
    public record Result(Map<String, String> shipped, Map<String, String> failed) {
    }

    // 생성자, 주문 DB와 운송 DB, 처리 이력 지정
    public ShipmentBatch(OrderRepository orders, ShipmentRepository shipments, ProcessLog log) {
        this.orders = orders;
        this.shipments = shipments;
        this.log = log;
    }

    // 1회 실행: STATUS='N' 주문을 SHIPMENT_TB에 등록 후 Y로 변경 (주문별 처리, 한 건 실패해도 계속)
    public synchronized Result runOnce() throws SQLException {
        Map<String, String> shipped = new LinkedHashMap<>();
        Map<String, String> failed = new LinkedHashMap<>();
        List<SavedOrder> pending = orders.findPending();
        if (pending.isEmpty()) {
            return new Result(shipped, failed);
        }

        try (Connection conn = shipments.connect()) {
            Map<String, String> existing = shipments.shipmentIdsByOrder(conn);  // 이전 실행에서 등록만 되고 Y 변경에 실패한 주문 확인용
            String lastId = shipments.maxShipmentId(conn);

            for (SavedOrder order : pending) {
                try {
                    String shipmentId = existing.get(order.orderId());
                    boolean recovered = shipmentId != null;
                    if (!recovered) {  // 아직 등록 안 된 주문만 INSERT (중복 등록 방지)
                        shipmentId = IdGenerator.next(lastId);
                        shipments.insert(conn, shipmentId, order.orderId(), order.order().itemId(), order.order().address());
                        lastId = shipmentId;
                    }
                    orders.markShipped(order.orderId());  // 실패하면 다음 실행 때 existing으로 찾아 Y만 변경
                    shipped.put(order.orderId(), shipmentId);
                    log.write(order.orderId(), Stage.SHIP, ProcessLog.Result.OK,
                            "SHIPMENT_ID=" + shipmentId + ", STATUS=Y" + (recovered ? " (already shipped, status only)" : ""));
                } catch (SQLException e) {  // 이 주문만 실패 처리하고 다음 주문 계속
                    failed.put(order.orderId(), e.getMessage());
                    log.write(order.orderId(), Stage.SHIP, ProcessLog.Result.FAIL, e.getMessage());
                }
            }
        }
        return new Result(shipped, failed);
    }
}
