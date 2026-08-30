package br.com.fatecararas.product_service.resources.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductDollarResponse(
        Integer id,
        String description,
        String barcode,
        BigDecimal priceBrl,
        BigDecimal dollarBuy,
        BigDecimal priceUsd,
        LocalDateTime quoteDate) {}
