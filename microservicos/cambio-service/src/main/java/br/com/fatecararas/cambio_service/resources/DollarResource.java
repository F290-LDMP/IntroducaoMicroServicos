package br.com.fatecararas.cambio_service.resources;

import br.com.fatecararas.cambio_service.domain.entities.Dollar;
import br.com.fatecararas.cambio_service.domain.repositories.DollarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/cambio/dollar")
public class DollarResource {
    private final DollarRepository repository;

    @GetMapping("/latest")
    public ResponseEntity<Dollar> latest() {
        return repository.findTopByOrderByDateDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
