# Integração entre `product-service` e `cambio-service`

[← Cambio service](cambio-service.md) · [Visão geral](../README.md)

Este guia conecta os dois serviços por HTTP e adiciona a conversão de preços de
reais para dólares.


## 19. Definir os contratos da integração

O `product-service` consumirá este endpoint:

```http
GET http://localhost:8100/cambio/dollar/latest
```

E disponibilizará ao cliente:

```http
GET http://localhost:8000/product/{id}/dollar
```

A conversão didática usará a cotação de compra:

```text
preço em USD = preço em BRL / cotação de compra do dólar
```

## 20. Criar os DTOs no serviço de produtos

No `product-service`, crie `clients/DollarQuote.java`:

```java
package br.com.fatecararas.product_service.clients;

import java.time.LocalDateTime;

public record DollarQuote(
        Integer id,
        String name,
        Double buy,
        Double sell,
        Double variation,
        LocalDateTime date) {}
```

Crie `resources/dto/ProductDollarResponse.java`:

```java
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
```

## 21. Criar o cliente do serviço de câmbio

Crie `clients/CambioUnavailableException.java`:

```java
package br.com.fatecararas.product_service.clients;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class CambioUnavailableException extends RuntimeException {
    public CambioUnavailableException() {
        super("Serviço de câmbio indisponível ou sem cotação");
    }
}
```

Crie `clients/CambioClient.java`:

```java
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
```

Essa chamada bloqueia a requisição do cliente até o `cambio-service` responder. Esse comportamento caracteriza a comunicação síncrona.

## 22. Criar o serviço de conversão

Crie `services/ProductConversionService.java`:

```java
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
```

Crie `services/ProductNotFoundException.java`:

```java
package br.com.fatecararas.product_service.services;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException() {
        super("Produto não encontrado");
    }
}
```

## 23. Criar o endpoint convertido

Acrescente ao `ProductResource` o atributo:

```java
private final ProductConversionService conversionService;
```

Importe as classes:

```java
import br.com.fatecararas.product_service.resources.dto.ProductDollarResponse;
import br.com.fatecararas.product_service.services.ProductConversionService;
```

E acrescente o método:

```java
@GetMapping("/{id}/dollar")
public ResponseEntity<ProductDollarResponse> findInDollar(
        @PathVariable Integer id) {
    return ResponseEntity.ok(conversionService.convert(id));
}
```

O `@RequiredArgsConstructor` passará a injetar tanto o repositório quanto o serviço de conversão.

## 24. Testar o fluxo completo

Mantenha os dois serviços ativos e execute:

```bash
curl -i http://localhost:8000/product/1/dollar
```

Exemplo de resposta:

```json
{
  "id": 1,
  "description": "ABACATE KG",
  "barcode": "97",
  "priceBrl": 4.99,
  "dollarBuy": 5.11,
  "priceUsd": 0.98,
  "quoteDate": "2026-08-16T14:30:00"
}
```

Experimente também os cenários de erro:

```bash
# Produto inexistente: deve responder 404
curl -i http://localhost:8000/product/999999/dollar

# Pare o cambio-service e repita: deve responder 503
curl -i http://localhost:8000/product/1/dollar
```

## 25. Executar os testes automatizados básicos

Em cada projeto, execute:

```bash
./gradlew test
```

O teste de contexto do `cambio-service` pode ser executado sem uma chave real: o scheduler detectará a configuração vazia e não fará a chamada externa.

## 26. Acessar os consoles H2

Com os serviços ativos, abra:

- produtos: `http://localhost:8000/h2-console`;
- câmbio: `http://localhost:8100/h2-console`.

Use as credenciais:

| Serviço | JDBC URL | Usuário | Senha |
|---|---|---|---|
| Produtos | `jdbc:h2:mem:productdb` | `aluno` | `fatec` |
| Câmbio | `jdbc:h2:mem:cambiodb` | `aluno` | `fatec` |

Observe que são bancos diferentes. Um microsserviço não acessa diretamente as tabelas do outro.

## 27. Solução de problemas

### A porta já está em uso

Encerre o processo que ocupa `8000` ou `8100`, ou altere a propriedade `server.port` do serviço correspondente.

### O câmbio registra que a chave não foi configurada

Defina `HG_BRASIL_API_KEY` no mesmo terminal antes de iniciar o `cambio-service`.

### A HG Brasil responde com erro

Confira a chave, a conexão com a internet e os limites do plano. Garanta também
que a URL configurada continue sendo o endpoint consolidado `/finance`, sem
substituí-lo por `/finance/quotations` ou `/v2/finance/quotes`. Enquanto a API
externa estiver indisponível, a última cotação salva continuará sendo usada. Em
um banco novo, ainda sem registros, `/cambio/dollar/latest` responderá `404`.

### O produto convertido responde `503`

Confirme se:

- o `cambio-service` está ativo na porta `8100`;
- existe uma cotação persistida;
- `CAMBIO_SERVICE_URL` aponta para o endereço correto;
- `curl http://localhost:8100/cambio/dollar/latest` responde diretamente.

### O Lombok não é reconhecido pela IDE

Habilite o processamento de anotações e instale o plugin do Lombok, quando exigido pela IDE. O Gradle continuará sendo a referência para a compilação.

## 28. Checklist final

- [ ] O `product-service` inicia na porta `8000`.
- [ ] As migrations criam e populam a tabela de produtos.
- [ ] O `cambio-service` inicia na porta `8100`.
- [ ] A chave da HG Brasil não está gravada no repositório.
- [ ] A única chamada externa usa `GET /finance?key=...`.
- [ ] O scheduler persiste a cotação do dólar.
- [ ] `/cambio/dollar/latest` devolve a última cotação.
- [ ] `/product/{id}/dollar` devolve o preço convertido.
- [ ] Produto inexistente resulta em `404 Not Found`.
- [ ] Câmbio indisponível resulta em `503 Service Unavailable`.

Ao concluir, teremos dois microsserviços independentes colaborando por HTTP. A simplicidade da chamada síncrona facilita o entendimento, mas também demonstra o acoplamento temporal: para converter um preço, o `product-service` precisa receber uma resposta do `cambio-service` naquele momento.

---

[← Cambio service](cambio-service.md) · [Visão geral](../README.md)
