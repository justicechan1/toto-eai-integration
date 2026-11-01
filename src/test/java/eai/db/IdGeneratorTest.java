package eai.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** IdGenerator 테스트. ORDER_ID, SHIPMENT_ID의 DB CHECK 제약(대문자 1자 + 숫자 3자리)에 맞는 ID를 만드는지 확인한다. */
class IdGeneratorTest {

    @Test
    void startsFromA001() {  // 저장된 주문이 없으면 A001
        assertEquals("A001", IdGenerator.next(null));
    }

    @Test
    void incrementsNumber() {  // 숫자 증가, 자릿수 유지
        assertEquals("A002", IdGenerator.next("A001"));
        assertEquals("B100", IdGenerator.next("B099"));
    }

    @Test
    void movesToNextLetterAfter999() {  // 999 다음은 다음 문자 001
        assertEquals("B001", IdGenerator.next("A999"));
    }

    @Test
    void failsAfterZ999() {  // 더 만들 수 없으면 실패
        assertThrows(IllegalStateException.class, () -> IdGenerator.next("Z999"));
    }

    @Test
    void rejectsInvalidFormat() {  // 형식이 다른 ID는 거부
        assertThrows(IllegalArgumentException.class, () -> IdGenerator.next("ORDER1"));
    }
}
