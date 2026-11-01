package eai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eai.config.AppConfig;
import eai.crypto.ConnectionDecryptor;
import eai.db.DbInfo;
import eai.log.ProcessLog;
import eai.order.Order;
import eai.order.OrderRepository;
import eai.order.OrderXmlParser;
import eai.receipt.FtpUploader;
import eai.receipt.ReceiptService;
import eai.recruiting.AssignmentInfo;
import eai.recruiting.RecruitingClient;
import eai.server.OrderApiServer;
import eai.shipment.ShipmentBatch;
import eai.shipment.ShipmentRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {

    static final Path RESPONSE_FILE = Path.of("data", "recruiting-response.json");  // API 응답 저장 위치 (git 제외)
    static final int PORT = 8080;  // 주문 수신 API 포트
    static final int SHIP_INTERVAL_SECONDS = 60;  // 배송 배치 실행 주기
    static final Path LOG_FILE = Path.of("logs", "process.log");  // 처리 이력 (git 제외)
    static final ProcessLog LOG = new ProcessLog(LOG_FILE);

    // 실행 인자로 받은 명령 실행
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(Path.of("config.properties"));  // 설정 파일이 없으면 여기서 바로 실패
        String command = args.length > 0 ? args[0] : "";

        switch (command) {
            case "fetch" -> fetch(config);      // 과제 API 호출 후 응답 저장
            case "decrypt" -> decrypt(config);  // 저장된 응답의 접속 정보 복호화
            case "parse" -> parse();            // 샘플 주문 XML 파싱 결과 확인
            case "db-check" -> dbCheck(config); // 주문/운송 DB 연결 확인
            case "serve" -> serve(config);      // 주문 수신 API 서버 실행
            case "send-sample" -> sendSample(args.length > 1 ? Integer.parseInt(args[1]) : null);  // 샘플 주문 XML 전송 (건수 지정 가능)
            case "receipts" -> receipts(config);  // 영수증 없는 주문의 영수증 업로드
            case "ship" -> ship(config);          // 배송 배치 1회 실행
            case "trace" -> trace(args.length > 1 ? args[1] : null);  // 처리 이력 조회 (주문 ID 지정 가능)
            default -> System.out.println("Usage: ./gradlew run --args=\"fetch|decrypt|parse|db-check|serve|send-sample [건수]|receipts|ship|trace [주문ID]\"");
        }
    }

    // 주문 수신 API 서버 실행 + 배송 배치 주기 실행 (종료: Ctrl+C)
    private static void serve(AppConfig config) throws Exception {
        AssignmentInfo info = AssignmentInfo.load(RESPONSE_FILE, config.phone());
        OrderRepository repository = new OrderRepository(info.orderDb(), info.applicantKey());
        ReceiptService receipts = new ReceiptService(repository, new FtpUploader(info.ftp()), config.require("applicant.name"), LOG);
        new OrderApiServer(repository, receipts, LOG).start(PORT);
        System.out.println("Order API listening on http://localhost:" + PORT + OrderApiServer.PATH);

        ShipmentBatch batch = shipmentBatch(info);
        Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
            try {
                batch.runOnce();  // 주문별 결과는 처리 이력에 기록됨
            } catch (Exception e) {  // 예외가 나도 다음 주기는 계속 실행
                LOG.write("-", ProcessLog.Stage.SHIP, ProcessLog.Result.FAIL, "batch error: " + e.getMessage());
            }
        }, SHIP_INTERVAL_SECONDS, SHIP_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("Shipment batch every " + SHIP_INTERVAL_SECONDS + "s, process log: " + LOG_FILE);
    }

    // 배송 배치 생성 (주문 DB, 운송 DB)
    private static ShipmentBatch shipmentBatch(AssignmentInfo info) {
        return new ShipmentBatch(new OrderRepository(info.orderDb(), info.applicantKey()),
                new ShipmentRepository(info.shipmentDb(), info.applicantKey()), LOG);
    }

    // 배송 배치 1회 실행 후 건수 출력
    private static void ship(AppConfig config) throws Exception {
        ShipmentBatch.Result result = shipmentBatch(AssignmentInfo.load(RESPONSE_FILE, config.phone())).runOnce();
        System.out.println("shipped: " + result.shipped().size() + ", failed: " + result.failed().size());
    }

    // 영수증이 없는 주문의 영수증을 FTP에 업로드 (실패분 재시도용)
    private static void receipts(AppConfig config) throws Exception {
        AssignmentInfo info = AssignmentInfo.load(RESPONSE_FILE, config.phone());
        OrderRepository repository = new OrderRepository(info.orderDb(), info.applicantKey());
        ReceiptService.Result result = new ReceiptService(repository, new FtpUploader(info.ftp()), config.require("applicant.name"), LOG).uploadMissing();
        System.out.println("uploaded: " + result.uploaded().size() + ", failed: " + result.failed().size());
    }

    // 처리 이력 조회: 주문 ID를 주면 그 주문의 전체 이력, 없으면 주문별 마지막 단계
    private static void trace(String key) throws Exception {
        if (key != null) {
            LOG.find(key).forEach(System.out::println);
            return;
        }
        LOG.latestByKey().values().forEach(System.out::println);
    }

    // 샘플 주문 XML을 주문 수신 API로 전송 후 응답 출력 (count가 있으면 앞에서부터 그 건수만)
    private static void sendSample(Integer count) throws Exception {
        JsonNode response = new ObjectMapper().readTree(RESPONSE_FILE.toFile());
        byte[] xml = Base64.getDecoder().decode(response.get("SAMPLE_DATA").asText());  // 원본 EUC-KR 바이트 그대로
        if (count != null) {
            List<Order> orders = new OrderXmlParser().parse(xml).orders().stream().limit(count).toList();
            xml = toXml(orders).getBytes(Charset.forName("EUC-KR"));  // 일부만 같은 형식의 XML로 다시 만듦
            orders.forEach(order -> System.out.println("send: " + order));
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + OrderApiServer.PATH))
                .header("Content-Type", "application/xml; charset=EUC-KR")
                .POST(HttpRequest.BodyPublishers.ofByteArray(xml))
                .build();
        HttpResponse<String> result = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP " + result.statusCode() + " " + result.body());
    }

    // 주문 목록을 원본과 같은 형식(HEADER + ITEM)의 XML 문자열로 변환
    private static String toXml(List<Order> orders) {
        StringBuilder sb = new StringBuilder();
        for (Order o : orders) {
            sb.append("<HEADER><USER_ID>").append(escape(o.userId())).append("</USER_ID><NAME>").append(escape(o.name()))
                    .append("</NAME><ADDRESS>").append(escape(o.address())).append("</ADDRESS></HEADER>\n")
                    .append("<ITEM><USER_ID>").append(escape(o.userId())).append("</USER_ID><ITEM_ID>").append(escape(o.itemId()))
                    .append("</ITEM_ID><ITEM_NAME>").append(escape(o.itemName())).append("</ITEM_NAME><PRICE>").append(escape(o.price()))
                    .append("</PRICE></ITEM>\n");
        }
        return sb.toString();
    }

    // XML 특수문자 치환
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // 주문/운송 DB 연결 후 컬럼과 내 데이터 건수 출력
    private static void dbCheck(AppConfig config) throws Exception {
        AssignmentInfo info = AssignmentInfo.load(RESPONSE_FILE, config.phone());

        for (DbInfo db : new DbInfo[] {info.orderDb(), info.shipmentDb()}) {
            try (Connection conn = db.connect()) {
                System.out.println(db.table() + " (" + conn.getMetaData().getDatabaseProductVersion().lines().findFirst().orElse("") + ")");

                List<String> columns = new ArrayList<>();
                try (ResultSet rs = conn.getMetaData().getColumns(null, null, db.table(), null)) {  // 테이블 컬럼 목록
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME") + " " + rs.getString("TYPE_NAME") + "(" + rs.getInt("COLUMN_SIZE") + ")");
                    }
                }
                System.out.println("  columns: " + columns);

                try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM " + db.table() + " WHERE APPLICANT_KEY = ?")) {
                    ps.setString(1, info.applicantKey());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        System.out.println("  my rows: " + rs.getInt(1));  // 내 지원자 키로 저장된 건수
                    }
                }
            }
        }
    }

    // 샘플 주문 XML 파싱 결과 출력
    private static void parse() throws Exception {
        JsonNode response = new ObjectMapper().readTree(RESPONSE_FILE.toFile());
        byte[] xml = Base64.getDecoder().decode(response.get("SAMPLE_DATA").asText());  // Base64 -> EUC-KR 바이트
        OrderXmlParser.Result result = new OrderXmlParser().parse(xml);

        System.out.println("orders: " + result.orders().size() + ", unmatched items: " + result.unmatchedItems().size());
        result.orders().stream().limit(3).forEach(order -> System.out.println("  " + order));  // 앞 3건만 출력
        result.unmatchedItems().forEach(item -> System.out.println("  [no HEADER] " + item));  // 주문자 없는 상품
    }

    // 접속 정보 복호화 결과 출력 (비밀번호 제외)
    private static void decrypt(AppConfig config) throws Exception {
        JsonNode response = new ObjectMapper().readTree(RESPONSE_FILE.toFile());  // API 재호출 없이 저장본 사용
        ConnectionDecryptor decryptor = new ConnectionDecryptor(config.phone());

        for (String conn : new String[] {"ORDER_TB_CONN", "SHIPMENT_TB_CONN", "FTP_CONN"}) {
            System.out.println(conn);
            decryptor.decryptFields(response.get(conn)).forEach((field, value) ->
                    System.out.println("  " + field + " = " + (field.equals("PASSWORD") ? "****" : value)));  // 비밀번호는 가림
        }
    }

    // 과제 API 호출 후 응답 저장
    private static void fetch(AppConfig config) throws Exception {
        RecruitingClient client = new RecruitingClient(
                config.require("recruiting.url"),
                config.require("recruiting.username"),
                config.require("recruiting.password"));
        JsonNode response = client.requestAssignment(
                config.require("applicant.name"), config.phone(), config.require("applicant.email"));

        Files.createDirectories(RESPONSE_FILE.getParent());  // data/ 폴더가 없으면 생성
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(RESPONSE_FILE.toFile(), response);  // 들여쓰기해서 저장

        System.out.println("Saved " + RESPONSE_FILE);
        response.fieldNames().forEachRemaining(field ->  // 값은 빼고 필드 이름과 종류만 출력
                System.out.println("  " + field + " (" + response.get(field).getNodeType() + ")"));
    }
}
