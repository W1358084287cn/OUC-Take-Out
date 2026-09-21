package edu.ouc.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import edu.ouc.common.R;
import edu.ouc.dto.CashierTodayDto;
import edu.ouc.entity.Orders;
import edu.ouc.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/cashier")
@Slf4j
@RequiredArgsConstructor
public class CashierController {

    private final IOrderService orderService;

    @GetMapping("/today")
    public R<CashierTodayDto> today() {
        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LambdaQueryWrapper<Orders> lqw = new LambdaQueryWrapper<>();
        lqw.ge(Orders::getOrderTime, todayStart);
        List<Orders> orderList = orderService.list(lqw);

        int total = orderList.size();
        BigDecimal totalAmount = BigDecimal.ZERO;
        int completed = 0;
        BigDecimal completedAmount = BigDecimal.ZERO;
        int making = 0;
        int refund = 0;
        BigDecimal refundAmount = BigDecimal.ZERO;

        for (Orders order : orderList) {
            totalAmount = totalAmount.add(order.getAmount());
            Integer status = order.getStatus();
            if (status == 3) {
                completed++;
                completedAmount = completedAmount.add(order.getAmount());
            } else if (status == 2) {
                making++;
            } else if (status == 6) {
                refund++;
                refundAmount = refundAmount.add(order.getAmount());
            }
        }

        CashierTodayDto dto = new CashierTodayDto();
        dto.setTotalOrders(total);
        dto.setTotalAmount(totalAmount);
        dto.setCompletedOrders(completed);
        dto.setCompletedAmount(completedAmount);
        dto.setMakingOrders(making);
        dto.setRefundOrders(refund);
        dto.setRefundAmount(refundAmount);

        log.info("收银台今日统计: total={}, amount={}, completed={}", total, totalAmount, completed);
        return R.success(dto);
    }
}