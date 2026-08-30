package br.com.fatecararas.cambio_service.services;

import br.com.fatecararas.cambio_service.clients.HgFinanceResponse;
import br.com.fatecararas.cambio_service.clients.HgFinanceResponse.DollarData;
import br.com.fatecararas.cambio_service.domain.entities.Dollar;
import br.com.fatecararas.cambio_service.domain.repositories.DollarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class UpdateCurrenciesService {
    private static final Logger logger =
            LoggerFactory.getLogger(UpdateCurrenciesService.class);

    private final DollarRepository dollarRepository;
    private final RestClient restClient;
    private final String financePath;
    private final String apiKey;

    public UpdateCurrenciesService(
            DollarRepository dollarRepository,
            RestClient.Builder restClientBuilder,
            @Value("${hgbrasil.base-url}") String baseUrl,
            @Value("${hgbrasil.finance-path:/finance}") String financePath,
            @Value("${hgbrasil.api-key}") String apiKey) {
        this.dollarRepository = dollarRepository;
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.financePath = financePath;
        this.apiKey = apiKey;
    }

    @Scheduled(fixedRateString = "${currency.update-rate-ms:600000}")
    public void update() {
        if (apiKey.isBlank()) {
            logger.warn("HG_BRASIL_API_KEY não foi configurada");
            return;
        }

        try {
            HgFinanceResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(financePath)
                            .queryParam("key", apiKey)
                            .build())
                    .retrieve()
                    .body(HgFinanceResponse.class);

            DollarData usd = extractDollar(response);
            Dollar dollar = Dollar.builder()
                    .name(usd.name())
                    .buy(usd.buy())
                    .sell(usd.sell())
                    .variation(usd.variation())
                    .build();

            dollarRepository.save(dollar);
            logger.info("Cotação do dólar atualizada: {}", dollar.getBuy());
        } catch (RestClientException | IllegalStateException exception) {
            logger.error("Não foi possível atualizar a cotação do dólar", exception);
        }
    }

    private DollarData extractDollar(HgFinanceResponse response) {
        if (response == null
                || !Boolean.TRUE.equals(response.validKey())) {
            throw new IllegalStateException("Chave da HG Brasil inválida");
        }
        if (response.results() == null
                || response.results().currencies() == null
                || !"BRL".equals(response.results().currencies().source())
                || response.results().currencies().usd() == null
                || response.results().currencies().usd().buy() == null) {
            throw new IllegalStateException(
                    "Resposta da HG Brasil sem cotação USD em BRL");
        }
        return response.results().currencies().usd();
    }
}
