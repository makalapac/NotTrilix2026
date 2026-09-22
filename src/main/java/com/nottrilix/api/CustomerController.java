package com.nottrilix.api;

import com.nottrilix.api.dto.CustomerDto;
import com.nottrilix.service.ShopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Kupci (u demu sluzi i kao 'prijava' u shop)")
public class CustomerController {

    private final ShopService shopService;

    public CustomerController(ShopService shopService) {
        this.shopService = shopService;
    }

    @GetMapping
    @Operation(summary = "Popis kupaca")
    public List<CustomerDto> list() {
        return shopService.customers();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Jedan kupac")
    public CustomerDto get(@PathVariable long id) {
        return shopService.customer(id);
    }
}
