package edu.ouc.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.ouc.entity.ShoppingCart;
import edu.ouc.service.ISharedCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SharedCartServiceImpl implements ISharedCartService {

    private static final String CART_KEY_PREFIX = "shared_cart:table:";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void addToSharedCart(String tableId, ShoppingCart item) {
        String key = CART_KEY_PREFIX + tableId;
        try {
            String json = objectMapper.writeValueAsString(item);
            stringRedisTemplate.opsForList().rightPush(key, json);
            log.info("添加到共享购物车: tableId={}, dish={}", tableId, item.getName());
        } catch (JsonProcessingException e) {
            log.error("序列化购物车失败: {}", e.getMessage());
        }
    }

    @Override
    public List<ShoppingCart> getSharedCart(String tableId) {
        List<ShoppingCart> result = new ArrayList<>();
        String key = CART_KEY_PREFIX + tableId;
        List<String> items = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (items != null) {
            for (String json : items) {
                try {
                    result.add(objectMapper.readValue(json, ShoppingCart.class));
                } catch (JsonProcessingException e) {
                    log.error("反序列化购物车失败: {}", e.getMessage());
                }
            }
        }
        return result;
    }

    @Override
    public void removeFromSharedCart(String tableId, Long itemId) {
        String key = CART_KEY_PREFIX + tableId;
        List<String> items = stringRedisTemplate.opsForList().range(key, 0, -1);
        if (items != null) {
            for (String json : items) {
                try {
                    ShoppingCart cart = objectMapper.readValue(json, ShoppingCart.class);
                    if (cart.getId() != null && cart.getId().equals(itemId)) {
                        stringRedisTemplate.opsForList().remove(key, 0, json);
                        break;
                    }
                } catch (JsonProcessingException e) {
                    log.error("反序列化购物车失败: {}", e.getMessage());
                }
            }
        }
    }

    @Override
    public void clearSharedCart(String tableId) {
        String key = CART_KEY_PREFIX + tableId;
        stringRedisTemplate.delete(key);
        log.info("清空共享购物车: tableId={}", tableId);
    }
}