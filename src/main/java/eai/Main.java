package eai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eai.config.AppConfig;
import eai.crypto.ConnectionDecryptor;
import eai.db.DbInfo;
import eai.order.OrderXmlParser;
import eai.recruiting.AssignmentInfo;
import eai.recruiting.RecruitingClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Main {

    static final Path RESPONSE_FILE = Path.of("data", "recruiting-response.json");  // API 응답 저장 위치 (git 제외)

    // 실행 인자로 받은 명령 실행
    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(Path.of("config.properties"));  // 설정 파일이 없으면 여기서 바로 실패
        String command = args.length > 0 ? args[0] : "";

        switch (command) {
            case "fetch" -> fetch(config);      // 과제 API 호출 후 응답 저장
            case "decrypt" -> decrypt(config);  // 저장된 응답의 접속 정보 복호화
            case "parse" -> parse();            // 샘플 주문 XML 파싱 결과 확인
            case "db-check" -> dbCheck(config); // 주문/운송 DB 연결 확인
            default -> System.out.println("Usage: ./gradlew run --args=\"fetch|decrypt|parse|db-check\"");
        }
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
