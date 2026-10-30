package eai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eai.config.AppConfig;
import eai.crypto.ConnectionDecryptor;
import eai.order.OrderXmlParser;
import eai.recruiting.RecruitingClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/**
 * 프로그램 시작점. 실행 인자로 받은 명령에 따라 각 기능을 조립해 실행한다.
 * - fetch   : 과제 API 호출 후 응답을 data/에 저장 (RecruitingClient)
 * - decrypt : 저장된 응답의 접속 정보 복호화 (ConnectionDecryptor)
 * - parse   : 저장된 응답의 샘플 주문 XML 파싱 (OrderXmlParser)
 * 모든 명령은 시작할 때 config.properties를 읽는다 (AppConfig).
 */
public class Main {

    /** API 응답 원본. 다음 단계에서 API를 다시 호출하지 않도록 로컬에 저장한다(git 제외). */
    static final Path RESPONSE_FILE = Path.of("data", "recruiting-response.json");

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.load(Path.of("config.properties"));  // 설정 파일이 없으면 여기서 바로 실패
        String command = args.length > 0 ? args[0] : "";

        switch (command) {
            case "fetch" -> fetch(config);      // 과제 API 호출 후 응답 저장
            case "decrypt" -> decrypt(config);  // 저장된 응답의 접속 정보 복호화
            case "parse" -> parse();            // 샘플 주문 XML 파싱 결과 확인
            default -> System.out.println("Usage: ./gradlew run --args=\"fetch|decrypt|parse\"");
        }
    }

    /** 저장된 응답의 SAMPLE_DATA(Base64, EUC-KR XML)를 파싱해 결과를 확인한다. */
    private static void parse() throws Exception {
        JsonNode response = new ObjectMapper().readTree(RESPONSE_FILE.toFile());
        byte[] xml = Base64.getDecoder().decode(response.get("SAMPLE_DATA").asText());  // Base64 -> EUC-KR 바이트
        OrderXmlParser.Result result = new OrderXmlParser().parse(xml);

        System.out.println("orders: " + result.orders().size() + ", unmatched items: " + result.unmatchedItems().size());
        result.orders().stream().limit(3).forEach(order -> System.out.println("  " + order));  // 앞 3건만 출력
        result.unmatchedItems().forEach(item -> System.out.println("  [no HEADER] " + item));  // 주문자 없는 상품
    }

    /** 저장된 응답의 접속 정보를 복호화해 확인한다. 비밀번호는 화면에 출력하지 않는다. */
    private static void decrypt(AppConfig config) throws Exception {
        JsonNode response = new ObjectMapper().readTree(RESPONSE_FILE.toFile());  // API 재호출 없이 저장본 사용
        ConnectionDecryptor decryptor = new ConnectionDecryptor(config.phone());

        for (String conn : new String[] {"ORDER_TB_CONN", "SHIPMENT_TB_CONN", "FTP_CONN"}) {
            System.out.println(conn);
            decryptor.decryptFields(response.get(conn)).forEach((field, value) ->
                    System.out.println("  " + field + " = " + (field.equals("PASSWORD") ? "****" : value)));  // 비밀번호는 가림
        }
    }

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
