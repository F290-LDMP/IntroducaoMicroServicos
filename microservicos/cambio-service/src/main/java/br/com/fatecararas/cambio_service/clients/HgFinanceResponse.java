package br.com.fatecararas.cambio_service.clients;

import com.fasterxml.jackson.annotation.JsonProperty;

public record HgFinanceResponse(
        @JsonProperty("valid_key") Boolean validKey,
        Results results) {
    public record Results(Currencies currencies) {}

    public record Currencies(
            String source,
            @JsonProperty("USD") DollarData usd) {}

    public record DollarData(
            String name,
            Double buy,
            Double sell,
            Double variation) {}
}
