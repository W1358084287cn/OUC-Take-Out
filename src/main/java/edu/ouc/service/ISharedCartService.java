package edu.ouc.service;

import edu.ouc.entity.ShoppingCart;

import java.util.List;

public interface ISharedCartService {

    void addToSharedCart(String tableId, ShoppingCart item);

    List<ShoppingCart> getSharedCart(String tableId);

    void removeFromSharedCart(String tableId, Long itemId);

    void clearSharedCart(String tableId);
}