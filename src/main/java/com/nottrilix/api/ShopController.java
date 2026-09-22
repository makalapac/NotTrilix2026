package com.nottrilix.api;

import com.nottrilix.api.dto.CatalogItem;
import com.nottrilix.api.dto.PurchaseRequest;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.domain.Channel;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shop")
@Tag(name = "Shop", description = "Prodajni kanal - web aplikacija za kupce")
public class ShopController {

    private final CatalogService catalogService;
    private final ShopService shopService;

    public ShopController(CatalogService catalogService, ShopService shopService) {
        this.catalogService = catalogService;
        this.shopService = shopService;
    }

    @GetMapping("/catalog")
    @Operation(summary = "Katalog bonova s dostupnom zalihom")
    public List<CatalogItem> catalog() {
        return catalogService.catalog();
    }

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Kupnja bona - vraća serijski broj i aktivacijski kod")
    public TransactionDto purchase(@Valid @RequestBody PurchaseRequest request) {
        return shopService.purchase(request.customerId(), request.productId(), Channel.WEB);
    }

    @GetMapping("/customers/{customerId}/transactions")
    @Operation(summary = "Kupnje jednog kupca (moje transakcije)")
    public List<TransactionDto> transactions(@PathVariable long customerId) {
        return shopService.customerTransactions(customerId);
    }
}
