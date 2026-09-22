package com.nottrilix.api;

import com.nottrilix.api.dto.CatalogItem;
import com.nottrilix.api.dto.PurchaseRequest;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.domain.Channel;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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

    private final CatalogService catalogService;
    private final ShopService shopService;

    public ShopController(CatalogService catalogService, ShopService shopService) {
        this.catalogService = catalogService;
        this.shopService = shopService;
    }

    @GetMapping("/catalog")
    @Operation(summary = "Katalog bonova (dostupno / rasprodano)")
    public List<CatalogItem> catalog() {
        return catalogService.catalog();
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kupnja bona za prijavljenog kupca - vraća serijski broj i aktivacijski kod")
    public TransactionDto purchase(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody PurchaseRequest request) {
        return shopService.purchase(user.id(), request.productId(), Channel.WEB);
    }

    @GetMapping("/transactions")
    @Operation(summary = "Kupnje prijavljenog kupca")
    public List<TransactionDto> transactions(@AuthenticationPrincipal AuthUser user) {
        return shopService.customerTransactions(user.id());
    }
}
