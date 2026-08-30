package br.com.fatecararas.product_service.clients;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CambioClient {
    private final RestClient restClient;

    public CambioClient(
            RestClient.Builder builder,
            @Value("${services.cambio.base-url}") String baseUrl) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public DollarQuote getLatestDollar() {
        try {
            DollarQuote quote = restClient.get()
                    .uri("/cambio/dollar/latest")
                    .retrieve()
                    .body(DollarQuote.class);

            if (quote == null || quote.buy() == null || quote.buy() <= 0) {
                throw new CambioUnavailableException();
            }
            return quote;
        } catch (RestClientException exception) {
            throw new CambioUnavailableException();
        }
    }
}
