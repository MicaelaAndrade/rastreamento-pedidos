package dev.micaelaandrade.pedidos.order;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findAllByOrderByIdDesc();

    List<Order> findAllByStatusOrderByIdDesc(OrderStatus status);
}
