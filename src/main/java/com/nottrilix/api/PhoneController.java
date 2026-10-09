package com.nottrilix.api;

import com.nottrilix.api.dto.PhoneCallEnd;
import com.nottrilix.api.dto.PhoneCallStart;
import com.nottrilix.api.dto.PhoneIdentity;
import com.nottrilix.api.dto.PhoneTurn;
import com.nottrilix.phone.PhoneSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The phone channel's door into the system, used by the agent application (role AGENT, its own key): who is calling,
 * what was said, when the call ended. The customer never calls these endpoints; the agent does, on the customer's behalf.
 */
@RestController
@RequestMapping("/api/phone")
@Validated
@Tag(name = "Phone", description = "Telefonski kanal (uloga AGENT): prepoznavanje pozivatelja po broju, bilježenje turnova i kraja poziva")
public class PhoneController {

    private static final String CALL_ID = "[A-Za-z0-9_-]{1,128}";   // Infobip call ids are 68 characters

    private final PhoneSessionService phone;

    public PhoneController(PhoneSessionService phone) {
        this.phone = phone;
    }

    @PostMapping("/calls")
    @Operation(summary = "Dolazni poziv: kupac po broju pozivatelja -> token kupca za agenta (ili nepoznat pozivatelj)")
    public PhoneIdentity identify(@Valid @RequestBody PhoneCallStart call) {
        return phone.identify(call);
    }

    @PostMapping("/calls/{callId}/turns")
    @Operation(summary = "Jedan turn poziva (transkript i odgovor) u audit trag i okvir razgovora")
    public void turn(@PathVariable @Pattern(regexp = CALL_ID) String callId, @Valid @RequestBody PhoneTurn turn) {
        phone.recordTurn(callId, turn);
    }

    @PostMapping("/calls/{callId}/end")
    @Operation(summary = "Kraj poziva: token kupca se povlači, poziv se zatvara u audit tragu")
    public void end(@PathVariable @Pattern(regexp = CALL_ID) String callId, @RequestBody PhoneCallEnd end) {
        phone.end(callId, end);
    }
}
