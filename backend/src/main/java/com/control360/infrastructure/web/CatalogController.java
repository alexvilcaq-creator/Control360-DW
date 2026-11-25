package com.control360.infrastructure.web;

import com.control360.application.Operations;
import com.control360.application.Store;
import com.control360.domain.Models.*;
import com.control360.domain.RuleViolation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import static com.control360.infrastructure.web.Dtos.*;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final Operations operations;
    private final Store store;

    public CatalogController(Operations operations, Store store) {
        this.operations = operations;
        this.store = store;
    }

    @GetMapping("/productos")
    List<ProductView> products(@RequestParam(required = false) String q, @RequestParam(required = false) Long category,
                               @RequestParam(required = false) String stock) {
        return operations.products(q, category, stock);
    }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    ProductView createProduct(Principal principal, @Valid @RequestBody ProductBody body) {
        return operations.saveProduct(actor(principal), body.toDomain(null), body.openingBalance());
    }

    @PutMapping("/productos/{id}")
    ProductView editProduct(Principal principal, @PathVariable long id, @Valid @RequestBody ProductBody body) {
        return operations.saveProduct(actor(principal), body.toDomain(id), 0);
    }

    @PostMapping("/inventario/{id}/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    ProductView adjust(Principal principal, @PathVariable long id, @Valid @RequestBody Adjustment body) {
        return operations.adjustStock(actor(principal), id, body.delta(), body.note().strip());
    }

    @GetMapping("/inventario/movimientos")
    List<Movement> movements(@RequestParam(required = false) Long productId) {
        return operations.movements(productId);
    }

    @GetMapping("/categorias")
    List<Category> categories() { return operations.list(Category.class); }

    @PostMapping("/categorias")
    @ResponseStatus(HttpStatus.CREATED)
    Category createCategory(@Valid @RequestBody CategoryBody body) { return operations.saveCategory(body.toDomain(null)); }

    @PutMapping("/categorias/{id}")
    Category editCategory(@PathVariable Long id, @Valid @RequestBody CategoryBody body) {
        return operations.saveCategory(body.toDomain(id));
    }

    @GetMapping("/ubicaciones")
    List<Location> locations() { return operations.list(Location.class); }

    @PostMapping("/ubicaciones")
    @ResponseStatus(HttpStatus.CREATED)
    Location createLocation(@Valid @RequestBody LocationBody body) { return operations.saveLocation(body.toDomain(null)); }

    @PutMapping("/ubicaciones/{id}")
    Location editLocation(@PathVariable Long id, @Valid @RequestBody LocationBody body) {
        return operations.saveLocation(body.toDomain(id));
    }

    @GetMapping("/clientes")
    List<Customer> customers() { return operations.list(Customer.class); }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    Customer createCustomer(@Valid @RequestBody CustomerBody body) { return operations.createCustomer(body.toDomain()); }

    private User actor(Principal principal) {
        return store.user(principal.getName()).orElseThrow(() -> RuleViolation.forbidden("Cuenta inexistente"));
    }
}
