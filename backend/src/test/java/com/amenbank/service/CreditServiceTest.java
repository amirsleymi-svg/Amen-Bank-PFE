package com.amenbank.service;

import com.amenbank.dto.request.CreditSimulationRequest;
import com.amenbank.dto.response.CreditSimulationResponse;
import com.amenbank.enums.CreditType;
import com.amenbank.repository.CreditApplicationRepository;
import com.amenbank.repository.UserRepository;
import com.amenbank.service.impl.CreditService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CreditService — amortization calculation")
class CreditServiceTest {

    @Mock CreditApplicationRepository creditApplicationRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @Mock EmailService emailService;

    @InjectMocks CreditService creditService;

    // ─── Simulate: basic contract ─────────────────────────────────
    @Test
    @DisplayName("simulate() returns non-null with correct field counts")
    void simulate_returnsValidResponse() {
        CreditSimulationRequest req = new CreditSimulationRequest();
        req.setAmount(new BigDecimal("50000"));
        req.setDurationMonths(60);
        req.setCreditType(CreditType.PERSONAL);

        CreditSimulationResponse res = creditService.simulate(req);

        assertThat(res).isNotNull();
        assertThat(res.getMonthlyPayment()).isPositive();
        assertThat(res.getTotalRepayment()).isGreaterThan(res.getRequestedAmount());
        assertThat(res.getTotalInterest()).isPositive();
        assertThat(res.getAmortizationTable()).hasSize(60);
    }

    @Test
    @DisplayName("simulate() amortization table last balance ≈ 0")
    void simulate_lastAmortizationBalance_isZero() {
        CreditSimulationRequest req = new CreditSimulationRequest();
        req.setAmount(new BigDecimal("100000"));
        req.setDurationMonths(120);
        req.setCreditType(CreditType.MORTGAGE);

        CreditSimulationResponse res = creditService.simulate(req);

        BigDecimal lastBalance = res.getAmortizationTable()
                .get(res.getAmortizationTable().size() - 1)
                .getRemainingBalance();

        assertThat(lastBalance.abs()).isLessThanOrEqualTo(new BigDecimal("1.000"));
    }

    @ParameterizedTest(name = "{0} type → rate {1}")
    @CsvSource({
        "PERSONAL, 0.0895",
        "MORTGAGE, 0.0725",
        "AUTO,     0.0850",
        "BUSINESS, 0.0950",
        "STUDENT,  0.0620"
    })
    @DisplayName("simulate() uses correct annual rate per credit type")
    void simulate_correctRate(String type, String expectedRate) {
        CreditSimulationRequest req = new CreditSimulationRequest();
        req.setAmount(new BigDecimal("20000"));
        req.setDurationMonths(24);
        req.setCreditType(CreditType.valueOf(type));

        CreditSimulationResponse res = creditService.simulate(req);

        assertThat(res.getAnnualRate())
                .isEqualByComparingTo(new BigDecimal(expectedRate));
    }

    @Test
    @DisplayName("simulate() monthly payment > 0 for all credit types")
    void simulate_allTypes_positivePayment() {
        for (CreditType type : CreditType.values()) {
            CreditSimulationRequest req = new CreditSimulationRequest();
            req.setAmount(new BigDecimal("30000"));
            req.setDurationMonths(36);
            req.setCreditType(type);

            CreditSimulationResponse res = creditService.simulate(req);
            assertThat(res.getMonthlyPayment())
                    .as("Monthly payment for %s should be positive", type)
                    .isPositive();
        }
    }

    @Test
    @DisplayName("simulate() total repayment = monthlyPayment × durationMonths (approx)")
    void simulate_totalRepaymentConsistency() {
        CreditSimulationRequest req = new CreditSimulationRequest();
        req.setAmount(new BigDecimal("10000"));
        req.setDurationMonths(12);
        req.setCreditType(CreditType.STUDENT);

        CreditSimulationResponse res = creditService.simulate(req);

        BigDecimal computed = res.getMonthlyPayment()
                .multiply(BigDecimal.valueOf(12))
                .setScale(3, RoundingMode.HALF_UP);

        assertThat(res.getTotalRepayment())
                .isCloseTo(computed, within(new BigDecimal("0.010")));
    }
}
