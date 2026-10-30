package eai.order;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * 주문 XML 변환 담당. EUC-KR 주문 XML을 ORDER_TB에 넣을 주문 목록으로 바꾼다.
 * - 입력: EUC-KR 바이트 XML (루트 요소 없이 HEADER, ITEM이 나열된 형태)
 * - 출력: Result (주문 목록 + 주문자를 못 찾은 상품 목록)
 * - 처리: HEADER(주문자)를 USER_ID별로 모은 뒤, ITEM(상품)마다 같은 USER_ID의 주문자와 합친다.
 *         HEADER와 ITEM은 순서가 아니라 USER_ID로 연결된다.
 * - 검사: 필수 값 누락, 숫자가 아닌 가격, DTD 포함 XML(XXE)은 실패 처리
 */
public class OrderXmlParser {

    private static final Charset EUC_KR = Charset.forName("EUC-KR");

    /** 파싱 결과. 주문자(HEADER)를 찾지 못한 상품은 버리지 않고 따로 돌려준다. */
    public record Result(List<Order> orders, List<OrderItem> unmatchedItems) {
    }

    private record Customer(String name, String address) {  // HEADER 요소
    }

    public Result parse(byte[] eucKrXml) {
        String xml = new String(eucKrXml, EUC_KR)  // 바이트 -> 한글 문자열
                .replaceFirst("^\\s*<\\?xml[^>]*\\?>", "");  // XML 선언이 있으면 제거 (루트로 감싸기 위해)
        Element root = parseDocument("<ROOT>" + xml + "</ROOT>");  // 루트 요소가 없어 감싸서 파싱

        Map<String, Customer> customers = new LinkedHashMap<>();  // USER_ID -> 주문자
        for (Element header : children(root, "HEADER")) {
            customers.put(text(header, "USER_ID"), new Customer(text(header, "NAME"), text(header, "ADDRESS")));
        }

        List<Order> orders = new ArrayList<>();
        List<OrderItem> unmatched = new ArrayList<>();
        for (Element itemElement : children(root, "ITEM")) {
            OrderItem item = new OrderItem(
                    text(itemElement, "USER_ID"), text(itemElement, "ITEM_ID"),
                    text(itemElement, "ITEM_NAME"), price(itemElement));
            Customer customer = customers.get(item.userId());
            if (customer == null) {  // 주문자 정보가 없는 상품
                unmatched.add(item);
                continue;
            }
            orders.add(new Order(item.userId(), item.itemId(), customer.name(), customer.address(), item.itemName(), item.price()));
        }
        return new Result(orders, unmatched);
    }

    private static Element parseDocument(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);  // 외부 입력 XML 보안 처리
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);  // DTD 차단 (XXE 방지)
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new IllegalArgumentException("Invalid order XML: " + e.getMessage(), e);
        }
    }

    private static List<Element> children(Element parent, String tag) {  // 바로 아래 자식 요소만
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals(tag)) {
                result.add(e);
            }
        }
        return result;
    }

    private static String text(Element parent, String tag) {  // 필수 값, 비어 있으면 실패
        List<Element> found = children(parent, tag);
        String value = found.isEmpty() ? "" : found.get(0).getTextContent().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Missing <" + tag + "> in <" + parent.getTagName() + ">");
        }
        return value;
    }

    private static String price(Element item) {  // 숫자만 허용
        String price = text(item, "PRICE");
        if (!price.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("PRICE is not a number: " + price + " (ITEM_ID " + text(item, "ITEM_ID") + ")");
        }
        return price;
    }
}
