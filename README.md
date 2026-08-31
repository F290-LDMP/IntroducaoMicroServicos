# Introdução a microsserviços com Spring Boot

Neste tutorial construiremos dois microsserviços e faremos uma comunicação síncrona entre eles:

- **product-service**: mantém o catálogo de produtos e seus preços em reais;
- **cambio-service**: consulta periodicamente o endpoint público consolidado `/finance` da HG Brasil, extrai a cotação do dólar e mantém um histórico local;
- ao solicitar um produto convertido, o `product-service` chama o `cambio-service` por HTTP e devolve o preço em dólares.

```text
HG Brasil
GET /finance?key=...
    |
    | atualização agendada
    v
cambio-service :8100 <--- HTTP síncrono --- product-service :8000 <--- Cliente
```

> O objetivo da aula é observar a separação de responsabilidades, os bancos independentes e o efeito da indisponibilidade de um serviço sobre uma chamada síncrona.

## 1. Pré-requisitos

Instale ou tenha disponível:

- Java 17 ou superior;
- uma IDE Java (IntelliJ IDEA, Eclipse ou VS Code);
- `curl`, Postman ou Insomnia para testar as APIs;
- uma chave de integração da [HG Brasil](https://hgbrasil.com/docs/guide/key).

O projeto usa o Gradle Wrapper 8.14.3. Portanto, não é necessário instalar o Gradle globalmente.

Para conferir o Java:

```bash
java -version
```

### Configurar o monorepo

Abra a pasta raiz `IntroducaoMicroServicos` como projeto Gradle na IDE. O
`settings.gradle` da raiz registra cada microsserviço como um subprojeto.

O wrapper e o `settings.gradle` ficam **somente na raiz**. Os subprojetos em
`microservicos/` não possuem `gradlew`, `gradlew.bat`, `settings.gradle` nem a
pasta `gradle/` próprios: todos compartilham o wrapper da raiz, que deve ser
sempre invocado a partir dela.

Crie o `settings.gradle` da raiz apenas com o nome do projeto:

```groovy
rootProject.name = 'IntroducaoMicroServicos'
```

> **Não compile ainda.** Neste ponto os subprojetos ainda não existem — um
> `./gradlew build` agora falharia por não haver nada para compilar. Cada guia a
> seguir cria um serviço em `microservicos/`, registra-o no `settings.gradle`
> com um novo `include ...` e só então demonstra a compilação e a execução do
> serviço recém-criado. Ao adicionar outro serviço, registre-o no
> `settings.gradle` da raiz seguindo o mesmo padrão.

> **Onde está o wrapper?** O Gradle Wrapper 8.14.3 usado nos comandos é o
> conjunto de arquivos da raiz (`gradlew`, `gradlew.bat`, `gradle/wrapper/`). Ele
> já está versionado no repositório — durante a aula, copie-o de um dos branches
> do projeto. Para gerá-lo do zero, instale o Gradle globalmente e execute
> `gradle wrapper --gradle-version 8.14.3` na raiz.

> **Dica:** se a máquina não tiver o JDK 17 instalado (os subprojetos declaram
> `JavaLanguageVersion.of(17)` no toolchain), o Gradle falhará ao compilar.
> Nesse caso, adicione ao início do `settings.gradle` o plugin
> [Foojay Toolchains Resolver](https://github.com/gradle/foojay-toolchains):
>
> ```groovy
> plugins {
>     id 'org.gradle.toolchains.foojay-resolver-convention' version '0.8.0'
> }
> ```
>
> Com ele, o Gradle baixa automaticamente um JDK 17 para o build, independente
> da versão do Java instalada (por exemplo, Java 21).

Com os subprojetos criados e registrados no `settings.gradle`, estes comandos
funcionam a partir da raiz:

- compilar e testar todos os microsserviços registrados: `./gradlew clean build`;
- executar somente o serviço de produtos: `./gradlew :microservicos:product-service:bootRun`;
- executar somente o serviço de câmbio: `./gradlew :microservicos:cambio-service:bootRun`;
- listar os subprojetos reconhecidos: `./gradlew projects`.

A compilação e a execução de cada serviço são demonstradas nos próprios guias,
no momento em que cada subprojeto é criado.

### Branches do repositório

O código está organizado em branches temáticas, que correspondem aos guias:

- `product-service`: somente a raiz do monorepo e o serviço de produtos;
- `cambio-service`: somente a raiz do monorepo e o serviço de câmbio;
- `integracao`: monorepo completo, com os dois serviços e a comunicação HTTP.

Cada branch pode ser estudada de forma independente. Para a conversão de preços
funcionar de ponta a ponta, use a branch `integracao`.

## 2. Estrutura final

Os serviços ficam em projetos independentes:

```text
IntroducaoMicroServicos/
├── gradlew / gradlew.bat      # wrapper compartilhado (somente na raiz)
├── settings.gradle            # registra os subprojetos
├── gradle/wrapper/            # jar e properties do wrapper
└── microservicos/                 # subprojetos sem wrapper próprio
    ├── product-service/
    │   ├── build.gradle
    │   └── src/
    │       └── main/
    │           ├── java/br/com/fatecararas/product_service/
    │           └── resources/
    └── cambio-service/
        ├── build.gradle
        └── src/
            └── main/
                ├── java/br/com/fatecararas/cambio_service/
                └── resources/
```

Cada serviço será executado em um processo próprio e terá seu próprio banco H2 em memória.

---

## Guias

O tutorial foi separado em três guias, que devem ser seguidos nesta ordem:

1. [`product-service`](docs/product-service.md) — criação do catálogo, banco, migrations e API REST.
2. [`cambio-service`](docs/cambio-service.md) — consulta ao endpoint `/finance` da HG Brasil e histórico de cotações.
3. [Integração entre os serviços](docs/integracao.md) — comunicação HTTP síncrona e conversão dos preços.
