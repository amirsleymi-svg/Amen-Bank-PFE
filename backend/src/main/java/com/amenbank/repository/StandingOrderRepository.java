package com.amenbank.repository;

import com.amenbank.entity.StandingOrder;
import com.amenbank.enums.StandingOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface StandingOrderRepository extends JpaRepository<StandingOrder, Long> {
    List<StandingOrder> findByUserId(Long userId);
    List<StandingOrder> findByStatusAndNextRunDateLessThanEqual(StandingOrderStatus status, LocalDate date);
}
