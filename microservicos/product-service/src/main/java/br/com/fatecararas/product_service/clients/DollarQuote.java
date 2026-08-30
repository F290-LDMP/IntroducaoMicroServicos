package br.com.fatecararas.product_service.clients;

import java.time.LocalDateTime;

public record DollarQuote(
        Integer id,
        String name,
        Double buy,
        Double sell,
        Double variation,
        LocalDateTime date) {}
