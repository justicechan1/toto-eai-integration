package eai.order;

import java.time.LocalDateTime;

public record SavedOrder(String orderId, Order order, LocalDateTime createTime) {
}
