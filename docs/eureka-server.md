# Eureka Server

[← Integração entre os serviços](integracao.md) · [Visão geral](../README.md)

Este guia adiciona o `discovery-service`, um Eureka Server que centraliza o
registro de instâncias. Nesta etapa, o `product-service` se registra no Eureka;
a chamada HTTP para o `cambio-service` continua usando sua URL configurada.

## 27. Criar o `discovery-service`

No [Spring Initializr](https://start.spring.io/), crie um projeto com estas
opções:

- Project: **Gradle - Groovy**;
- Language: **Java**;
- Spring Boot: **4.1.0**;
- Group: `dev.sdras`;
- Artifact: `discovery-service`;
- Package name: `dev.sdras.discoveryservice`;
- Java: **17**.

Adicione as dependências Eureka Server, Spring Web MVC e Actuator. Extraia o
projeto em `spring-cloud/discovery-service`.

> **Ajustes para o monorepo:** remova `gradlew`, `gradlew.bat`, a pasta
> `gradle/` e o `settings.gradle` gerados pelo projeto independente. O wrapper
> e o `settings.gradle` válidos ficam somente na raiz, conforme o
> [README](../README.md#configurar-o-monorepo). Mantenha `build.gradle` e
> `src/`.

No `settings.gradle` da raiz, registre o subprojeto:

```groovy
include 'spring-cloud:discovery-service'
```

## 28. Configurar as dependências

Substitua `spring-cloud/discovery-service/build.gradle` por:

```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'dev.sdras'
version = '0.0.1-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

ext {
    set('springCloudVersion', "2025.1.3")
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-server'
    testImplementation 'org.springframework.boot:spring-boot-starter-actuator-test'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

O BOM `spring-cloud-dependencies` mantém as versões do Spring Cloud compatíveis
entre o servidor e o cliente Eureka.

## 29. Habilitar e configurar o Eureka Server

Crie `src/main/java/dev/sdras/discoveryservice/DiscoveryServiceApplication.java`:

```java
package dev.sdras.discoveryservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

@EnableEurekaServer
@SpringBootApplication
public class DiscoveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiscoveryServiceApplication.class, args);
    }
}
```

Crie `src/main/resources/application.yaml`:

```yaml
spring:
  application:
    name: discovery-service

eureka:
  client:
    fetch-registry: false
    register-with-eureka: false

server:
  port: 8761
```

Essas opções impedem que o servidor tente registrar a si mesmo. A interface do
Eureka ficará disponível na porta `8761`.

## 30. Registrar o `product-service`

No `microservicos/product-service/build.gradle`, importe o BOM e acrescente o
cliente Eureka:

```groovy
ext {
    set('springCloudVersion', "2025.1.3")
}

dependencyManagement {
    imports {
        mavenBom "org.springframework.cloud:spring-cloud-dependencies:${springCloudVersion}"
    }
}

dependencies {
    // demais dependências do serviço
    implementation 'org.springframework.cloud:spring-cloud-starter-netflix-eureka-client'
}
```

Em `microservicos/product-service/src/main/resources/application.yaml`,
adicione:

```yaml
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_SERVER_URL:http://localhost:8761/eureka/}
```

Não é necessária uma anotação extra na classe principal: o cliente Eureka é
configurado automaticamente. `EUREKA_SERVER_URL` permite alterar o servidor
sem recompilar a aplicação.

## 31. Executar e validar

Na raiz do monorepo, inicie primeiro o Eureka Server:

```bash
./gradlew :spring-cloud:discovery-service:bootRun
```

Em outro terminal, inicie o serviço de produtos:

```bash
./gradlew :microservicos:product-service:bootRun
```

Abra `http://localhost:8761`. Em **Instances currently registered with
Eureka**, aparecerá `PRODUCT-SERVICE`, com o link para a instância na porta
`8000`.

Para testar os dois projetos:

```bash
./gradlew :spring-cloud:discovery-service:test :microservicos:product-service:test
```
