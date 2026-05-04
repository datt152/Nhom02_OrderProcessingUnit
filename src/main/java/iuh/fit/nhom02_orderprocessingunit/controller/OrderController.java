package iuh.fit.nhom02_orderprocessingunit.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import java.util.Map;
import java.util.UUID;
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/checkout")
public class OrderController {

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient; // Spring Boot 3.2+

    public OrderController(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.create();
    }

    @PostMapping
    public String checkout(@RequestParam String userId) {
        String cartKey = "cart:" + userId;

        // 1. Lấy cart từ Data Grid
        Map<Object, Object> cart = redisTemplate.opsForHash().entries(cartKey);
        if (cart.isEmpty()) return "Giỏ hàng trống!";

        // 2. Xử lý trừ kho bằng cách gọi qua Inventory PU (PU4)
        for (Map.Entry<Object, Object> entry : cart.entrySet()) {
            String productId = entry.getKey().toString();
            int quantity = Integer.parseInt(entry.getValue().toString());

            try {
                // Gọi HTTP sang Service kho (Port 8084)
                String inventoryUrl = "http://172.16.71.20:8084/stock/deduct?productId=" + productId + "&amount=" + quantity;
                restClient.post()
                        .uri(inventoryUrl)
                        .retrieve()
                        .toBodilessEntity(); // Kì vọng trả về 200 OK
            } catch (Exception e) {
                // Lỗi 400 Bad Request (Hết hàng)
                return "Sản phẩm " + productId + " đã hết hàng!";
            }
        }

        // 3. Tạo order trên Data Grid
        String orderId = UUID.randomUUID().toString();
        String orderData = "{\"userId\": \"" + userId + "\", \"status\": \"SUCCESS\"}";
        redisTemplate.opsForValue().set("order:" + orderId, orderData);

        // 4. Xóa giỏ hàng sau khi chốt đơn thành công
        redisTemplate.delete(cartKey);

        // 5. Publish Event (Optional - Giả lập)
        System.out.println("Bắn message vào RabbitMQ: Đơn hàng " + orderId + " đã tạo!");

        return "Đặt hàng thành công! Mã đơn: " + orderId;
    }
}