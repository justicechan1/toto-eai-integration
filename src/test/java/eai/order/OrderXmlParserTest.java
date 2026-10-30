package eai.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** OrderXmlParser 테스트. USER_ID 기준 합치기, 한글 유지, 잘못된 입력 거부를 확인한다. */
class OrderXmlParserTest {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    // 실제 샘플과 같은 구조: 루트 요소 없음, HEADER 뒤의 ITEM이 다른 USER_ID를 가질 수 있음
    private static final String SAMPLE = """
            <HEADER>
                <USER_ID>USER01</USER_ID>
                <NAME>김철수</NAME>
                <ADDRESS>서울특별시 강남구</ADDRESS>
            </HEADER>

            <ITEM>
                <USER_ID>USER02</USER_ID>
                <ITEM_ID>ITEM03</ITEM_ID>
                <ITEM_NAME>반팔티</ITEM_NAME>
                <PRICE>15000</PRICE>
            </ITEM>

            <HEADER>
                <USER_ID>USER02</USER_ID>
                <NAME>이영희</NAME>
                <ADDRESS>서울특별시 서초구</ADDRESS>
            </HEADER>

            <ITEM>
                <USER_ID>USER01</USER_ID>
                <ITEM_ID>ITEM01</ITEM_ID>
                <ITEM_NAME>청바지</ITEM_NAME>
                <PRICE>33000</PRICE>
            </ITEM>

            <ITEM>
                <USER_ID>USER99</USER_ID>
                <ITEM_ID>ITEM70</ITEM_ID>
                <ITEM_NAME>모자</ITEM_NAME>
                <PRICE>23000</PRICE>
            </ITEM>
            """;

    private static OrderXmlParser.Result parse(String xml) {
        return new OrderXmlParser().parse(xml.getBytes(EUC_KR));  // 실제 입력처럼 EUC-KR 바이트로 전달
    }

    @Test
    void joinsItemWithHeaderByUserIdAndKeepsKorean() {  // 순서가 아니라 USER_ID로 연결, 한글 유지
        List<Order> orders = parse(SAMPLE).orders();
        assertEquals(List.of(
                new Order("USER02", "ITEM03", "이영희", "서울특별시 서초구", "반팔티", "15000"),
                new Order("USER01", "ITEM01", "김철수", "서울특별시 강남구", "청바지", "33000")), orders);
    }

    @Test
    void reportsItemWithoutHeader() {  // 주문자 없는 상품은 따로 반환
        assertEquals(List.of(new OrderItem("USER99", "ITEM70", "모자", "23000")), parse(SAMPLE).unmatchedItems());
    }

    @Test
    void acceptsXmlDeclaration() {  // XML 선언이 있어도 파싱
        assertEquals(2, parse("<?xml version=\"1.0\" encoding=\"EUC-KR\"?>\n" + SAMPLE).orders().size());
    }

    @Test
    void rejectsMissingField() {  // 필수 값 누락 시 실패
        String xml = "<ITEM><USER_ID>USER01</USER_ID><ITEM_ID>ITEM01</ITEM_ID><PRICE>1000</PRICE></ITEM>";
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }

    @Test
    void rejectsNonNumericPrice() {  // 가격이 숫자가 아니면 실패
        String xml = SAMPLE.replace("15000", "만오천원");
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }

    @Test
    void rejectsDoctype() {  // DTD 포함 XML 차단 (XXE 방지)
        String xml = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>" + SAMPLE;
        assertThrows(IllegalArgumentException.class, () -> parse(xml));
    }
}
