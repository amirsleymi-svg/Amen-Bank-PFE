package com.amenbank.service.impl;

import com.amenbank.dto.request.TransferRequest;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.dto.response.TransferResponse;
import com.amenbank.entity.*;
import com.amenbank.enums.*;
import com.amenbank.exception.*;
import com.amenbank.repository.*;
import com.amenbank.security.totp.TotpService;
import com.amenbank.service.AuditService;
import com.amenbank.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private final TransferRepository transferRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TotpService totpService;
    private final AccountService accountService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final TransactionRepository transactionRepository;

    // ─── Initiate single transfer ─────────────────────────────────────
    @Transactional
    public TransferResponse initiateTransfer(TransferRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Account fromAccount = accountRepository
                .findActiveAccountByIdAndUserId(request.getFromAccountId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getFromAccountId()));

        // Verify TOTP
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("2FA must be enabled to perform transfers", "TOTP_REQUIRED");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
            throw new InvalidTotpException();
        }

        // Validate amount and limits
        validateTransferLimits(fromAccount, request.getAmount());

        // Create transfer record
        Transfer transfer = Transfer.builder()
                .fromAccount(fromAccount)
                .toIban(request.getToIban())
                .toName(request.getToName())
                .amount(request.getAmount())
                .currency("TND")
                .label(request.getLabel())
                .status(TransferStatus.PROCESSING)
                .totpVerified(true)
                .scheduledDate(request.getScheduledDate())
                .build();

        // Scheduled transfer
        if (request.getScheduledDate() != null && request.getScheduledDate().isAfter(LocalDate.now())) {
            transfer.setStatus(TransferStatus.PENDING);
            Transfer saved = transferRepository.save(transfer);
            auditService.log("TRANSFER_SCHEDULED", "Transfer", saved.getId(), user.getEmail(),
                    "Transfer of " + request.getAmount() + " TND scheduled for " + request.getScheduledDate());
            return toTransferResponse(saved);
        }

        // Execute immediately
        return executeTransfer(transfer, user);
    }

    // ─── Execute transfer ─────────────────────────────────────────────
    @Transactional
    public TransferResponse executeTransfer(Transfer transfer, User user) {
        try {
            Transaction debitTx = accountService.debitAccount(
                    transfer.getFromAccount(),
                    transfer.getAmount(),
                    transfer.getToIban(),
                    transfer.getToName(),
                    TransactionCategory.TRANSFER,
                    transfer.getLabel() != null ? transfer.getLabel() : "Transfer to " + transfer.getToName()
            );

            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setDebitTxId(debitTx.getId());
            transfer.setProcessedAt(LocalDateTime.now());
            Transfer saved = transferRepository.save(transfer);

            // Send notification email
            emailService.sendTransferNotification(
                    user.getEmail(), user.getFullName(),
                    debitTx.getTransactionRef(),
                    transfer.getAmount(), transfer.getCurrency()
            );

            auditService.log("TRANSFER_COMPLETED", "Transfer", saved.getId(), user.getEmail(),
                    "Transfer of " + transfer.getAmount() + " TND to " + transfer.getToIban());

            return toTransferResponse(saved);

        } catch (InsufficientFundsException | AccountFrozenException e) {
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(e.getMessage());
            transferRepository.save(transfer);
            throw e;
        }
    }

    // ─── Batch CSV transfer ───────────────────────────────────────────
    @Transactional
    public Map<String, Object> processBatchCsv(MultipartFile file, Long userId,
                                                Long fromAccountId, String totpCode) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CSV file is required", "CSV_FILE_REQUIRED");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        Account fromAccount = accountRepository
                .findActiveAccountByIdAndUserId(fromAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", fromAccountId));

        // Verify TOTP once for entire batch
        if (!Boolean.TRUE.equals(user.getTotpEnabled())) {
            throw new BusinessException("2FA must be enabled to perform transfers", "TOTP_REQUIRED");
        }
        if (!totpService.verifyCode(user.getTotpSecret(), totpCode)) {
            throw new InvalidTotpException();
        }

        List<Map<String, Object>> results = new ArrayList<>();
        List<Transfer> validTransfers = new ArrayList<>();
        int errorCount = 0;

        try (CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader()
                .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            int rowNum = 1;
            for (CSVRecord record : parser) {
                rowNum++;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("row", rowNum);

                try {
                    String iban = record.get("iban").trim();
                    String name = record.get("name").trim();
                    BigDecimal amount = new BigDecimal(record.get("amount").trim());
                    String label = record.isMapped("label") ? record.get("label").trim() : "";

                    if (iban.isEmpty() || name.isEmpty()) throw new BusinessException("IBAN and name required");
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException("Amount must be positive");

                    Transfer t = Transfer.builder()
                            .fromAccount(fromAccount)
                            .toIban(iban)
                            .toName(name)
                            .amount(amount)
                            .currency("TND")
                            .label(label)
                            .status(TransferStatus.PENDING)
                            .totpVerified(true)
                            .build();

                    validTransfers.add(t);
                    row.put("status", "VALID");
                    row.put("iban", iban);
                    row.put("name", name);
                    row.put("amount", amount);

                } catch (Exception e) {
                    errorCount++;
                    row.put("status", "ERROR");
                    row.put("error", e.getMessage());
                }
                results.add(row);
            }
        } catch (Exception e) {
            throw new BusinessException("Failed to parse CSV: " + e.getMessage(), "CSV_PARSE_ERROR");
        }

        BigDecimal totalAmount = validTransfers.stream()
                .map(Transfer::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalRows", results.size(),
                "validRows", validTransfers.size(),
                "errorRows", errorCount,
                "totalAmount", totalAmount,
                "rows", results,
                "pendingTransfers", validTransfers.stream().map(this::toTransferResponse).collect(Collectors.toList())
        );
    }

    // ─── Get transfers ────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<TransferResponse> getUserTransfers(Long userId, int page, int size) {
        Page<Transfer> tPage = transferRepository.findByFromAccountUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));

        return PageResponse.<TransferResponse>builder()
                .content(tPage.getContent().stream().map(this::toTransferResponse).collect(Collectors.toList()))
                .page(tPage.getNumber()).size(tPage.getSize())
                .totalElements(tPage.getTotalElements())
                .totalPages(tPage.getTotalPages())
                .first(tPage.isFirst()).last(tPage.isLast())
                .build();
    }

    // ─── Cancel transfer ──────────────────────────────────────────────
    @Transactional
    public void cancelTransfer(Long transferId, Long userId) {
        Transfer transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new ResourceNotFoundException("Transfer", transferId));

        if (!transfer.getFromAccount().getUser().getId().equals(userId)) {
            throw new ForbiddenException("Not your transfer");
        }
        if (transfer.getStatus() != TransferStatus.PENDING) {
            throw new BusinessException("Only pending transfers can be cancelled", "CANNOT_CANCEL");
        }

        transfer.setStatus(TransferStatus.CANCELLED);
        transferRepository.save(transfer);
    }

    // ─── Validate limits ──────────────────────────────────────────────
    private void validateTransferLimits(Account account, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        BigDecimal todayDebits = transactionRepository.sumTodayDebits(account.getId(), today);
        if (todayDebits.add(amount).compareTo(account.getDailyLimit()) > 0) {
            throw new BusinessException("Daily transfer limit exceeded", "DAILY_LIMIT_EXCEEDED");
        }

        BigDecimal monthDebits = transactionRepository.sumMonthDebits(account.getId(), today);
        if (monthDebits.add(amount).compareTo(account.getMonthlyLimit()) > 0) {
            throw new BusinessException("Monthly transfer limit exceeded", "MONTHLY_LIMIT_EXCEEDED");
        }
    }

    public TransferResponse toTransferResponse(Transfer t) {
        return TransferResponse.builder()
                .id(t.getId())
                .fromAccountNumber(t.getFromAccount() != null ? t.getFromAccount().getAccountNumber() : null)
                .toIban(t.getToIban())
                .toName(t.getToName())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .label(t.getLabel())
                .status(t.getStatus())
                .scheduledDate(t.getScheduledDate())
                .processedAt(t.getProcessedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
