package com.amenbank.service.impl;

import com.amenbank.dto.response.AccountResponse;
import com.amenbank.dto.response.PageResponse;
import com.amenbank.dto.response.TransactionResponse;
import com.amenbank.entity.Account;
import com.amenbank.entity.Transaction;
import com.amenbank.entity.User;
import com.amenbank.enums.*;
import com.amenbank.exception.*;
import com.amenbank.repository.AccountRepository;
import com.amenbank.repository.TransactionRepository;
import com.amenbank.repository.UserRepository;
import com.amenbank.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    // ─── Get all accounts for user ────────────────────────────────────
    @Transactional(readOnly = true)
    public List<AccountResponse> getUserAccounts(Long userId) {
        return accountRepository.findByUserId(userId)
                .stream()
                .map(this::toAccountResponse)
                .collect(Collectors.toList());
    }

    // ─── Get single account ───────────────────────────────────────────
    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId, Long userId) {
        Account account = accountRepository.findActiveAccountByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        return toAccountResponse(account);
    }

    // ─── Get transactions with filters ───────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactions(
            Long accountId, Long userId,
            LocalDate from, LocalDate to,
            TransactionType type, TransactionStatus status,
            int page, int size) {

        // Verify ownership
        accountRepository.findActiveAccountByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        Page<Transaction> txPage = transactionRepository.findFiltered(
                accountId, from, to, type, status,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );

        return PageResponse.<TransactionResponse>builder()
                .content(txPage.getContent().stream().map(this::toTxResponse).collect(Collectors.toList()))
                .page(txPage.getNumber())
                .size(txPage.getSize())
                .totalElements(txPage.getTotalElements())
                .totalPages(txPage.getTotalPages())
                .first(txPage.isFirst())
                .last(txPage.isLast())
                .build();
    }

    // ─── Export transactions as CSV ───────────────────────────────────
    @Transactional(readOnly = true)
    public byte[] exportTransactionsCsv(Long accountId, Long userId,
                                         LocalDate from, LocalDate to) {
        accountRepository.findActiveAccountByIdAndUserId(accountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        Page<Transaction> all = transactionRepository.findFiltered(
                accountId, from, to, null, null,
                PageRequest.of(0, Integer.MAX_VALUE)
        );

        StringBuilder csv = new StringBuilder();
        csv.append("Date,Ref,Type,Category,Amount,Currency,Balance After,Label,Status,Counterpart\n");

        for (Transaction tx : all.getContent()) {
            csv.append(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s\n",
                    tx.getValueDate(), tx.getTransactionRef(),
                    tx.getType(), tx.getCategory(),
                    tx.getAmount(), tx.getCurrency(),
                    tx.getBalanceAfter(),
                    escCsv(tx.getLabel()), tx.getStatus(),
                    escCsv(tx.getCounterpartName())
            ));
        }
        return csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ─── Create account (internal) ────────────────────────────────────
    @Transactional
    public AccountResponse createAccount(Long userId, AccountType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String accountNumber = generateAccountNumber();
        String iban = generateIban(accountNumber);

        Account account = Account.builder()
                .user(user)
                .accountNumber(accountNumber)
                .iban(iban)
                .accountType(type)
                .currency("TND")
                .balance(BigDecimal.ZERO)
                .availableBalance(BigDecimal.ZERO)
                .status(AccountStatus.ACTIVE)
                .build();

        Account saved = accountRepository.save(account);
        auditService.log("ACCOUNT_CREATED", "Account", saved.getId(),
                user.getEmail(), "New " + type + " account created");
        return toAccountResponse(saved);
    }

    // ─── Internal: debit account ──────────────────────────────────────
    @Transactional
    public Transaction debitAccount(Account account, BigDecimal amount,
                                     String counterpartIban, String counterpartName,
                                     TransactionCategory category, String label) {
        if (!account.isActive()) throw new AccountFrozenException();
        if (account.getAvailableBalance().compareTo(amount) < 0) throw new InsufficientFundsException();

        BigDecimal newBalance = account.getBalance().subtract(amount);
        BigDecimal newAvailable = account.getAvailableBalance().subtract(amount);

        account.setBalance(newBalance);
        account.setAvailableBalance(newAvailable);
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .transactionRef(UUID.randomUUID().toString())
                .account(account)
                .type(TransactionType.DEBIT)
                .category(category)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceAfter(newBalance)
                .counterpartIban(counterpartIban)
                .counterpartName(counterpartName)
                .label(label)
                .status(TransactionStatus.COMPLETED)
                .valueDate(LocalDate.now())
                .processedAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }

    // ─── Internal: credit account ─────────────────────────────────────
    @Transactional
    public Transaction creditAccount(Account account, BigDecimal amount,
                                      String counterpartIban, String counterpartName,
                                      TransactionCategory category, String label) {
        BigDecimal newBalance = account.getBalance().add(amount);
        BigDecimal newAvailable = account.getAvailableBalance().add(amount);

        account.setBalance(newBalance);
        account.setAvailableBalance(newAvailable);
        accountRepository.save(account);

        Transaction tx = Transaction.builder()
                .transactionRef(UUID.randomUUID().toString())
                .account(account)
                .type(TransactionType.CREDIT)
                .category(category)
                .amount(amount)
                .currency(account.getCurrency())
                .balanceAfter(newBalance)
                .counterpartIban(counterpartIban)
                .counterpartName(counterpartName)
                .label(label)
                .status(TransactionStatus.COMPLETED)
                .valueDate(LocalDate.now())
                .processedAt(LocalDateTime.now())
                .build();

        return transactionRepository.save(tx);
    }

    // ─── Helpers ──────────────────────────────────────────────────────
    private String generateAccountNumber() {
        String num;
        do {
            num = "TN" + String.format("%016d", (long)(Math.random() * 1_000_000_000_000_000L));
        } while (accountRepository.existsByAccountNumber(num));
        return num;
    }

    private String generateIban(String accountNumber) {
        return "TN59" + accountNumber.replaceAll("[^0-9]", "").substring(0, 20);
    }

    private String escCsv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    public AccountResponse toAccountResponse(Account a) {
        return AccountResponse.builder()
                .id(a.getId())
                .accountNumber(a.getAccountNumber())
                .iban(a.getIban())
                .accountType(a.getAccountType())
                .currency(a.getCurrency())
                .balance(a.getBalance())
                .availableBalance(a.getAvailableBalance())
                .status(a.getStatus())
                .dailyLimit(a.getDailyLimit())
                .monthlyLimit(a.getMonthlyLimit())
                .openedAt(a.getOpenedAt())
                .build();
    }

    public TransactionResponse toTxResponse(Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .type(t.getType())
                .category(t.getCategory())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .balanceAfter(t.getBalanceAfter())
                .label(t.getLabel())
                .status(t.getStatus())
                .counterpartIban(t.getCounterpartIban())
                .counterpartName(t.getCounterpartName())
                .valueDate(t.getValueDate())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
