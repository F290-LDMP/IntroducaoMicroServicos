package br.com.fatecararas.product_service.resources;

import br.com.fatecararas.product_service.domain.entities.ProductEntity;
import br.com.fatecararas.product_service.domain.repositories.ProductRepository;
import br.com.fatecararas.product_service.resources.dto.ProductDollarResponse;
import br.com.fatecararas.product_service.services.ProductConversionService;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RequiredArgsConstructor
@RestController
@RequestMapping("/product")
public class ProductResource {
    private final ProductRepository repository;
    private final ProductConversionService conversionService;

    @GetMapping("/{id}")
    public ResponseEntity<ProductEntity> findById(@PathVariable Integer id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> save(@RequestBody ProductEntity product) {
        ProductEntity saved = repository.save(product);
        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(uri).build();
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ProductEntity>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "description"));
        Page<ProductEntity> products = repository.findAll(pageable);

        if (products.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}/dollar")
    public ResponseEntity<ProductDollarResponse> findInDollar(
            @PathVariable Integer id) {
        return ResponseEntity.ok(conversionService.convert(id));
    }
}
