package com.amenbank.service.impl;

import com.amenbank.dto.request.StandingOrderRequest;
import com.amenbank.dto.response.StandingOrderResponse;
import com.amenbank.entity.*;
import com.amenbank.enums.StandingOrderStatus;
import com.amenbank.exception.*;
import com.amenbank.repository.*;
import com.amenbank.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StandingOrderService {

    private final StandingOrderRepository standingOrderRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final AccountService accountService;

    @Transactional
    public StandingOrderResponse create(StandingOrderRequest req, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Account fromAccount = accountRepository
                .findActiveAccountByIdAndUserId(req.getFromAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", req.getFromAccountId()));

        StandingOrder so = StandingOrder.builder()
                .user(user)
                .fromAccount(fromAccount)
                .toIban(req.getToIban())
                .toName(req.getToName())
                .amount(req.getAmount())
                .label(req.getLabel())
                .frequency(req.getFrequency())
                .startDate(req.getStartDate())
                .endDate(req.getEndDate())
                .nextRunDate(req.getStartDate())
                .status(StandingOrderStatus.ACTIVE)
                .build();

        StandingOrder saved = standingOrderRepository.save(so);
        auditService.log("STANDING_ORDER_CREATED", "StandingOrder", saved.getId(),
                user.getEmail(), req.getFrequency() + " " + req.getAmount() + " TND");
        return toResponse(saved);
    }

    public List<StandingOrderResponse> getUserOrders(Long userId) {
        return standingOrderRepository.findByUserId(userId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public void cancel(Long id, Long userId) {
        StandingOrder so = standingOrderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("StandingOrder", id));
        if (!so.getUser().getId().equals(userId))
            throw new ForbiddenException("Not your standing order");
        so.setStatus(StandingOrderStatus.CANCELLED);
        standingOrderRepository.save(so);
    }

    @Scheduled(cron = "0 0 6 * * *") // daily at 06:00
    @Transactional
    public void executeScheduled() {
        List<StandingOrder> due = standingOrderRepository
                .findByStatusAndNextRunDateLessThanEqual(StandingOrderStatus.ACTIVE, LocalDate.now());
        for (StandingOrder so : due) {
            try {
                accountService.debitAccount(
                        so.getFromAccount(), so.getAmount(),
                        so.getToIban(), so.getToName(),
                        com.amenbank.enums.TransactionCategory.STANDING_ORDER,
                        so.getLabel() != null ? so.getLabel() : "Ordre permanent"
                );
                so.setLastRunDate(LocalDate.now());
                so.setNextRunDate(nextDate(so));
                if (so.getEndDate() != null && so.getNextRunDate().isAfter(so.getEndDate()))
                    so.setStatus(StandingOrderStatus.EXPIRED);
                standingOrderRepository.save(so);
            } catch (Exception e) {
                log.error("Failed to execute standing order {}: {}", so.getId(), e.getMessage());
            }
        }
    }

    private LocalDate nextDate(StandingOrder so) {
        return switch (so.getFrequency()) {
            case DAILY     -> so.getNextRunDate().plusDays(1);
            case WEEKLY    -> so.getNextRunDate().plusWeeks(1);
            case MONTHLY   -> so.getNextRunDate().plusMonths(1);
            case QUARTERLY -> so.getNextRunDate().plusMonths(3);
            case YEARLY    -> so.getNextRunDate().plusYears(1);
        };
    }

    private StandingOrderResponse toResponse(StandingOrder s) {
        return StandingOrderResponse.builder()
                .id(s.getId())
                .fromAccountNumber(s.getFromAccount().getAccountNumber())
                .toIban(s.getToIban()).toName(s.getToName())
                .amount(s.getAmount()).currency(s.getCurrency())
                .label(s.getLabel()).frequency(s.getFrequency())
                .startDate(s.getStartDate()).endDate(s.getEndDate())
                .nextRunDate(s.getNextRunDate()).lastRunDate(s.getLastRunDate())
                .status(s.getStatus()).createdAt(s.getCreatedAt())
                .build();
    }
}
