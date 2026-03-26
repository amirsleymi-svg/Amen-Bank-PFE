package com.amenbank.controller;

import com.amenbank.dto.request.TransferRequest;
import com.amenbank.dto.response.*;
import com.amenbank.service.impl.TransferService;
import com.amenbank.service.impl.UserDetailsServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
@Tag(name = "Transfers", description = "Wire transfers, batch CSV, standing orders")
@SecurityRequirement(name = "bearerAuth")
public class TransferController {

    private final TransferService transferService;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping
    @Operation(summary = "Initiate a wire transfer (requires TOTP)")
    @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
    public ResponseEntity<ApiResponse<TransferResponse>> initiate(
            @Valid @RequestBody TransferRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transfer initiated", transferService.initiateTransfer(request, user.getId())));
    }

    @GetMapping
    @Operation(summary = "Get all transfers for current user")
    @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
    public ResponseEntity<ApiResponse<PageResponse<TransferResponse>>> getTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Transfers loaded",
                transferService.getUserTransfers(user.getId(), page, size)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel a pending transfer")
    @PreAuthorize("hasAuthority('TRANSFER_CANCEL')")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        transferService.cancelTransfer(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok("Transfer cancelled"));
    }

    @PostMapping("/batch")
    @Operation(summary = "Upload CSV for batch transfers")
    @PreAuthorize("hasAuthority('TRANSFER_CREATE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadBatch(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long fromAccountId,
            @RequestParam String totpCode,
            @AuthenticationPrincipal UserDetails userDetails) {
        var user = userDetailsService.loadUserEntityByUsername(userDetails.getUsername());
        Map<String, Object> result = transferService.processBatchCsv(file, user.getId(), fromAccountId, totpCode);
        return ResponseEntity.ok(ApiResponse.ok("Batch processed", result));
    }

    @GetMapping("/batch/template")
    @Operation(summary = "Download CSV template for batch transfers")
    public ResponseEntity<byte[]> downloadTemplate() {
        String csv = "iban,name,amount,label\nTN5900400410000012345678901,John Doe,500.000,Monthly payment\n";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"transfer-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }
}
