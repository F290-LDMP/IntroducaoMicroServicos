package br.com.fatecararas.cambio_service.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity(name = "dollar")
public class Dollar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private String name;
    private Double buy;
    private Double sell;
    private Double variation;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime date;
}
