package com.payvault.wallet;

import com.payvault.wallet.client.UserClient;
import com.payvault.wallet.dto.WalletRequest;
import com.payvault.wallet.dto.WalletResponse;
import com.payvault.wallet.entity.Wallet;
import com.payvault.wallet.exception.InsufficientBalanceException;
import com.payvault.wallet.exception.WalletNotFoundException;
import com.payvault.wallet.repository.WalletRepository;
import com.payvault.wallet.service.WalletServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WalletServiceApplicationTests {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserClient userClient;

    private WalletServiceImpl walletService;

    @BeforeEach
    void setUp() {

        MockitoAnnotations.openMocks(this);

        walletService = new WalletServiceImpl(
                walletRepository,
                userClient
        );
    }

    // ---------------------------------------------------------
    // 1. Create Wallet - Success
    // ---------------------------------------------------------

    @Test
    void createWalletShouldCreateSuccessfully() {

        WalletRequest request =
                new WalletRequest(2L);

        when(userClient.getUserById(2L))
                .thenReturn(null);

        when(walletRepository.existsByUserId(2L))
                .thenReturn(false);

        Wallet savedWallet =
                new Wallet(2L);

        savedWallet.setId(10L);

        when(walletRepository.save(any(Wallet.class)))
                .thenReturn(savedWallet);

        WalletResponse response =
                walletService.createWallet(request);

        assertNotNull(response);

        assertEquals(
                10L,
                response.getId()
        );

        assertEquals(
                2L,
                response.getUserId()
        );

        verify(userClient)
                .getUserById(2L);

        verify(walletRepository)
                .existsByUserId(2L);

        verify(walletRepository)
                .save(any(Wallet.class));
    }

    // ---------------------------------------------------------
    // 2. Create Wallet - Invalid User
    // ---------------------------------------------------------

    @Test
    void createWalletShouldRejectInvalidUser() {

        WalletRequest request =
                new WalletRequest(999L);

        when(userClient.getUserById(999L))
                .thenThrow(
                        new RuntimeException(
                                "User not found"
                        )
                );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> walletService.createWallet(request)
                );

        assertEquals(
                "User not found with id: 999",
                exception.getMessage()
        );

        verify(userClient)
                .getUserById(999L);

        verify(walletRepository, never())
                .existsByUserId(anyLong());

        verify(walletRepository, never())
                .save(any(Wallet.class));
    }

    // ---------------------------------------------------------
    // 3. Create Wallet - Duplicate Wallet
    // ---------------------------------------------------------

    @Test
    void createWalletShouldRejectDuplicateWallet() {

        WalletRequest request =
                new WalletRequest(2L);

        when(userClient.getUserById(2L))
                .thenReturn(null);

        when(walletRepository.existsByUserId(2L))
                .thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> walletService.createWallet(request)
                );

        assertEquals(
                "Wallet already exists for user id: 2",
                exception.getMessage()
        );

        verify(userClient)
                .getUserById(2L);

        verify(walletRepository)
                .existsByUserId(2L);

        verify(walletRepository, never())
                .save(any(Wallet.class));
    }

    // ---------------------------------------------------------
    // 4. Get Wallet By ID - Success
    // ---------------------------------------------------------

    @Test
    void getWalletByIdShouldReturnWallet() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        when(walletRepository.findById(10L))
                .thenReturn(Optional.of(wallet));

        WalletResponse response =
                walletService.getWalletById(10L);

        assertNotNull(response);

        assertEquals(
                10L,
                response.getId()
        );

        assertEquals(
                2L,
                response.getUserId()
        );

        verify(walletRepository)
                .findById(10L);
    }

    // ---------------------------------------------------------
    // 5. Get Wallet By ID - Not Found
    // ---------------------------------------------------------

    @Test
    void getWalletByIdShouldThrowWhenNotFound() {

        when(walletRepository.findById(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception =
                assertThrows(
                        WalletNotFoundException.class,
                        () -> walletService.getWalletById(999L)
                );

        assertEquals(
                "Wallet not found with id: 999",
                exception.getMessage()
        );

        verify(walletRepository)
                .findById(999L);
    }

    // ---------------------------------------------------------
    // 6. Get Wallet By User ID
    // ---------------------------------------------------------

    @Test
    void getWalletByUserIdShouldReturnWallet() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        when(walletRepository.findByUserId(2L))
                .thenReturn(Optional.of(wallet));

        WalletResponse response =
                walletService.getWalletByUserId(2L);

        assertNotNull(response);

        assertEquals(
                10L,
                response.getId()
        );

        assertEquals(
                2L,
                response.getUserId()
        );

        verify(walletRepository)
                .findByUserId(2L);
    }

    // ---------------------------------------------------------
    // 7. Get All Wallets
    // ---------------------------------------------------------

    @Test
    void getAllWalletsShouldReturnWallets() {

        Wallet wallet1 =
                new Wallet(2L);

        wallet1.setId(10L);

        Wallet wallet2 =
                new Wallet(3L);

        wallet2.setId(11L);

        when(walletRepository.findAll())
                .thenReturn(
                        List.of(wallet1, wallet2)
                );

        List<WalletResponse> response =
                walletService.getAllWallets();

        assertNotNull(response);

        assertEquals(
                2,
                response.size()
        );

        assertEquals(
                2L,
                response.get(0).getUserId()
        );

        assertEquals(
                3L,
                response.get(1).getUserId()
        );

        verify(walletRepository)
                .findAll();
    }

    // ---------------------------------------------------------
    // 8. Credit Wallet
    // ---------------------------------------------------------

    @Test
    void creditShouldIncreaseBalance() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        wallet.setBalance(
                new BigDecimal("500.00")
        );

        when(walletRepository.findByUserId(2L))
                .thenReturn(Optional.of(wallet));

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        WalletResponse response =
                walletService.credit(
                        2L,
                        new BigDecimal("200.00")
                );

        assertEquals(
                new BigDecimal("700.00"),
                response.getBalance()
        );

        verify(walletRepository)
                .findByUserId(2L);

        verify(walletRepository)
                .save(wallet);
    }

    // ---------------------------------------------------------
    // 9. Debit Wallet
    // ---------------------------------------------------------

    @Test
    void debitShouldDecreaseBalance() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        wallet.setBalance(
                new BigDecimal("500.00")
        );

        when(walletRepository.findByUserId(2L))
                .thenReturn(Optional.of(wallet));

        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        WalletResponse response =
                walletService.debit(
                        2L,
                        new BigDecimal("200.00")
                );

        assertEquals(
                new BigDecimal("300.00"),
                response.getBalance()
        );

        verify(walletRepository)
                .findByUserId(2L);

        verify(walletRepository)
                .save(wallet);
    }

    // ---------------------------------------------------------
    // 10. Debit - Insufficient Balance
    // ---------------------------------------------------------

    @Test
    void debitShouldRejectInsufficientBalance() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        wallet.setBalance(
                new BigDecimal("100.00")
        );

        when(walletRepository.findByUserId(2L))
                .thenReturn(Optional.of(wallet));

        InsufficientBalanceException exception =
                assertThrows(
                        InsufficientBalanceException.class,
                        () -> walletService.debit(
                                2L,
                                new BigDecimal("200.00")
                        )
                );

        assertEquals(
                "Insufficient wallet balance",
                exception.getMessage()
        );

        verify(walletRepository)
                .findByUserId(2L);

        verify(walletRepository, never())
                .save(any(Wallet.class));
    }

    // ---------------------------------------------------------
    // 11. Amount Validation
    // ---------------------------------------------------------

    @Test
    void creditShouldRejectInvalidAmount() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> walletService.credit(
                                2L,
                                BigDecimal.ZERO
                        )
                );

        assertEquals(
                "Amount must be greater than zero",
                exception.getMessage()
        );

        verifyNoInteractions(
                walletRepository
        );
    }

    // ---------------------------------------------------------
    // 12. Delete Wallet
    // ---------------------------------------------------------

    @Test
    void deleteWalletShouldDeleteSuccessfully() {

        Wallet wallet =
                new Wallet(2L);

        wallet.setId(10L);

        when(walletRepository.findById(10L))
                .thenReturn(Optional.of(wallet));

        walletService.deleteWallet(10L);

        verify(walletRepository)
                .findById(10L);

        verify(walletRepository)
                .delete(wallet);
    }

    // ---------------------------------------------------------
    // 13. Delete Wallet - Not Found
    // ---------------------------------------------------------

    @Test
    void deleteWalletShouldThrowWhenNotFound() {

        when(walletRepository.findById(999L))
                .thenReturn(Optional.empty());

        WalletNotFoundException exception =
                assertThrows(
                        WalletNotFoundException.class,
                        () -> walletService.deleteWallet(999L)
                );

        assertEquals(
                "Wallet not found with id: 999",
                exception.getMessage()
        );

        verify(walletRepository)
                .findById(999L);

        verify(walletRepository, never())
                .delete(any(Wallet.class));
    }
}