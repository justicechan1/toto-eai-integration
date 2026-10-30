package eai.order;

/**
 * 주문 상품 1개 (XML의 ITEM 요소). 아직 주문자 정보(이름, 주소)가 없는 상태다.
 * - 만드는 곳: OrderXmlParser가 ITEM 요소를 읽어서 생성
 * - 쓰는 곳: 주문자를 찾으면 Order로 합쳐지고, 못 찾으면 그대로 unmatchedItems에 담겨 보고됨
 */
public record OrderItem(String userId, String itemId, String itemName, String price) {
}
