package eai.receipt;

import eai.order.Order;
import eai.order.SavedOrder;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class ReceiptFile {

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};  // 엑셀에서 한글이 깨지지 않도록
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private ReceiptFile() {
    }

    // 파일명: 이름_주문ID_업로드시각.csv (FTP 덮어쓰기 불가라 항상 새 이름)
    public static String fileName(String applicantName, String orderId, LocalDateTime now) {
        return applicantName + "_" + orderId + "_" + now.format(FILE_TIME) + ".csv";
    }

    // 이 주문의 영수증이 이미 있는지 (파일명 앞부분 "이름_주문ID_"로 판단)
    public static boolean exists(String applicantName, String orderId, List<String> fileNames) {
        String prefix = applicantName + "_" + orderId + "_";
        return fileNames.stream().anyMatch(name -> name.startsWith(prefix) && name.endsWith(".csv"));
    }

    // 영수증 CSV 내용 (UTF-8 BOM + 헤더 + 주문 1행)
    public static byte[] csv(SavedOrder saved) {
        Order o = saved.order();
        String text = "ORDER_ID,USER_ID,NAME,ADDRESS,ITEM_ID,ITEM_NAME,PRICE,ORDER_TIME\r\n"
                + String.join(",", field(saved.orderId()), field(o.userId()), field(o.name()), field(o.address()),
                        field(o.itemId()), field(o.itemName()), field(o.price()), field(saved.createTime().format(TIME))) + "\r\n";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    // CSV 값 처리: 쉼표, 큰따옴표, 줄바꿈이 있으면 큰따옴표로 감싸기
    static String field(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
