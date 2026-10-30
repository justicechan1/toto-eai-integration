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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final URI uri;
    private final String authorization;

    public RecruitingClient(String url, String username, String password) {
        this.uri = URI.create(url);
        String credentials = username + ":" + password;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public JsonNode requestAssignment(String name, String phone, String email) throws IOException, InterruptedException {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("NAME", name);
        body.put("PHONE_NUMBER", phone);
        body.put("E_MAIL", email);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("RecruitingTest call failed: HTTP " + response.statusCode() + " " + response.body());
        }
        return MAPPER.readTree(response.body());
    }
}
