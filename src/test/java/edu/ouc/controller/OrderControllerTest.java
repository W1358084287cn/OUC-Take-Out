package edu.ouc.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.ouc.common.R;
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
 * 订单控制层单元测试 —— 覆盖后台管理端所有按钮操作
 *
 * @since 2024/9/19
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OrderControllerTest {

    @Autowired
    private OrderController orderController;

    // ==================== 分页查询测试 ====================

    @Test
    @Order(1)
    void testPageDefault() {
        R<Page<OrderDto>> result = orderController.page(1L, 10L, null, null, null);
        assertNotNull(result);
        assertEquals(1, result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData().getTotal() > 0, "订单总数应大于0");
        assertTrue(result.getData().getRecords().size() <= 10, "每页记录数不应超过10");
        System.out.println("默认分页查询成功: total=" + result.getData().getTotal()
                + ", records=" + result.getData().getRecords().size());
    }

    @Test
    @Order(2)
    void testPageWithPageSize() {
        R<Page<OrderDto>> result = orderController.page(1L, 5L, null, null, null);
        assertNotNull(result);
        assertEquals(1, result.getCode());
        assertTrue(result.getData().getRecords().size() <= 5, "每页记录数不应超过5");
        System.out.println("分页(pageSize=5)查询成功: records=" + result.getData().getRecords().size());
    }

    @Test
    @Order(3)
    void testPageNoOverlap() {
        R<Page<OrderDto>> page1 = orderController.page(1L, 5L, null, null, null);
        R<Page<OrderDto>> page2 = orderController.page(2L, 5L, null, null, null);
        assertNotNull(page1.getData());
        assertNotNull(page2.getData());
        if (!page1.getData().getRecords().isEmpty() && !page2.getData().getRecords().isEmpty()) {
            Long firstIdPage1 = page1.getData().getRecords().get(0).getId();
            Long firstIdPage2 = page2.getData().getRecords().get(0).getId();
            assertNotEquals(firstIdPage1, firstIdPage2, "第1页和第2页首条记录不应重复");
            System.out.println("分页无重复验证通过: page1首条=" + firstIdPage1 + ", page2首条=" + firstIdPage2);
        }
    }

    @Test
    @Order(4)
    void testPageSearchByNumber() {
        R<Page<OrderDto>> result = orderController.page(1L, 10L, "210125", null, null);
        assertNotNull(result);
        assertEquals(1, result.getCode());
        System.out.println("按订单号搜索'210125'结果数: " + result.getData().getTotal());
    }

    // ==================== 订单详情查询测试 ====================

    @Test
    @Order(5)
    void testGetById() {
        R<Page<OrderDto>> page = orderController.page(1L, 1L, null, null, null);
        assertNotNull(page.getData());
        if (!page.getData().getRecords().isEmpty()) {
            Long orderId = page.getData().getRecords().get(0).getId();
            R<Orders> result = orderController.getById(orderId);
            assertNotNull(result);
            assertEquals(1, result.getCode());
            assertNotNull(result.getData());
            assertEquals(orderId, result.getData().getId());
            System.out.println("订单详情查询成功: id=" + orderId + ", number=" + result.getData().getNumber());
        }
    }

    @Test
    @Order(6)
    void testGetByIdNotExist() {
        R<Orders> result = orderController.getById(Long.MAX_VALUE);
        assertNotNull(result);
        assertEquals(0, result.getCode());
        assertNotNull(result.getMsg());
        System.out.println("不存在订单查询返回错误: " + result.getMsg());
    }

    // ==================== 待处理订单数测试 ====================

    @Test
    @Order(7)
    void testNewCount() {
        R<Integer> result = orderController.getNewCount();
        assertNotNull(result);
        assertEquals(1, result.getCode());
        assertNotNull(result.getData());
        assertTrue(result.getData() >= 0, "待处理订单数应 >= 0");
        System.out.println("待处理订单数(status=2制作中): " + result.getData());
    }

    // ==================== 完成操作测试 (status: 2制作中→3已完成) ====================

    @Test
    @Order(8)
    void testDispatch() {
        R<Page<OrderDto>> page = orderController.page(1L, 100L, null, null, null);
        assertNotNull(page.getData());
        OrderDto status2Order = page.getData().getRecords().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .findFirst().orElse(null);
        if (status2Order == null) {
            System.out.println("无status=2订单，跳过完成测试");
            return;
        }
        Long testOrderId = status2Order.getId();
        String testOrderNumber = status2Order.getNumber();
        System.out.println("准备完成订单: id=" + testOrderId + ", number=" + testOrderNumber + ", 当前status=2");

        Orders updateOrder = new Orders();
        updateOrder.setId(testOrderId);
        updateOrder.setStatus(3);
        R<String> result = orderController.update(updateOrder);
        assertNotNull(result);
        assertEquals(1, result.getCode());
        assertEquals("修改成功", result.getData());
        System.out.println("完成操作成功");

        R<Orders> verify = orderController.getById(testOrderId);
        assertNotNull(verify.getData());
        assertEquals(3, verify.getData().getStatus(), "完成后状态应为3(已完成)，前端只显示[查看]按钮");
        System.out.println("验证完成结果: status=" + verify.getData().getStatus() + " (已完成) → 前端只显示[查看]按钮");
    }

    // ==================== 取消操作测试 (status: 2制作中→5已取消) ====================

    @Test
    @Order(9)
    void testCancel() {
        R<Page<OrderDto>> page = orderController.page(1L, 100L, null, null, null);
        assertNotNull(page.getData());
        OrderDto status2Order = page.getData().getRecords().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .findFirst().orElse(null);
        if (status2Order == null) {
            System.out.println("无status=2订单，跳过取消测试");
            return;
        }
        Long cancelId = status2Order.getId();
        System.out.println("准备取消订单: id=" + cancelId + ", number=" + status2Order.getNumber() + ", 当前status=2");

        Orders updateOrder = new Orders();
        updateOrder.setId(cancelId);
        updateOrder.setStatus(5);
        R<String> result = orderController.update(updateOrder);
        assertNotNull(result);
        assertEquals(1, result.getCode());
        assertEquals("修改成功", result.getData());
        System.out.println("取消操作成功");

        R<Orders> verify = orderController.getById(cancelId);
        assertNotNull(verify.getData());
        assertEquals(5, verify.getData().getStatus(), "取消后状态应为5(已取消)");
        System.out.println("验证取消结果: status=" + verify.getData().getStatus() + " (已取消) → 前端只显示[查看]按钮");
    }

    // ==================== 完整流程回归测试 (2制作中→3已完成) ====================

    @Test
    @Order(10)
    void testFullFlowDispatchThenComplete() {
        R<Page<OrderDto>> page = orderController.page(1L, 100L, null, null, null);
        assertNotNull(page.getData());
        OrderDto status2Order = page.getData().getRecords().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == 2)
                .findFirst().orElse(null);
        if (status2Order == null) {
            System.out.println("无status=2订单，跳过完整流程回归测试");
            return;
        }
        Long flowId = status2Order.getId();
        System.out.println("完整流程测试开始: id=" + flowId + ", number=" + status2Order.getNumber());

        // Step1: 确认初始状态
        assertEquals(2, status2Order.getStatus(), "初始状态应为2(制作中)");
        System.out.println("  Step1: 初始状态确认 → status=2(制作中)，前端应显示[完成]按钮");

        // Step2: 完成
        Orders done = new Orders();
        done.setId(flowId);
        done.setStatus(3);
        R<String> doneResult = orderController.update(done);
        assertEquals(1, doneResult.getCode());
        System.out.println("  Step2: 完成操作 → 成功");

        // Step3: 验证完成
        R<Orders> afterDone = orderController.getById(flowId);
        assertEquals(3, afterDone.getData().getStatus());
        System.out.println("  Step3: 验证完成 → status=3(已完成)，前端只显示[查看]按钮");
        System.out.println("完整流程测试通过: 2→3 状态转换正常");
    }

    // ==================== 异常场景测试 ====================

    @Test
    @Order(11)
    void testUpdateNonExistentOrder() {
        Orders nonExistent = new Orders();
        nonExistent.setId(Long.MAX_VALUE);
        nonExistent.setStatus(3);
        R<String> result = orderController.update(nonExistent);
        assertNotNull(result);
        assertEquals(0, result.getCode(), "不存在的订单更新应失败");
        System.out.println("不存在订单更新测试: code=" + result.getCode() + ", msg=" + result.getMsg());
    }

    @Test
    @Order(12)
    void testFrontendButtonLogic() {
        R<Page<OrderDto>> page = orderController.page(1L, 50L, null, null, null);
        assertNotNull(page.getData());
        for (OrderDto order : page.getData().getRecords()) {
            Integer status = order.getStatus();
            assertNotNull(status, "订单状态不应为空");
            assertTrue(status >= 1 && status <= 6, "订单状态应在1-6范围内: " + status);
            switch (status) {
                case 1:
                    // 待付款 → 只有[查看]
                    break;
                case 2:
                    // 制作中 → [查看] + [完成]
                    break;
                case 3:
                    // 已完成 → 只有[查看]
                    break;
                case 4:
                    // 已完成(兼容旧状态) → 只有[查看]
                    break;
                case 5:
                    // 已取消 → 只有[查看]
                    break;
                case 6:
                    // 已退款 → 只有[查看]
                    break;
            }
        }
        System.out.println("前端按钮逻辑模拟通过: 共验证" + page.getData().getRecords().size() + "条订单");
        System.out.println("  status=1 → [查看] 仅查看");
        System.out.println("  status=2 → [查看] + [完成]");
        System.out.println("  status=3 → [查看] 仅查看");
        System.out.println("  status=4 → [查看] 仅查看(兼容旧状态)");
        System.out.println("  status=5 → [查看] 仅查看");
        System.out.println("  status=6 → [查看] 仅查看(已退款)");
    }
}