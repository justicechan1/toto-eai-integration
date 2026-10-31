package eai.receipt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eai.order.Order;
import eai.order.SavedOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** ReceiptFile 테스트. 파일명 규칙, 기존 영수증 판단, CSV 내용(BOM, 한글, 쉼표 처리)을 확인한다. */
class ReceiptFileTest {

    private static final LocalDateTime TIME = LocalDateTime.of(2026, 10, 31, 16, 16, 45);

    @Test
    void fileNameHasNameOrderIdAndTime() {  // 이름_주문ID_시각.csv
        assertEquals("홍길동_A001_20261031161645.csv", ReceiptFile.fileName("홍길동", "A001", TIME));
    }

    @Test
    void existsMatchesOnlyMyOrder() {  // 같은 이름, 같은 주문 ID의 csv만 기존 영수증으로 인정
        List<String> files = List.of("홍길동_A001_20261031161645.csv", "박도영_A002.xml", "홍길동_A0010_x.csv");
        assertTrue(ReceiptFile.exists("홍길동", "A001", files));
        assertFalse(ReceiptFile.exists("홍길동", "A002", files));  // 다른 지원자 파일은 무시
        assertFalse(ReceiptFile.exists("김철수", "A001", files));
    }

    @Test
    void csvHasBomHeaderAndKoreanRow() {  // UTF-8 BOM + 헤더 + 주문 1행
        SavedOrder saved = new SavedOrder("A001", new Order("USER09", "ITEM45", "한지민", "서울특별시 성동구", "선글라스", "95000"), TIME);
        byte[] csv = ReceiptFile.csv(saved);

        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(csv, 3));
        assertEquals("ORDER_ID,USER_ID,NAME,ADDRESS,ITEM_ID,ITEM_NAME,PRICE,ORDER_TIME\r\n"
                        + "A001,USER09,한지민,서울특별시 성동구,ITEM45,선글라스,95000,2026-10-31 16:16:45\r\n",
                new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8));
    }

    @Test
    void quotesValueWithCommaOrQuote() {  // 쉼표, 큰따옴표가 있으면 큰따옴표로 감싸기
        assertEquals("\"서울, 강남구\"", ReceiptFile.field("서울, 강남구"));
        assertEquals("\"5\"\"인치\"\"\"", ReceiptFile.field("5\"인치\""));
        assertEquals("선글라스", ReceiptFile.field("선글라스"));
    }
}
