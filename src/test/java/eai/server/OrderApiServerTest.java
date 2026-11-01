package eai.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import eai.db.DbInfo;
import eai.log.ProcessLog;
import eai.order.OrderRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** OrderApiServer 테스트. 메모리 DB(H2)로 HTTP 요청부터 저장, 응답까지 전체 흐름을 확인한다. */
class OrderApiServerTest {

    private static final String XML = """
            <HEADER><USER_ID>USER01</USER_ID><NAME>김철수</NAME><ADDRESS>서울특별시 강남구</ADDRESS></HEADER>
            <ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM01</ITEM_ID><ITEM_NAME>청바지</ITEM_NAME><PRICE>33000</PRICE></ITEM>
            <ITEM><USER_ID>USER99</USER_ID><ITEM_ID>ITEM70</ITEM_ID><ITEM_NAME>모자</ITEM_NAME><PRICE>23000</PRICE></ITEM>
            """;

    @TempDir
    Path tempDir;

    private HttpServer server;
    private URI uri;
    private ProcessLog log;

    @BeforeEach
    void startServer() throws Exception {  // 메모리 DB에 테이블 생성 후 빈 포트로 서버 시작
        DbInfo db = new DbInfo("jdbc:h2:mem:" + System.nanoTime() + ";DB_CLOSE_DELAY=-1", "sa", "", "ORDER_TB");
        try (Connection conn = db.connect(); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE ORDER_TB (ORDER_ID VARCHAR(100), APPLICANT_KEY VARCHAR(100), USER_ID VARCHAR(100), ITEM_ID VARCHAR(100),"
                    + " NAME VARCHAR(100), ADDRESS VARCHAR(100), ITEM_NAME VARCHAR(100), PRICE VARCHAR(100), STATUS VARCHAR(100),"
                    + " PRIMARY KEY (ORDER_ID, APPLICANT_KEY))");
        }
        log = new ProcessLog(tempDir.resolve("process.log"));
        server = new OrderApiServer(new OrderRepository(db, "KEY1"), null, log).start(0);
        uri = URI.create("http://localhost:" + server.getAddress().getPort() + OrderApiServer.PATH);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private HttpResponse<String> post(byte[] body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void savesOrdersAndReportsUnmatchedItems() throws Exception {  // 저장 결과와 주문자 없는 상품을 응답
        HttpResponse<String> response = post(XML.getBytes(Charset.forName("EUC-KR")));

        assertEquals(200, response.statusCode());
        assertEquals("{\"savedCount\":1,\"savedOrderIds\":[\"A001\"],\"duplicateCount\":0,\"unmatchedItems\":[\"USER99/ITEM70\"]}", response.body());
    }

    @Test
    void writesProcessLog() throws Exception {  // 저장한 주문과 저장하지 않은 상품 모두 이력에 남김
        post(XML.getBytes(Charset.forName("EUC-KR")));

        assertTrue(log.find("A001").get(0).endsWith("| A001 | SAVE | OK | USER01/ITEM01"));
        assertTrue(log.find("USER99/ITEM70").get(0).endsWith("| USER99/ITEM70 | SAVE | SKIP | no HEADER for USER_ID"));
    }

    @Test
    void reportsDuplicateOnResend() throws Exception {  // 같은 XML 재전송 시 중복으로 처리
        byte[] body = XML.getBytes(Charset.forName("EUC-KR"));
        post(body);

        assertTrue(post(body).body().contains("\"savedCount\":0,\"savedOrderIds\":[],\"duplicateCount\":1"));
    }

    @Test
    void rejectsInvalidXml() throws Exception {  // 잘못된 XML은 400
        assertEquals(400, post("<ITEM><USER_ID>".getBytes()).statusCode());
    }

    @Test
    void rejectsGet() throws Exception {  // POST 외 요청은 405
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        assertEquals(405, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
