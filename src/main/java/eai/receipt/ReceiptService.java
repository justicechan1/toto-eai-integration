package eai.receipt;

import eai.log.ProcessLog;
import eai.log.ProcessLog.Stage;
import eai.order.OrderRepository;
import eai.order.SavedOrder;
import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReceiptService {

    private final OrderRepository repository;
    private final FtpUploader uploader;
    private final String applicantName;
    private final ProcessLog log;

    // 업로드 결과 (업로드한 파일명 목록, 실패한 파일명과 원인)
    public record Result(List<String> uploaded, Map<String, String> failed) {
    }

    // 생성자, 주문 저장소와 FTP, 파일명에 넣을 지원자 이름, 처리 이력 지정
    public ReceiptService(OrderRepository repository, FtpUploader uploader, String applicantName, ProcessLog log) {
        this.repository = repository;
        this.uploader = uploader;
        this.applicantName = applicantName;
        this.log = log;
    }

    // 영수증이 없는 주문을 찾아 업로드 (이미 있으면 건너뜀, 실패한 주문은 다음 실행 때 다시 시도)
    public synchronized Result uploadMissing() throws SQLException, IOException {
        List<String> existing;
        try {
            existing = uploader.listFileNames();  // FTP 목록이 업로드 이력 역할
        } catch (IOException e) {  // FTP 접속 실패
            log.write("-", Stage.RECEIPT, ProcessLog.Result.FAIL, "FTP list failed: " + e.getMessage());
            throw e;
        }
        LocalDateTime now = LocalDateTime.now();

        Map<String, byte[]> files = new LinkedHashMap<>();
        Map<String, String> orderIds = new LinkedHashMap<>();  // 파일명 -> 주문 ID (이력 기록용)
        for (SavedOrder saved : repository.findAll()) {
            if (!ReceiptFile.exists(applicantName, saved.orderId(), existing)) {
                String fileName = ReceiptFile.fileName(applicantName, saved.orderId(), now);
                files.put(fileName, ReceiptFile.csv(saved));
                orderIds.put(fileName, saved.orderId());
            }
        }
        if (files.isEmpty()) {
            return new Result(List.of(), Map.of());
        }

        Map<String, String> failed;
        try {
            failed = uploader.upload(files);
        } catch (IOException e) {  // 접속 실패 시 대상 주문 모두 실패로 기록
            orderIds.values().forEach(orderId -> log.write(orderId, Stage.RECEIPT, ProcessLog.Result.FAIL, e.getMessage()));
            throw e;
        }
        List<String> uploaded = new ArrayList<>(files.keySet());
        uploaded.removeAll(failed.keySet());
        uploaded.forEach(fileName -> log.write(orderIds.get(fileName), Stage.RECEIPT, ProcessLog.Result.OK, fileName));
        failed.forEach((fileName, error) -> log.write(orderIds.get(fileName), Stage.RECEIPT, ProcessLog.Result.FAIL, error));
        return new Result(uploaded, failed);
    }
}
