# `cambio-service`

[← Product service](product-service.md) · [Visão geral](../README.md) · [Próximo: integração →](integracao.md)

Este guia contém somente a criação do microsserviço de câmbio e sua comunicação
com o endpoint consolidado `/finance` da HG Brasil.


## 10. Criar o `cambio-service`

No Spring Initializr, crie outro projeto com:

- Project: **Gradle - Groovy**;
- Language: **Java**;
- Spring Boot: **4.1.0**;
- Group: `br.com.fatecararas`;
- Artifact: `cambio-service`;
- Package name: `br.com.fatecararas.cambio_service`;
- Java: **17**.

Adicione Spring Web MVC, Spring Data JPA, H2 Database, Lombok e Actuator. Extraia em `microservicos/cambio-service`.

> **Ajustes para o monorepo:** assim como no `product-service`, remova do
> projeto gerado pelo Initializr os arquivos `gradlew`, `gradlew.bat`, a pasta
> `gradle/` e o `settings.gradle`. O subprojeto usa o wrapper e o
> `settings.gradle` da raiz do monorepo — veja o
> [README](../README.md#configurar-o-monorepo).

Agora registre o subprojeto no `settings.gradle` da raiz, adicionando a linha do
`cambio-service`:

```groovy
rootProject.name = 'IntroducaoMicroServicos'

include 'microservicos:product-service'
include 'microservicos:cambio-service'
```

Use este `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'br.com.fatecararas'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-h2console'
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-restclient'
    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testCompileOnly 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testAnnotationProcessor 'org.projectlombok:lombok'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

> O `spring-boot-starter-restclient` é necessário porque o scheduler usa o
> `RestClient` para chamar a HG Brasil. No Spring Boot 4, o starter `webmvc`
> não inclui mais a auto-configuração do `RestClient.Builder` — sem este
> starter, o contexto falha ao iniciar com
> `NoSuchBeanDefinitionException: RestClient$Builder`.

## 11. Configurar o câmbio e a chave externa

> Crie uma chave de integração na [HG Brasil](https://hgbrasil.com/docs/guide/key) e guarde-a em um local seguro. Não a grave no repositório.

Crie `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: cambio-service
  h2:
    console:
      enabled: true
  datasource:
    url: jdbc:h2:mem:cambiodb
    driver-class-name: org.h2.Driver
    username: aluno
    password: fatec
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

server:
  port: 8100

hgbrasil:
  base-url: https://api.hgbrasil.com
  finance-path: /finance
  api-key: ${HG_BRASIL_API_KEY:}

currency:
  update-rate-ms: ${CURRENCY_UPDATE_RATE_MS:600000}
```

Defina a chave somente no ambiente do terminal que executará o serviço:

```bash
export HG_BRASIL_API_KEY="SUA_CHAVE"
```

No PowerShell:

```powershell
$env:HG_BRASIL_API_KEY="SUA_CHAVE"
```

Não grave a chave real no `application.yaml` e não a envie ao Git. Neste projeto,
a única requisição externa é `GET https://api.hgbrasil.com/finance?key=...`.
Endpoints específicos, como `/finance/quotations` e os endpoints `/v2`, não são
necessários para a aula e podem exigir um plano pago. A documentação do endpoint
consolidado está disponível na [HG Brasil](https://hgbrasil.com/docs/finance/).

Para validar a chave e o acesso antes de iniciar o serviço:

```bash
curl --get "https://api.hgbrasil.com/finance" \
  --data-urlencode "key=${HG_BRASIL_API_KEY}"
```

## 12. Criar a entidade de cotação

Crie `domain/entities/Dollar.java`:

```java
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
```

Cada execução do scheduler inserirá uma nova linha. Assim, o banco guardará o histórico das consultas realizadas.

## 13. Consultar a última cotação

Crie `domain/repositories/DollarRepository.java`:

```java
package br.com.fatecararas.cambio_service.domain.repositories;

import br.com.fatecararas.cambio_service.domain.entities.Dollar;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DollarRepository extends JpaRepository<Dollar, Integer> {
    Optional<Dollar> findTopByOrderByDateDesc();
}
```

O nome do método informa ao Spring Data que deve ordenar por data decrescente e devolver somente o primeiro registro.

## 14. Mapear a resposta da HG Brasil

O endpoint `/finance` devolve várias informações financeiras em uma única
resposta. O serviço precisa mapear apenas a cotação `USD` presente em
`results.currencies`. Crie `clients/HgFinanceResponse.java`:

```java
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
```

Os `record`s representam somente a parte da resposta necessária para esta aula.
Campos extras enviados pela API serão ignorados. `valid_key` permite distinguir
uma chave recusada de uma resposta que simplesmente não contém a cotação, e
`source` confirma que os valores estão expressos em reais (`BRL`).

## 15. Criar o scheduler

Crie `services/UpdateCurrenciesService.java`:

```java
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
```

Observe a sintaxe correta do `RestClient`: primeiro chamamos `get()` e depois informamos o endereço com `uri(...)`.

Se a consulta externa falhar, se `valid_key` for falso ou se a resposta não
trouxer `USD` com moeda-base `BRL`, o erro será registrado e a última cotação
salva continuará disponível.

## 16. Habilitar o agendamento

Edite `CambioServiceApplication.java`:

```java
package br.com.fatecararas.cambio_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CambioServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CambioServiceApplication.class, args);
    }
}
```

Por padrão, o método agendado é executado assim que o serviço inicia e depois a cada 10 minutos.

## 17. Expor a última cotação

Crie `resources/DollarResource.java`:

```java
package br.com.fatecararas.cambio_service.resources;

import br.com.fatecararas.cambio_service.domain.entities.Dollar;
import br.com.fatecararas.cambio_service.domain.repositories.DollarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/cambio/dollar")
public class DollarResource {
    private final DollarRepository repository;

    @GetMapping("/latest")
    public ResponseEntity<Dollar> latest() {
        return repository.findTopByOrderByDateDesc()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
```

## 18. Executar e testar o câmbio

Com o subprojeto registrado no `settings.gradle`, compile-o a partir da **raiz
do monorepo**. O primeiro build baixa as dependências:

```bash
./gradlew :microservicos:cambio-service:build
```

Em um novo terminal, execute o serviço:

```bash
export HG_BRASIL_API_KEY="SUA_CHAVE"
./gradlew :microservicos:cambio-service:bootRun
```

Consulte a cotação persistida:

```bash
curl -i http://localhost:8100/cambio/dollar/latest
```

Exemplo de resposta:

```json
{
  "id": 1,
  "name": "Dollar",
  "buy": 5.11,
  "sell": 5.10,
  "variation": 0.03,
  "date": "2026-08-16T14:30:00"
}
```

Os valores e a data variarão. Se ainda não existir uma cotação, o endpoint responderá `404 Not Found`.

### Checkpoint

Confirme:

- o serviço iniciou na porta `8100`;
- o log informa que a cotação foi atualizada;
- `GET /cambio/dollar/latest` devolve `200 OK`;
- reiniciar somente o `product-service` não afeta o banco do câmbio.

---


---

[← Product service](product-service.md) · [Visão geral](../README.md) · [Próximo: integração →](integracao.md)
