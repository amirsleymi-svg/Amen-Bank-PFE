package com.amenbank.service.impl;

import com.amenbank.dto.request.CreditApplicationRequest;
import com.amenbank.dto.request.CreditSimulationRequest;
import com.amenbank.dto.response.CreditApplicationResponse;
import com.amenbank.dto.response.CreditSimulationResponse;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.entity.CreditApplication;
import com.amenbank.entity.User;
import com.amenbank.enums.CreditStatus;
import com.amenbank.enums.CreditType;
import com.amenbank.exception.BusinessException;
import com.amenbank.exception.ResourceNotFoundException;
import com.amenbank.repository.CreditApplicationRepository;
import com.amenbank.repository.UserRepository;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CreditService {

    private final CreditApplicationRepository creditApplicationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final EmailService emailService;

    // ─── Interest rates per credit type ───────────────────────────────
    private BigDecimal getAnnualRate(CreditType type) {
        return switch (type) {
            case PERSONAL   -> new BigDecimal("0.0895"); // 8.95%
            case MORTGAGE   -> new BigDecimal("0.0725"); // 7.25%
            case AUTO       -> new BigDecimal("0.0850"); // 8.50%
            case BUSINESS   -> new BigDecimal("0.0950"); // 9.50%
            case STUDENT    -> new BigDecimal("0.0620"); // 6.20%
        };
    }

    // ─── Simulate credit ──────────────────────────────────────────────
    public CreditSimulationResponse simulate(CreditSimulationRequest request) {
        BigDecimal annualRate = getAnnualRate(request.getCreditType());
        BigDecimal monthlyRate = annualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);
        int n = request.getDurationMonths();
        BigDecimal principal = request.getAmount();

        // Monthly payment formula: P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(monthlyRate);
        BigDecimal onePlusRPowN = onePlusR.pow(n, new MathContext(15));
        BigDecimal numerator = principal.multiply(monthlyRate).multiply(onePlusRPowN);
        BigDecimal denominator = onePlusRPowN.subtract(BigDecimal.ONE);
        BigDecimal monthlyPayment = numerator.divide(denominator, 3, RoundingMode.HALF_UP);

        BigDecimal totalRepayment = monthlyPayment.multiply(BigDecimal.valueOf(n));
        BigDecimal totalInterest = totalRepayment.subtract(principal);

        // Build amortization table
        List<CreditSimulationResponse.AmortizationEntry> table = new ArrayList<>();
        BigDecimal remainingBalance = principal;

        for (int month = 1; month <= n; month++) {
            BigDecimal interestForMonth = remainingBalance.multiply(monthlyRate).setScale(3, RoundingMode.HALF_UP);
            BigDecimal principalForMonth = monthlyPayment.subtract(interestForMonth);
            remainingBalance = remainingBalance.subtract(principalForMonth);
            if (remainingBalance.compareTo(BigDecimal.ZERO) < 0) remainingBalance = BigDecimal.ZERO;

            table.add(CreditSimulationResponse.AmortizationEntry.builder()
                    .month(month)
                    .payment(monthlyPayment)
                    .principal(principalForMonth.setScale(3, RoundingMode.HALF_UP))
                    .interest(interestForMonth)
                    .remainingBalance(remainingBalance.setScale(3, RoundingMode.HALF_UP))
                    .build());
        }

        return CreditSimulationResponse.builder()
                .requestedAmount(principal)
                .durationMonths(n)
                .annualRate(annualRate)
                .monthlyPayment(monthlyPayment)
                .totalRepayment(totalRepayment.setScale(3, RoundingMode.HALF_UP))
                .totalInterest(totalInterest.setScale(3, RoundingMode.HALF_UP))
                .creditType(request.getCreditType())
                .amortizationTable(table)
                .build();
    }

    // ─── Apply for credit ─────────────────────────────────────────────
    @Transactional
    public CreditApplicationResponse apply(CreditApplicationRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (creditApplicationRepository.existsByUserIdAndCreditTypeAndStatus(
                userId, request.getCreditType(), CreditStatus.PENDING)) {
            throw new BusinessException(
                    "You already have a pending " + request.getCreditType() + " credit application",
                    "DUPLICATE_APPLICATION");
        }

        BigDecimal annualRate = getAnnualRate(request.getCreditType());
        CreditSimulationRequest simulationRequest = new CreditSimulationRequest();
        simulationRequest.setAmount(request.getAmount());
        simulationRequest.setDurationMonths(request.getDurationMonths());
        simulationRequest.setCreditType(request.getCreditType());
        CreditSimulationResponse sim = simulate(simulationRequest);

        CreditApplication application = CreditApplication.builder()
                .user(user)
                .amount(request.getAmount())
                .durationMonths(request.getDurationMonths())
                .annualRate(annualRate)
                .monthlyPayment(sim.getMonthlyPayment())
                .totalCost(sim.getTotalRepayment())
                .purpose(request.getPurpose())
                .creditType(request.getCreditType())
                .status(CreditStatus.PENDING)
                .build();

        CreditApplication saved = creditApplicationRepository.save(application);

        // Notify admins
        emailService.notifyAdminsNewCreditApplication(
                user.getFullName(),
                user.getEmail(),
                request.getCreditType().name(),
                request.getAmount());
        auditService.log("CREDIT_APPLIED", "CreditApplication", saved.getId(),
                user.getEmail(), "Credit application: " + request.getCreditType() + " " + request.getAmount() + " TND");

        return toCreditResponse(saved);
    }

    // ─── Get user applications ────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<CreditApplicationResponse> getUserApplications(Long userId, int page, int size) {
        Page<CreditApplication> cPage = creditApplicationRepository
                .findByUserIdOrderByCreatedAtDesc(userId,
                        PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.<CreditApplicationResponse>builder()
                .content(cPage.getContent().stream().map(this::toCreditResponse).collect(Collectors.toList()))
                .page(cPage.getNumber()).size(cPage.getSize())
                .totalElements(cPage.getTotalElements())
                .totalPages(cPage.getTotalPages())
                .first(cPage.isFirst()).last(cPage.isLast())
                .build();
    }

    // ─── Admin: update status ─────────────────────────────────────────
    @Transactional
    public CreditApplicationResponse updateStatus(Long applicationId, CreditStatus newStatus,
                                                   String reason, Long adminId) {
        CreditApplication app = creditApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("CreditApplication", applicationId));

        if (app.getStatus() == CreditStatus.APPROVED || app.getStatus() == CreditStatus.REJECTED) {
            throw new BusinessException("Application already processed", "ALREADY_PROCESSED");
        }

        if (newStatus == CreditStatus.REJECTED && (reason == null || reason.isBlank())) {
            throw new BusinessException("Rejection reason is required when rejecting a credit application", "REJECTION_REASON_REQUIRED");
        }

        app.setStatus(newStatus);
        app.setRejectionReason(newStatus == CreditStatus.REJECTED ? reason : null);
        app.setReviewedAt(java.time.LocalDateTime.now());

        if (newStatus == CreditStatus.DISBURSED) {
            app.setDisbursedAt(java.time.LocalDateTime.now());
        }

        CreditApplication saved = creditApplicationRepository.save(app);

        emailService.sendCreditStatusUpdate(
                app.getUser().getEmail(), app.getUser().getFullName(),
                app.getCreditType().name(), newStatus.name(), reason
        );

        auditService.log("CREDIT_STATUS_UPDATED", "CreditApplication", applicationId,
                null, newStatus + " - " + reason);

        return toCreditResponse(saved);
    }

    public CreditApplicationResponse toCreditResponse(CreditApplication c) {
        return CreditApplicationResponse.builder()
                .id(c.getId())
                .amount(c.getAmount())
                .durationMonths(c.getDurationMonths())
                .annualRate(c.getAnnualRate())
                .monthlyPayment(c.getMonthlyPayment())
                .totalCost(c.getTotalCost())
                .purpose(c.getPurpose())
                .creditType(c.getCreditType())
                .status(c.getStatus())
                .rejectionReason(c.getRejectionReason())
                .createdAt(c.getCreatedAt())
                .reviewedAt(c.getReviewedAt())
                .build();
    }
}
