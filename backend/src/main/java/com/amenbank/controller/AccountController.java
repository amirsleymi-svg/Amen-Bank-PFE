package com.amenbank.controller;

import com.amenbank.dto.response.*;
import com.amenbank.enums.TransactionStatus;
import com.amenbank.enums.TransactionType;
import com.amenbank.service.impl.AccountService;
import com.amenbank.service.impl.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "Accounts", description = "Account management and transactions")
@SecurityRequirement(name = "bearerAuth")
public class AccountController {

    private final AccountService accountService;
    private final UserDetailsServiceImpl userDetailsService;

    @GetMapping
    @Operation(summary = "Get all accounts for current user")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Accounts loaded",
                accountService.getUserAccounts(user.getId())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get account by ID")
    @PreAuthorize("hasAuthority('ACCOUNT_READ')")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Account loaded",
                accountService.getAccount(id, user.getId())));
    }

    @GetMapping("/{id}/transactions")
    @Operation(summary = "Get paginated transactions with filters")
    @PreAuthorize("hasAuthority('TRANSACTION_READ')")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionStatus status,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Transactions loaded",
                accountService.getTransactions(id, user.getId(), from, to, type, status, page, size)));
    }

    @GetMapping("/{id}/transactions/export")
    @Operation(summary = "Export transactions as CSV")
    @PreAuthorize("hasAuthority('TRANSACTION_EXPORT')")
    public ResponseEntity<byte[]> exportCsv(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        byte[] csv = accountService.exportTransactionsCsv(id, user.getId(), from, to);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transactions.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
