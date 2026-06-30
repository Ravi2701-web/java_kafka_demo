package com.ecom.repository;

import com.ecom.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderId(String orderId);

    @Transactional
    @Modifying
    @Query("UPDATE Order o SET o.status = :status WHERE o.orderId = :orderId")
    int updateStatusByOrderId(String orderId, com.ecom.model.OrderStatus status);
}
