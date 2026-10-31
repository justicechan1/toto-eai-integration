package eai.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import eai.order.OrderItem;
import eai.order.OrderRepository;
import eai.order.OrderXmlParser;
import eai.receipt.ReceiptService;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderApiServer {

    public static final String PATH = "/orders";
    private static final int MAX_BODY_BYTES = 1024 * 1024;  // 요청 본문 최대 1MB
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrderXmlParser parser = new OrderXmlParser();
    private final OrderRepository repository;
    private final ReceiptService receipts;

    // 생성자, 주문 저장소와 영수증 업로드 지정 (receipts가 null이면 영수증 단계 생략)
    public OrderApiServer(OrderRepository repository, ReceiptService receipts) {
        this.repository = repository;
        this.receipts = receipts;
    }

    // 지정한 포트로 서버 시작
    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(PATH, this::handle);
        server.start();
        return server;
    }

    // 주문 XML 요청 처리: 파싱 -> 저장 -> 결과 응답
    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            if (!exchange.getRequestMethod().equals("POST")) {
                respond(exchange, 405, Map.of("error", "POST only"));
                return;
            }
            byte[] body = readBody(exchange.getRequestBody());
            if (body == null) {
                respond(exchange, 413, Map.of("error", "Request body exceeds " + MAX_BODY_BYTES + " bytes"));
                return;
            }

            OrderXmlParser.Result parsed;
            try {
                parsed = parser.parse(body);
            } catch (IllegalArgumentException e) {  // XML 형식 오류, 필수 값 누락
                respond(exchange, 400, Map.of("error", e.getMessage()));
                return;
            }

            OrderRepository.SaveResult saved;
            try {
                saved = repository.saveAll(parsed.orders());
            } catch (SQLException e) {  // DB 오류, 전체 롤백됨
                respond(exchange, 500, Map.of("error", "DB error: " + e.getMessage()));
                return;
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("savedCount", saved.savedIds().size());
            result.put("savedOrderIds", saved.savedIds());
            result.put("duplicateCount", saved.duplicates().size());
            result.put("unmatchedItems", itemIds(parsed.unmatchedItems()));  // 주문자 정보가 없어 저장하지 않은 상품
            if (receipts != null && !saved.savedIds().isEmpty()) {
                result.put("receipt", uploadReceipts());
            }
            respond(exchange, 200, result);
        }
    }

    // 영수증 업로드, 실패해도 주문 저장은 유지 (다음 업로드 때 다시 시도)
    private Map<String, Object> uploadReceipts() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            ReceiptService.Result uploaded = receipts.uploadMissing();
            result.put("uploaded", uploaded.uploaded());
            result.put("failed", uploaded.failed());
        } catch (IOException | SQLException e) {  // FTP 접속 오류 등
            result.put("error", e.getMessage());
        }
        return result;
    }

    // 본문 읽기, 최대 크기를 넘으면 null
    private static byte[] readBody(InputStream in) throws IOException {
        byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
        return body.length > MAX_BODY_BYTES ? null : body;
    }

    // JSON 응답 전송
    private static void respond(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] json = MAPPER.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, json.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(json);
        }
    }

    // 상품 목록을 "USER_ID/ITEM_ID" 형태로
    private static List<String> itemIds(List<OrderItem> items) {
        return items.stream().map(item -> item.userId() + "/" + item.itemId()).toList();
    }
}
