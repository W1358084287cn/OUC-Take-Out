package edu.ouc.controller;

import edu.ouc.common.R;
import edu.ouc.entity.ShoppingCart;
import edu.ouc.service.ISharedCartService;
import edu.ouc.service.impl.ShoppingCartServiceImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Author: Sihang Xie
 * Description: 购物车控制层
 * Date: 2022/10/24 15:09
 * Version: 0.0.1
 * Modified By:
 */
@Slf4j
@RestController
@RequestMapping("/shoppingCart")
@RequiredArgsConstructor
public class ShoppingCartController {

    private final ShoppingCartServiceImpl shoppingCartService;
    private final ISharedCartService sharedCartService;

    // 添加菜品到购物车
    @PostMapping("/add")
    public R<ShoppingCart> add(@RequestBody ShoppingCart shoppingCart,
                                @RequestParam(required = false) String tableId) {
        log.info("添加购物车: dishId={}, setmealId={}, flavor={}, tableId={}", shoppingCart.getDishId(), shoppingCart.getSetmealId(), shoppingCart.getDishFlavor(), tableId);
        ShoppingCart result = shoppingCartService.add(shoppingCart);
        if (tableId != null && !tableId.isEmpty()) {
            sharedCartService.addToSharedCart(tableId, result);
        }
        return R.success(result);
    }

    // 查询当前用户的购物车中所有信息
    @GetMapping("/list")
    public R<List<ShoppingCart>> list(@RequestParam(required = false) String tableId) {
        List<ShoppingCart> userCart = shoppingCartService.getUserList();
        if (tableId != null && !tableId.isEmpty()) {
            List<ShoppingCart> sharedCart = sharedCartService.getSharedCart(tableId);
            List<ShoppingCart> merged = new ArrayList<>(userCart);
            merged.addAll(sharedCart);
            return R.success(merged);
        }
        return R.success(userCart);
    }

    // 清空购物车
    @DeleteMapping("/clean")
    public R<String> clean(@RequestParam(required = false) String tableId) {
        log.info("清空购物车: tableId={}", tableId);
        if (shoppingCartService.clean()) {
            if (tableId != null && !tableId.isEmpty()) {
                sharedCartService.clearSharedCart(tableId);
            }
            return R.success("清空成功");
        }
        return R.error("清空失败");
    }

    // 购物车商品减一
    @PostMapping("/sub")
    public R<String> sub(@RequestBody ShoppingCart shoppingCart,
                          @RequestParam(required = false) String tableId) {
        log.info("购物车减一: dishId={}, setmealId={}, tableId={}", shoppingCart.getDishId(), shoppingCart.getSetmealId(), tableId);
        if (shoppingCartService.sub(shoppingCart)) {
            if (tableId != null && !tableId.isEmpty()) {
                sharedCartService.removeFromSharedCart(tableId, shoppingCart.getId());
            }
            return R.success("删除成功");
        }
        return R.error("删除失败");
    }
}