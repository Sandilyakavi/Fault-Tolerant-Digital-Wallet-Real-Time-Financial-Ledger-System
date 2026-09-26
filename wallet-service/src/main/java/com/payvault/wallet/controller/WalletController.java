package com.payvault.wallet.controller;

import com.payvault.wallet.dto.WalletRequest;
import com.payvault.wallet.dto.WalletResponse;
import com.payvault.wallet.service.WalletService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(
            @Valid @RequestBody WalletRequest request) {

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(walletService.createWallet(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WalletResponse> getWalletById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                walletService.getWalletById(id)
        );
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<WalletResponse> getWalletByUserId(
            @PathVariable Long userId) {

        return ResponseEntity.ok(
                walletService.getWalletByUserId(userId)
        );
    }

    @GetMapping
    public ResponseEntity<List<WalletResponse>> getAllWallets() {

        return ResponseEntity.ok(
                walletService.getAllWallets()
        );
    }

    @PostMapping("/user/{userId}/credit")
    public ResponseEntity<WalletResponse> credit(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {

        return ResponseEntity.ok(
                walletService.credit(userId, amount)
        );
    }

    @PostMapping("/user/{userId}/debit")
    public ResponseEntity<WalletResponse> debit(
            @PathVariable Long userId,
            @RequestParam BigDecimal amount) {

        return ResponseEntity.ok(
                walletService.debit(userId, amount)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWallet(
            @PathVariable Long id) {

        walletService.deleteWallet(id);

        return ResponseEntity.noContent().build();
    }
}