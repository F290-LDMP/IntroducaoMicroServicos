package br.com.fatecararas.product_service.domain.repositories;

import br.com.fatecararas.product_service.domain.entities.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {
    Optional<ProductEntity> findByBarcode(String barcode);
    List<ProductEntity> findByDescriptionContains(String term);
}
