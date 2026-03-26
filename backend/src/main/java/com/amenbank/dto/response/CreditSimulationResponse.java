package com.amenbank.dto.response;
import com.amenbank.enums.CreditType;
import lombok.*;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class CreditSimulationResponse {
    private BigDecimal requestedAmount;
    private Integer durationMonths;
    private BigDecimal annualRate;
    private BigDecimal monthlyPayment;
    private BigDecimal totalRepayment;
    private BigDecimal totalInterest;
    private CreditType creditType;
    private List<AmortizationEntry> amortizationTable;

    @Data @Builder
    public static class AmortizationEntry {
        private Integer month;
        private BigDecimal payment;
        private BigDecimal principal;
        private BigDecimal interest;
        private BigDecimal remainingBalance;
    }
}
