package com.amenbank.controller;

import com.amenbank.dto.request.StandingOrderRequest;
import com.amenbank.dto.response.ApiResponse;
import com.amenbank.dto.response.StandingOrderResponse;
import com.amenbank.service.impl.StandingOrderService;
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

import java.util.List;

@RestController
@RequestMapping("/standing-orders")
@RequiredArgsConstructor
@Tag(name = "Standing Orders", description = "Recurring automatic transfers")
@SecurityRequirement(name = "bearerAuth")
public class StandingOrderController {

    private final StandingOrderService service;
    private final UserDetailsServiceImpl userDetailsService;

    @PostMapping
    @PreAuthorize("hasAuthority('STANDING_ORDER_CREATE')")
    @Operation(summary = "Create a new standing order")
    public ResponseEntity<ApiResponse<StandingOrderResponse>> create(
            @Valid @RequestBody StandingOrderRequest req,
            @AuthenticationPrincipal UserDetails ud) {
        var user = userDetailsService.loadUserEntityByUsername(ud.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Standing order created", service.create(req, user.getId())));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STANDING_ORDER_CREATE')")
    @Operation(summary = "List all standing orders")
    public ResponseEntity<ApiResponse<List<StandingOrderResponse>>> list(
            @AuthenticationPrincipal UserDetails ud) {
        var user = userDetailsService.loadUserEntityByUsername(ud.getUsername());
        return ResponseEntity.ok(ApiResponse.ok("Orders loaded", service.getUserOrders(user.getId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STANDING_ORDER_CANCEL')")
    @Operation(summary = "Cancel a standing order")
    public ResponseEntity<ApiResponse<Void>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails ud) {
        var user = userDetailsService.loadUserEntityByUsername(ud.getUsername());
        service.cancel(id, user.getId());
        return ResponseEntity.ok(ApiResponse.ok("Standing order cancelled"));
    }
}
