package com.nottrilix.api;

import com.nottrilix.api.dto.CatalogItem;
import com.nottrilix.api.dto.PurchaseRequest;
import com.nottrilix.api.dto.PurchaseResponse;
import com.nottrilix.api.dto.TopUpRequest;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherCheckDto;
import com.nottrilix.api.dto.WalletDto;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.Channel;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.ShopService;
import com.nottrilix.service.VoucherCheckService;
import com.nottrilix.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Prodajni kanal; sve operacije se odnose na prijavljenog kupca (uloga CUSTOMER). */
@RestController
@RequestMapping("/api/shop")
@Tag(name = "Shop", description = "Prodajni kanal - web aplikacija za kupce (uloga CUSTOMER)")
public class ShopController {

    private static final String CUSTOMER_TOPUP_NOTE = "Uplata karticom (simulirano)";

    private final CatalogService catalogService;
    private final ShopService shopService;
    private final WalletService walletService;
    private final VoucherCheckService voucherCheckService;

    public ShopController(CatalogService catalogService, ShopService shopService, WalletService walletService,
                          VoucherCheckService voucherCheckService) {
        this.catalogService = catalogService;
        this.shopService = shopService;
        this.walletService = walletService;
        this.voucherCheckService = voucherCheckService;
    }

    @GetMapping("/catalog")
    @Operation(summary = "Katalog bonova (dostupno / rasprodano)")
    public List<CatalogItem> catalog() {
        return catalogService.catalog();
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kupnja bona za prijavljenog kupca, naplata s walleta - vraća bon (serijski + kod) i novo stanje walleta")
    public PurchaseResponse purchase(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PurchaseRequest request) {
        return shopService.purchase(user.id(), request.productId(), Channel.WEB);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Kupnje prijavljenog kupca")
    public List<TransactionDto> transactions(@AuthenticationPrincipal AuthUser user) {
        return shopService.customerTransactions(user.id());
    }

    @GetMapping("/vouchers/{serial}/check")
    @Operation(summary = "Provjera iskorištenosti vlastitog bona kod pružatelja usluge (fiktivni upit)")
    public VoucherCheckDto checkVoucher(@AuthenticationPrincipal AuthUser user, @PathVariable String serial) {
        return voucherCheckService.checkForCustomer(user.id(), serial);
    }

    @GetMapping("/wallet")
    @Operation(summary = "Wallet prijavljenog kupca: stanje i povijest promjena")
    public WalletDto wallet(@AuthenticationPrincipal AuthUser user) {
        return walletService.wallet(user.id());
    }

    @PostMapping("/wallet/topup")
    @Operation(summary = "Uplata na wallet (simulirano plaćanje karticom)")
    public WalletDto topUp(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody TopUpRequest request) {
        return walletService.topUp(user.id(), request.amountEur(), CUSTOMER_TOPUP_NOTE);
    }
}
