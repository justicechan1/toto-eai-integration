package eai.order;

import java.util.regex.Pattern;

public final class OrderIdGenerator {

    private static final Pattern ID = Pattern.compile("[A-Z][0-9]{3}");  // DB CHECK 제약과 같은 형식

    private OrderIdGenerator() {
    }

    // 다음 ID 생성: A001 -> A002, A999 -> B001, 이전 ID가 없으면 A001
    public static String next(String lastId) {
        if (lastId == null) {
            return "A001";
        }
        if (!ID.matcher(lastId).matches()) {
            throw new IllegalArgumentException("Invalid ID format: " + lastId);
        }
        char letter = lastId.charAt(0);
        int number = Integer.parseInt(lastId.substring(1));
        if (number < 999) {
            return letter + String.format("%03d", number + 1);
        }
        if (letter == 'Z') {  // Z999 이후는 형식상 만들 수 없음
            throw new IllegalStateException("ID range exhausted after " + lastId);
        }
        return (char) (letter + 1) + "001";
    }
}
