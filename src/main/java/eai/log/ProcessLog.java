package eai.log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ProcessLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final String SEP = " | ";

    // 처리 단계: 수신, ORDER_TB 저장, 영수증 업로드, 배송 등록
    public enum Stage { RECEIVE, SAVE, RECEIPT, SHIP }

    // 처리 결과: 성공, 건너뜀(중복·주문자 없음 등), 실패
    public enum Result { OK, SKIP, FAIL }

    private final Path file;

    // 생성자, 로그 파일 위치 지정
    public ProcessLog(Path file) {
        this.file = file;
    }

    // 처리 이력 1줄 기록: 시각 | 키(주문 ID 등) | 단계 | 결과 | 상세 (기록 실패가 처리를 멈추지 않음)
    public synchronized void write(String key, Stage stage, Result result, String detail) {
        String line = String.join(SEP, LocalDateTime.now().format(TIME), key, stage.name(), result.name(),
                detail == null ? "" : detail.replaceAll("\\s*[\\r\\n|]+\\s*", " "));  // 한 줄 형식 유지
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("process log write failed: " + e.getMessage());
        }
        System.out.println(line);
    }

    // 키(주문 ID 또는 USER_ID/ITEM_ID)로 이력 조회
    public List<String> find(String key) throws IOException {
        return lines().stream().filter(line -> key.equals(field(line, 1))).toList();
    }

    // 키별 마지막 이력 (주문이 어디까지 처리됐는지 한눈에)
    public Map<String, String> latestByKey() throws IOException {
        Map<String, String> latest = new LinkedHashMap<>();
        for (String line : lines()) {
            String key = field(line, 1);
            if (!key.equals("-")) {  // 주문과 무관한 요청 단위 기록 제외
                latest.remove(key);  // 마지막 처리 순서대로 정렬
                latest.put(key, line);
            }
        }
        return latest;
    }

    // 로그 파일 전체 줄 (없으면 빈 목록)
    private List<String> lines() throws IOException {
        return Files.exists(file) ? Files.readAllLines(file, StandardCharsets.UTF_8) : List.of();
    }

    // 한 줄에서 n번째 항목
    private static String field(String line, int index) {
        String[] fields = line.split(" \\| ", -1);
        return fields.length > index ? fields[index] : "";
    }
}
