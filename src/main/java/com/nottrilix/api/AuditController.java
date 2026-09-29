package com.nottrilix.api;

import com.nottrilix.api.dto.AuditEventDto;
import com.nottrilix.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Audit trag agenta za podrsku (uloga ADMIN). */
@RestController
@RequestMapping("/api/admin/audit")
@Tag(name = "Audit", description = "Audit trag agenta: pozivi alata, dohvati iz baze znanja, turnovi razgovora")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Audit događaji, najnoviji prvi (opcionalno po razgovoru ili kupcu)")
    public List<AuditEventDto> events(@RequestParam(required = false) String conversationId,
                                      @RequestParam(required = false) Long customerId,
                                      @RequestParam(defaultValue = "200") int limit) {
        return auditService.events(conversationId, customerId, limit);
    }
}
