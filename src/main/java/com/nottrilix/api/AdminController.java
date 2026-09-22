package com.nottrilix.api;

import com.nottrilix.api.dto.AdminTopUpRequest;
import com.nottrilix.api.dto.ResolveRequest;
import com.nottrilix.api.dto.StockRow;
import com.nottrilix.api.dto.TicketDto;
import com.nottrilix.api.dto.TicketRequest;
import com.nottrilix.api.dto.TransactionDto;
import com.nottrilix.api.dto.VoucherDto;
import com.nottrilix.api.dto.WalletDto;
import com.nottrilix.domain.Brand;
import com.nottrilix.domain.TransactionStatus;
import com.nottrilix.service.CatalogService;
import com.nottrilix.service.SupportService;
import com.nottrilix.service.SupportService.TransactionFilter;
import com.nottrilix.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Pregled transakcija i zaliha, podrska (povrat, blokada bona, prijave, wallet kupca)")
public class AdminController {

    private final SupportService supportService;
    private final CatalogService catalogService;
    private final WalletService walletService;

    public AdminController(SupportService supportService, CatalogService catalogService, WalletService walletService) {
        this.supportService = supportService;
        this.catalogService = catalogService;
        this.walletService = walletService;
    }

    @GetMapping("/transactions")
    @Operation(summary = "Sve transakcije (opcionalno filtrirane)")
    public List<TransactionDto> transactions(@RequestParam(required = false) TransactionStatus status,
                                             @RequestParam(required = false) Brand brand,
                                             @RequestParam(required = false) Long customerId) {
        return supportService.transactions(new TransactionFilter(status, brand, customerId));
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Jedna transakcija")
    public TransactionDto transaction(@PathVariable long id) {
        return supportService.transaction(id);
    }

    @PostMapping("/transactions/{id}/refund")
    @Operation(summary = "Povrat novca na wallet kupca (unutar roka, bon nije iskoristen) - bon se blokira")
    public TransactionDto refund(@PathVariable long id) {
        return supportService.refund(id);
    }

    @PostMapping("/vouchers/{serial}/block")
    @Operation(summary = "Blokada bona po serijskom broju")
    public VoucherDto blockVoucher(@PathVariable String serial) {
        return supportService.blockVoucher(serial);
    }

    @GetMapping("/stock")
    @Operation(summary = "Zaliha po proizvodu i statusu bonova")
    public List<StockRow> stock() {
        return catalogService.stock();
    }

    @GetMapping("/customers/{id}/wallet")
    @Operation(summary = "Wallet kupca: stanje i povijest promjena")
    public WalletDto customerWallet(@PathVariable long id) {
        return walletService.wallet(id);
    }

    @PostMapping("/customers/{id}/wallet/topup")
    @Operation(summary = "Uplata podrske na wallet kupca (s napomenom)")
    public WalletDto topUpCustomerWallet(@PathVariable long id, @Valid @RequestBody AdminTopUpRequest request) {
        return walletService.topUp(id, request.amountEur(), request.note());
    }

    @GetMapping("/tickets")
    @Operation(summary = "Prijave podrske")
    public List<TicketDto> tickets() {
        return supportService.tickets();
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nova prijava podrske")
    public TicketDto createTicket(@Valid @RequestBody TicketRequest request) {
        return supportService.createTicket(request.customerId(), request.transactionId(),
                request.subject(), request.description());
    }

    @PostMapping("/tickets/{id}/resolve")
    @Operation(summary = "Zatvaranje prijave s rjesenjem")
    public TicketDto resolveTicket(@PathVariable long id, @Valid @RequestBody ResolveRequest request) {
        return supportService.resolveTicket(id, request.resolution());
    }
}
