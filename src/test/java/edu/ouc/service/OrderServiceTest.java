package edu.ouc.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.ouc.dto.OrderDto;
import edu.ouc.entity.Orders;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单服务层单元测试 —— 验证分页、状态更新、订单查询等核心逻辑
 *
 * @since 2024/9/19
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderServiceTest {

    @Autowired
    private IOrderService orderService;

    // ==================== 分页查询测试 ====================

    @Test
    @Order(1)
    void testGetAllPageDefault() {
        Page<OrderDto> result = orderService.getAllPage(1L, 10L, null, null, null);
        assertNotNull(result);
        assertTrue(result.getTotal() > 0, "订单总数应大于0");
        assertTrue(result.getRecords().size() <= 10, "每页记录数不应超过10");
        System.out.println("服务层分页查询: total=" + result.getTotal()
                + ", current=" + result.getCurrent() + ", pages=" + result.getPages());
    }

    @Test
    @Order(2)
    void testGetAllPageRecordsMatchPageSize() {
        Page<OrderDto> result = orderService.getAllPage(1L, 3L, null, null, null);
        assertNotNull(result);
        assertTrue(result.getRecords().size() <= 3, "记录数不应超过pageSize=3");
        System.out.println("分页限制验证: pageSize=3, 实际返回=" + result.getRecords().size() + "条");
    }

    @Test
    @Order(3)
    void testGetAllPageNoOverlap() {
        Page<OrderDto> page1 = orderService.getAllPage(1L, 5L, null, null, null);
        Page<OrderDto> page2 = orderService.getAllPage(2L, 5L, null, null, null);
        if (!page1.getRecords().isEmpty() && !page2.getRecords().isEmpty()) {
            Long firstIdPage1 = page1.getRecords().get(0).getId();
            Long firstIdPage2 = page2.getRecords().get(0).getId();
            assertNotEquals(firstIdPage1, firstIdPage2, "两页数据首条ID不应相同，分页修复有效");
            System.out.println("分页无重复: page1首条=" + firstIdPage1 + ", page2首条=" + firstIdPage2);
        }
    }

    // ==================== 状态更新测试 ====================

    @Test
    @Order(4)
    void testUpdateStatus() {
        Page<OrderDto> page = orderService.getAllPage(1L, 100L, null, null, null);
        OrderDto status2Order = page.getRecords().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .findFirst().orElse(null);
        if (status2Order == null) {
            System.out.println("无status=2订单，跳过状态更新测试");
            return;
        }
        Long orderId = status2Order.getId();
        System.out.println("测试订单: id=" + orderId + ", 初始status=" + status2Order.getStatus());

        Orders update = new Orders();
        update.setId(orderId);
        update.setStatus(3);
        Boolean result = orderService.update(update);
        assertTrue(result, "订单状态更新应成功");
        System.out.println("状态更新成功: 2→3");

        Orders verify = orderService.getOrderById(orderId);
        assertNotNull(verify);
        assertEquals(3, verify.getStatus(), "更新后状态应为3");
        System.out.println("状态验证通过: status=" + verify.getStatus());
    }

    // ==================== 订单详情查询测试 ====================

    @Test
    @Order(5)
    void testGetOrderById() {
        Page<OrderDto> page = orderService.getAllPage(1L, 1L, null, null, null);
        if (!page.getRecords().isEmpty()) {
            Long orderId = page.getRecords().get(0).getId();
            Orders order = orderService.getOrderById(orderId);
            assertNotNull(order);
            assertEquals(orderId, order.getId());
            assertNotNull(order.getNumber(), "订单号不应为空");
            assertNotNull(order.getStatus(), "订单状态不应为空");
            System.out.println("订单详情查询: id=" + orderId + ", number=" + order.getNumber()
                    + ", status=" + order.getStatus() + ", amount=" + order.getAmount());
        }
    }

    // ==================== 待处理订单数测试 ====================

    @Test
    @Order(6)
    void testGetNewOrderCount() {
        Integer count = orderService.getNewOrderCount();
        assertNotNull(count);
        assertTrue(count >= 0, "待处理订单数应 >= 0");
        System.out.println("待处理订单数(status=2或3): " + count);
    }
}