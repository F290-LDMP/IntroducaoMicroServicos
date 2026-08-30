package br.com.fatecararas.product_service.services;

import br.com.fatecararas.product_service.clients.CambioClient;
import br.com.fatecararas.product_service.clients.DollarQuote;
import br.com.fatecararas.product_service.domain.entities.ProductEntity;
import br.com.fatecararas.product_service.domain.repositories.ProductRepository;
import br.com.fatecararas.product_service.resources.dto.ProductDollarResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class ProductConversionService {
    private final ProductRepository productRepository;
    private final CambioClient cambioClient;

    public ProductDollarResponse convert(Integer productId) {
        ProductEntity product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);
        DollarQuote quote = cambioClient.getLatestDollar();

        BigDecimal priceBrl = BigDecimal.valueOf(product.getPrice());
        BigDecimal dollarBuy = BigDecimal.valueOf(quote.buy());
        BigDecimal priceUsd = priceBrl.divide(
                dollarBuy,
                2,
                RoundingMode.HALF_UP);

        return new ProductDollarResponse(
                product.getId(),
                product.getDescription(),
                product.getBarcode(),
                priceBrl,
                dollarBuy,
                priceUsd,
                quote.date());
    }
}
