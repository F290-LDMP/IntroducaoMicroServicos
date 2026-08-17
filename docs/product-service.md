# `product-service`

[← Visão geral](../README.md) · [Próximo: cambio-service →](cambio-service.md)

Este guia contém somente a criação e a validação do microsserviço de produtos.


## 3. Criar o `product-service`

No [Spring Initializr](https://start.spring.io/), crie um projeto com estas opções:

- Project: **Gradle - Groovy**;
- Language: **Java**;
- Spring Boot: **4.1.0**;
- Group: `br.com.fatecararas`;
- Artifact: `product-service`;
- Package name: `br.com.fatecararas.product_service`;
- Java: **17**.

Adicione as dependências:

- Spring Web MVC;
- Spring Data JPA;
- Validation;
- H2 Database;
- Flyway Migration;
- Lombok;
- Actuator.

Extraia o projeto em `microservicos/product-service`.

O arquivo `build.gradle` deve conter:

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
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    compileOnly 'org.projectlombok:lombok'
    runtimeOnly 'com.h2database:h2'
    annotationProcessor 'org.projectlombok:lombok'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-data-jpa-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-validation-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testCompileOnly 'org.projectlombok:lombok'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
    testAnnotationProcessor 'org.projectlombok:lombok'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

## 4. Configurar o serviço e o banco

Crie `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: product-service
  h2:
    console:
      enabled: true
  datasource:
    url: jdbc:h2:mem:productdb
    driver-class-name: org.h2.Driver
    username: aluno
    password: fatec
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: true
    database-platform: org.hibernate.dialect.H2Dialect
    properties:
      hibernate:
        format_sql: true

server:
  port: 8000

services:
  cambio:
    base-url: ${CAMBIO_SERVICE_URL:http://localhost:8100}
```

A propriedade `CAMBIO_SERVICE_URL` permitirá trocar o endereço do serviço de câmbio sem recompilar o projeto.

## 5. Criar as migrations

O Flyway executa scripts versionados ao iniciar a aplicação. Crie a pasta `src/main/resources/db/migration`.

Crie `V1__Create_Table_Products.sql`:

```sql
CREATE TABLE IF NOT EXISTS product (
    id INT PRIMARY KEY AUTO_INCREMENT,
    description VARCHAR(500) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    barcode VARCHAR(50) NOT NULL
);
```

Crie `V2__Populate_Table_Products.sql` com alguns dados:

```sql
INSERT INTO product (description, price, barcode)
VALUES ('ABACATE KG', 4.99, '97');

INSERT INTO product (description, price, barcode)
VALUES ('ABACAXI UN', 2.99, '1014');

INSERT INTO product (description, price, barcode)
VALUES ('ACUCAR UNIAO 1KG', 2.49, '7891910000197');
```

> As migrations já estão no repositório. Se você estiver reproduzindo a aula, crie apenas os três registros acima.

O repositório possui uma versão maior dessa migration, com mais produtos. Para reproduzir a aula, os três registros acima são suficientes.

## 6. Criar a entidade `ProductEntity`

Crie `domain/entities/ProductEntity.java`:

```java
package br.com.fatecararas.product_service.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
@Entity(name = "product")
public class ProductEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(length = 500, nullable = false)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column(nullable = false)
    private String barcode;
}
```

## 7. Criar o repositório

Crie `domain/repositories/ProductRepository.java`:

```java
package br.com.fatecararas.product_service.domain.repositories;

import br.com.fatecararas.product_service.domain.entities.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<ProductEntity, Integer> {
    Optional<ProductEntity> findByBarcode(String barcode);
    List<ProductEntity> findByDescriptionContains(String term);
}
```

O Spring Data JPA cria a implementação desse repositório durante a inicialização.

## 8. Criar a API de produtos

Crie `resources/ProductResource.java`:

```java
package br.com.fatecararas.product_service.resources;

import br.com.fatecararas.product_service.domain.entities.ProductEntity;
import br.com.fatecararas.product_service.domain.repositories.ProductRepository;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RequiredArgsConstructor
@RestController
@RequestMapping("/product")
public class ProductResource {
    private final ProductRepository repository;

    @GetMapping("/{id}")
    public ResponseEntity<ProductEntity> findById(@PathVariable Integer id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<Void> save(@RequestBody ProductEntity product) {
        ProductEntity saved = repository.save(product);
        URI uri = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(uri).build();
    }

    @GetMapping("/all")
    public ResponseEntity<Page<ProductEntity>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        PageRequest pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "description"));
        Page<ProductEntity> products = repository.findAll(pageable);

        if (products.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(products);
    }
}
```

## 9. Executar e testar o serviço de produtos

Entre na pasta do serviço:

```bash
cd microservicos/product-service
./gradlew bootRun
```

No Windows, use:

```powershell
gradlew.bat bootRun
```

Em outro terminal, liste os produtos:

```bash
curl "http://localhost:8000/product/all?page=0&size=10"
```

Busque um produto pelo id:

```bash
curl http://localhost:8000/product/1
```

Cadastre um produto:

```bash
curl -i -X POST http://localhost:8000/product \
  -H "Content-Type: application/json" \
  -d '{"description":"CAFE 500G","price":18.90,"barcode":"7890000000001"}'
```

A resposta deve ter status `201 Created` e o cabeçalho `Location` com a URL do novo produto.

### Checkpoint

Antes de prosseguir, confirme:

- a aplicação iniciou na porta `8000`;
- o Flyway executou as duas migrations;
- `GET /product/1` devolve um produto;
- `POST /product` devolve `201 Created`.

---


---

[← Visão geral](../README.md) · [Próximo: cambio-service →](cambio-service.md)
