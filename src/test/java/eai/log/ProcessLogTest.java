package eai.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eai.log.ProcessLog.Result;
import eai.log.ProcessLog.Stage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ProcessLog 테스트. 한 줄 형식, 주문 ID별 조회, 주문별 마지막 단계 요약을 확인한다. */
class ProcessLogTest {

    @TempDir
    Path tempDir;

    @Test
    void writesOneLinePerEventAndFindsByKey() throws Exception {  // 시각 | 키 | 단계 | 결과 | 상세, 키로 조회
        ProcessLog log = new ProcessLog(tempDir.resolve("logs/process.log"));
        log.write("A001", Stage.SAVE, Result.OK, "USER09/ITEM45");
        log.write("A002", Stage.SAVE, Result.OK, "USER04/ITEM15");
        log.write("A001", Stage.RECEIPT, Result.FAIL, "550 Permission\r\ndenied | x");  // 줄바꿈, 구분자 제거

        List<String> a001 = log.find("A001");
        assertEquals(2, a001.size());
        assertTrue(a001.get(0).endsWith(" | A001 | SAVE | OK | USER09/ITEM45"));
        assertTrue(a001.get(1).endsWith(" | A001 | RECEIPT | FAIL | 550 Permission denied x"));
    }

    @Test
    void latestByKeyShowsLastStageOfEachOrder() throws Exception {  // 주문별 마지막 단계, 요청 단위 기록(-) 제외
        ProcessLog log = new ProcessLog(tempDir.resolve("process.log"));
        log.write("-", Stage.RECEIVE, Result.OK, "orders=2");
        log.write("A001", Stage.SAVE, Result.OK, "");
        log.write("A002", Stage.SAVE, Result.OK, "");
        log.write("A001", Stage.SHIP, Result.OK, "SHIPMENT_ID=A001");

        Map<String, String> latest = log.latestByKey();
        assertEquals(List.of("A002", "A001"), List.copyOf(latest.keySet()));  // 마지막 처리 순서
        assertTrue(latest.get("A001").contains("| SHIP | OK |"));
    }

    @Test
    void emptyWhenNoLogFile() throws Exception {  // 로그 파일이 없으면 빈 결과
        assertTrue(new ProcessLog(tempDir.resolve("none.log")).find("A001").isEmpty());
    }
}
