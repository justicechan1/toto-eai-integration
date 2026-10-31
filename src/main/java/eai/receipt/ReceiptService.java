package eai.receipt;

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

    // 업로드 결과 (업로드한 파일명 목록, 실패한 파일명과 원인)
    public record Result(List<String> uploaded, Map<String, String> failed) {
    }

    // 생성자, 주문 저장소와 FTP, 파일명에 넣을 지원자 이름 지정
    public ReceiptService(OrderRepository repository, FtpUploader uploader, String applicantName) {
        this.repository = repository;
        this.uploader = uploader;
        this.applicantName = applicantName;
    }

    // 영수증이 없는 주문을 찾아 업로드 (이미 있으면 건너뜀, 실패한 주문은 다음 실행 때 다시 시도)
    public synchronized Result uploadMissing() throws SQLException, IOException {
        List<String> existing = uploader.listFileNames();  // FTP 목록이 업로드 이력 역할
        LocalDateTime now = LocalDateTime.now();

        Map<String, byte[]> files = new LinkedHashMap<>();
        for (SavedOrder saved : repository.findAll()) {
            if (!ReceiptFile.exists(applicantName, saved.orderId(), existing)) {
                files.put(ReceiptFile.fileName(applicantName, saved.orderId(), now), ReceiptFile.csv(saved));
            }
        }
        if (files.isEmpty()) {
            return new Result(List.of(), Map.of());
        }

        Map<String, String> failed = uploader.upload(files);
        List<String> uploaded = new ArrayList<>(files.keySet());
        uploaded.removeAll(failed.keySet());
        return new Result(uploaded, failed);
    }
}
