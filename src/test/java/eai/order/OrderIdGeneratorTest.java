package eai.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** OrderIdGenerator 테스트. DB CHECK 제약(대문자 1자 + 숫자 3자리)에 맞는 ID를 만드는지 확인한다. */
class OrderIdGeneratorTest {

    @Test
    void startsFromA001() {  // 저장된 주문이 없으면 A001
        assertEquals("A001", OrderIdGenerator.next(null));
    }

    @Test
    void incrementsNumber() {  // 숫자 증가, 자릿수 유지
        assertEquals("A002", OrderIdGenerator.next("A001"));
        assertEquals("B100", OrderIdGenerator.next("B099"));
    }

    @Test
    void movesToNextLetterAfter999() {  // 999 다음은 다음 문자 001
        assertEquals("B001", OrderIdGenerator.next("A999"));
    }

    @Test
    void failsAfterZ999() {  // 더 만들 수 없으면 실패
        assertThrows(IllegalStateException.class, () -> OrderIdGenerator.next("Z999"));
    }

    @Test
    void rejectsInvalidFormat() {  // 형식이 다른 ID는 거부
        assertThrows(IllegalArgumentException.class, () -> OrderIdGenerator.next("ORDER1"));
    }
}
