package eai.order;

/**
 * 주문 1건 (ORDER_TB 한 행). 주문자 정보와 상품 정보를 합친 값 객체다.
 * - 만드는 곳: OrderXmlParser가 HEADER(이름, 주소)와 ITEM(상품, 가격)을 USER_ID로 합쳐서 생성
 * - 쓰는 곳: ORDER_TB 저장 (ORDER_ID, APPLICANT_KEY, STATUS는 저장 단계에서 채움)
 */
public record Order(String userId, String itemId, String name, String address, String itemName, String price) {
}
