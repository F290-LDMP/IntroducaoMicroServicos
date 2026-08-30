package br.com.fatecararas.cambio_service.domain.repositories;

import br.com.fatecararas.cambio_service.domain.entities.Dollar;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DollarRepository extends JpaRepository<Dollar, Integer> {
    Optional<Dollar> findTopByOrderByDateDesc();
}
