package eai.recruiting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 과제 제공 API(RecruitingTest)를 호출해 지원자 키, 암호화된 접속 정보,
 * 샘플 데이터를 JSON 그대로 반환한다.
 */
public class RecruitingClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();  // Java 객체 <-> JSON 문자열

    private final HttpClient http = HttpClient.newBuilder()  // JDK 내장 HTTP 클라이언트
            .connectTimeout(Duration.ofSeconds(10))  // 연결 타임아웃 10초
            .build();
    private final URI uri;
    private final String authorization;

    public RecruitingClient(String url, String username, String password) {
        this.uri = URI.create(url);
        String credentials = username + ":" + password;  // 아이디:비밀번호 문자열
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));  // 헤더 값: Basic + Base64 인코딩
    }

    public JsonNode requestAssignment(String name, String phone, String email) throws IOException, InterruptedException {
        Map<String, String> body = new LinkedHashMap<>();  // 요청 본문
        body.put("NAME", name);
        body.put("PHONE_NUMBER", phone);
        body.put("E_MAIL", email);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))  // 응답 타임아웃 30초
                .header("Authorization", authorization)  // 인증 헤더
                .header("Content-Type", "application/json; charset=UTF-8")  // 한글 전송용 UTF-8
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))  // Map을 JSON 문자열로 바꾸고 POST
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));  // 응답이 올 때까지 대기
        if (response.statusCode() != 200) {  // 실패 시 상태 코드와 응답 본문 출력
            throw new IOException("RecruitingTest call failed: HTTP " + response.statusCode() + " " + response.body());
        }
        return MAPPER.readTree(response.body());  // 응답 JSON을 트리 형태로 변환
    }
}
