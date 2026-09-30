package com.nottrilix.api;

import com.nottrilix.api.dto.ConsentDto;
import com.nottrilix.api.dto.ConsentRequest;
import com.nottrilix.api.dto.CustomerDataExport;
import com.nottrilix.api.dto.EraseResult;
import com.nottrilix.api.dto.PrivacyOverview;
import com.nottrilix.auth.AuthUser;
import com.nottrilix.privacy.PrivacyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nottrilix.domain.ConsentType;

/** The customer's rights over their own data (role CUSTOMER): consents, overview, export, conversation erasure. */
@RestController
@RequestMapping("/api/shop/privacy")
@Tag(name = "Privacy", description = "GDPR: privole kupca, pregled i izvoz podataka, brisanje razgovora na zahtjev")
public class PrivacyController {

    private final PrivacyService privacyService;

    public PrivacyController(PrivacyService privacyService) {
        this.privacyService = privacyService;
    }

    @GetMapping
    @Operation(summary = "Što sustav čuva o kupcu: rokovi, privole, broj razgovora")
    public PrivacyOverview overview(@AuthenticationPrincipal AuthUser user) {
        return privacyService.overview(user);
    }

    @PostMapping("/consent")
    @Operation(summary = "Kupac daje privolu (npr. VOICE za glasovni razgovor)")
    public ConsentDto consent(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ConsentRequest request) {
        return privacyService.giveConsent(user, request.type());
    }

    @DeleteMapping("/consent/{type}")
    @Operation(summary = "Kupac povlači privolu")
    public PrivacyOverview withdraw(@AuthenticationPrincipal AuthUser user, @PathVariable ConsentType type) {
        privacyService.withdrawConsent(user, type);
        return privacyService.overview(user);
    }

    @GetMapping("/export")
    @Operation(summary = "Izvoz svih podataka kupca (JSON za preuzimanje)")
    public ResponseEntity<CustomerDataExport> export(@AuthenticationPrincipal AuthUser user) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"nottrilix-moji-podaci.json\"")
                .body(privacyService.export(user));
    }

    @PostMapping("/erase-conversations")
    @Operation(summary = "Brisanje razgovora kupca (poruke se brišu, ostali tragovi anonimiziraju)")
    public EraseResult erase(@AuthenticationPrincipal AuthUser user) {
        return privacyService.eraseConversations(user);
    }
}
