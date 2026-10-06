# Portcullis

Estar logado não quer dizer que você pode mexer naquele registro.

Portcullis é uma lib Java que eu criei em cima dessa ideia. Você anota o método e, antes de ele rodar, ela confere se tem alguém logado, se essa pessoa tem a role ou a permissão certa, se o recurso pedido é dela, se está no mesmo tenant e se ela não passou do limite de chamadas. Ela também garante que uma requisição repetida, como um pagamento reenviado depois de um timeout, rode uma vez só.

```java
@GetMapping("/orders/{id}")
@OwnedBy(Order.class)
public Order get(@ResourceId @PathVariable Long id) { ... }
```

Se o pedido não for do usuário logado, a chamada para ali com um 403. O método nem é executado.

## Por que eu fiz

A maioria das APIs confere bem quem está chamando. O token é validado, a sessão existe, o gateway deixa passar. O que costuma faltar é a pergunta seguinte: esse registro específico é de quem está pedindo? Quando ela não é feita, basta trocar o id na URL para ler os dados de outra pessoa. Não tem nada de sofisticado nisso, e é um dos problemas de segurança mais comuns em API.

A correção é simples, mas precisa estar em todo endpoint que recebe um id, sem exceção. E uma regra que depende de ser lembrada em cada endpoint vai ser esquecida em algum. Normalmente ela fica espalhada: um `if` no controller, outro no service, cada um escrito de um jeito, e olhando o método não dá para saber se ele está protegido.

Eu queria que essa regra ficasse declarada no próprio método, com uma anotação, de um jeito que esquecer fique visível em code review. E queria isso sem me prender a um framework. Então a Portcullis segue algumas regras:

- o núcleo não depende de nada; a interceptação é feita com AspectJ, então funciona igual em Spring, Micronaut, Quarkus ou Java puro
- a regra de posse é uma função Java comum, que você escreve e testa como qualquer outro código
- custo baixo: as anotações são lidas uma vez por método, e depois disso não tem reflexão por chamada
- na dúvida, nega: anotação mal configurada ou resolver que falha bloqueiam a chamada

## O que ela não faz

A Portcullis não autentica ninguém: ela usa o usuário que o seu framework já autenticou. Também não cuida de HTTPS, validação de entrada, CORS, CSRF ou firewall. Ela é a última checagem antes do método rodar, a que fica mais perto do dado.

## Exemplo completo

```java
@Authenticated
@RequiresRole("USER")
public class OrderController {

    @OwnedBy(Order.class)
    public Order get(@ResourceId Long orderId) { ... }

    @OwnedBy(value = Order.class, bypassRoles = "ADMIN")
    public void cancel(@ResourceId Long orderId) { ... }

    @OwnedBy(Order.class)
    @OwnedBy(Item.class)
    public Item item(@ResourceId(Order.class) Long orderId, @ResourceId(Item.class) Long itemId) { ... }

    @OwnedBy(Order.class)
    public void update(@ResourceId UpdateOrder request) { ... }

    @RequiresPermission("orders:export")
    @RateLimit(requests = 5, window = 1, unit = ChronoUnit.MINUTES)
    public byte[] export() { ... }

    @PublicAccess
    public String health() { return "ok"; }
}
```

Algumas coisas que esse exemplo mostra:

- anotação na classe vale para todos os métodos públicos dela, e a do método substitui a da classe
- `bypassRoles` deixa um admin passar sem ser dono do recurso
- quando o método recebe mais de um id, cada `@ResourceId` diz de qual tipo é
- se o id vem dentro do corpo da requisição, o DTO implementa `HasResourceId`:

```java
record UpdateOrder(Long orderId, String note) implements HasResourceId<Long> {
    public Long resourceId() { return orderId; }
}
```

E a configuração, feita uma vez na subida da aplicação:

```java
Portcullis.configure()
        .principalProvider(currentUser)                                 // de onde vem o usuário logado
        .ownership(Order.class, (SecurityPrincipal user, Long id) -> orders.existsByIdAndOwnerId(id, user.id()))
        .ownership(Item.class, (SecurityPrincipal user, Long id) -> items.isOwnedBy(id, user.id()))
        .auditListener(new LoggingAuditListener())
        .install();
```

## Anotações

| Anotação | Onde | O que exige |
|---|---|---|
| `@Authenticated` | método, classe | alguém logado |
| `@RequiresRole({"ADMIN", "SUPPORT"})` | método, classe | qualquer uma das roles (`match = Match.ALL` para todas) |
| `@RequiresPermission({"orders:read"})` | método, classe | todas as permissões (`match = Match.ANY` para qualquer uma) |
| `@OwnedBy(Order.class)` | método | o recurso do `@ResourceId` é do usuário |
| `@SameTenant(Invoice.class)` | método | o recurso do `@ResourceId` está no tenant do usuário |
| `@RateLimit(requests = 60)` | método, classe | no máximo N chamadas por usuário na janela |
| `@PublicAccess` | método | ignora login, role e permissão definidos na classe |
| `@ResourceId` | parâmetro | marca o id usado por `@OwnedBy` e `@SameTenant` |
| `@Idempotent` | método | a mesma requisição repetida roda uma vez só |
| `@IdempotencyKey` | parâmetro | marca a chave de idempotência enviada pelo cliente |

Tudo que precisa de usuário já implica `@Authenticated`. As checagens rodam nesta ordem e param na primeira que falhar: login, rate limit, roles, permissões, tenant, posse. Coloquei as mais baratas primeiro, assim uma chamada negada não chega a consultar o banco.

Só métodos públicos e não estáticos são interceptados. Métodos privados e lambdas dentro de uma classe anotada ficam de fora.

## Instalação

Precisa de Java 25.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-aspectj</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Depois, o weaving. Eu uso o modo em que o `javac` compila normalmente e o `ajc` costura as classes já compiladas. Assim não tem conflito com Lombok, MapStruct ou outro annotation processor:

```xml
<plugin>
    <groupId>dev.aspectj</groupId>
    <artifactId>aspectj-maven-plugin</artifactId>
    <version>1.14.1</version>
    <dependencies>
        <dependency>
            <groupId>org.aspectj</groupId>
            <artifactId>aspectjtools</artifactId>
            <version>1.9.25</version>
        </dependency>
    </dependencies>
    <configuration>
        <complianceLevel>25</complianceLevel>
        <aspectLibraries>
            <aspectLibrary>
                <groupId>io.github.sfesantos</groupId>
                <artifactId>portcullis-aspectj</artifactId>
            </aspectLibrary>
        </aspectLibraries>
        <weaveDirectories>
            <weaveDirectory>${project.build.outputDirectory}</weaveDirectory>
        </weaveDirectories>
        <forceAjcCompile>true</forceAjcCompile>
        <sources/>
        <Xlint>ignore</Xlint>
    </configuration>
    <executions>
        <execution>
            <phase>process-classes</phase>
            <goals>
                <goal>compile</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Se preferir não mexer no build, dá para usar load time weaving rodando a aplicação com `-javaagent:aspectjweaver-1.9.25.jar`. O jar já traz o `META-INF/aop.xml`.

Se o projeto já tem um mecanismo de interceptação próprio, também dá para dispensar o AspectJ e chamar a checagem direto:

```java
Portcullis.guard().check(method, targetClass, arguments);
```

## Quem está logado

A lib não autentica ninguém. Ela pergunta para um `PrincipalProvider` quem está logado, e você liga isso no que o seu framework já tem. O usuário é um `SecurityPrincipal` com `id()`, `roles()`, `permissions()` e `tenantId()`. Dá para implementar a interface no seu próprio tipo de usuário ou usar `SimplePrincipal.of("42").withRoles("ADMIN").withTenant("acme")`.

Spring Security:

```java
Optional<SecurityPrincipal> fromSpringSecurity() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
        return Optional.empty();
    }
    var roles = auth.getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring(5))
            .toArray(String[]::new);
    return Optional.of(SimplePrincipal.of(auth.getName()).withRoles(roles));
}
```

Micronaut:

```java
() -> securityService.getAuthentication()
        .map(a -> SimplePrincipal.of(a.getName()).withRoles(a.getRoles().toArray(String[]::new)))
```

Quarkus:

```java
() -> identity.isAnonymous()
        ? Optional.empty()
        : Optional.of(SimplePrincipal.of(identity.getPrincipal().getName())
                .withRoles(identity.getRoles().toArray(String[]::new)))
```

Sem framework nenhum, o padrão é ler de `PortcullisContext`, que você preenche num filtro:

```java
try (var scope = PortcullisContext.bind(principal)) {
    chain.doFilter(request, response);
}
```

## Rate limit

`@RateLimit` conta chamadas por usuário em cada método. Chamadas anônimas dividem um único contador. O algoritmo é token bucket: com `requests = 60` e janela de um minuto, a pessoa pode fazer 60 chamadas seguidas, e depois ganha uma nova a cada segundo.

```java
@RateLimit(requests = 60)                                         // 60 por minuto
public List<Order> list() { ... }

@RateLimit(requests = 3, window = 10, unit = ChronoUnit.MINUTES)  // 3 a cada 10 minutos
public void resendConfirmationEmail() { ... }

@RateLimit(requests = 100, key = "search")                        // os dois métodos dividem o mesmo limite
public List<Product> searchByName(String name) { ... }

@RateLimit(requests = 100, key = "search")
public List<Product> searchByTag(String tag) { ... }
```

Quando o limite estoura, sai uma `RateLimitExceededException` (429) com `retryAfter()`, que serve direto para o header `Retry-After`.

Onde os contadores ficam depende do que você configurar:

| Opção | Módulo | Vale para |
|---|---|---|
| `InMemoryRateLimiter` | já vem no core, é o padrão | uma instância só |
| `CaffeineRateLimiter` | `portcullis-caffeine` | uma instância só, com memória limitada |
| `RedisRateLimiter` | `portcullis-redis` | qualquer número de instâncias |

Se a aplicação roda em mais de uma instância, use Redis. Com as outras duas, cada instância conta sozinha, e três instâncias com limite 60 deixam passar 180.

### Caffeine

Bom para quando é uma instância só e você quer garantir que a memória não cresce sem limite, por exemplo sob ataque com muitos ids diferentes. Os contadores expiram sozinhos depois de uma janela sem uso.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-caffeine</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
Portcullis.configure()
        .rateLimiter(CaffeineRateLimiter.create())               // até 100 mil contadores
        // .rateLimiter(CaffeineRateLimiter.withMaximumSize(10_000))
        .install();
```

### Redis

O contador vive no Redis, então todas as instâncias enxergam o mesmo limite. Cada chamada é um script Lua atômico, uma ida e volta ao Redis só. O script usa o relógio do próprio Redis, então diferença de horário entre as máquinas não bagunça a contagem. Os contadores expiram sozinhos depois de uma janela parada.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-redis</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

O módulo não traz cliente Redis. Ele usa o que a aplicação já tem, e eu deixei adaptadores prontos para Lettuce e Jedis.

Lettuce:

```java
var connection = RedisClient.create("redis://localhost:6379").connect();

Portcullis.configure()
        .rateLimiter(RedisRateLimiter.of(new LettuceRedisScripts(connection.sync())))
        .install();
```

Jedis:

```java
var jedis = redis.clients.jedis.RedisClient.create("localhost", 6379);

Portcullis.configure()
        .rateLimiter(RedisRateLimiter.of(new JedisRedisScripts(jedis)))
        .install();
```

Spring Boot com `spring-boot-starter-data-redis` já usa Lettuce por baixo. Dá para aproveitar o cliente que o Spring configurou, com host, senha e TLS:

```java
@Configuration
class RateLimitConfig {

    RateLimitConfig(LettuceConnectionFactory factory) {
        var connection = ((RedisClient) factory.getNativeClient()).connect();
        Portcullis.configure()
                .rateLimiter(RedisRateLimiter.of(new LettuceRedisScripts(connection.sync())))
                .install();
    }
}
```

Com Redis Cluster, passe `RedisClusterClient.connect().sync()` para o `LettuceRedisScripts`. Cada contador é uma chave só, então funciona sem configuração extra.

Para outro cliente, implemente `RedisScripts`: são dois métodos, `evalSha` e `eval`.

E se o Redis cair? Por padrão a chamada é negada, coerente com o resto da lib. Se para você disponibilidade pesa mais que o limite, dá para deixar passar:

```java
RedisRateLimiter.builder(new LettuceRedisScripts(connection.sync()))
        .onFailure(OnRedisFailure.ALLOW)   // loga um aviso e libera a chamada
        .keyPrefix("myapp:rl:")            // padrão: portcullis:rl:
        .build();
```

## Idempotência

Pensa numa transferência. O cliente manda a requisição, a rede cai antes da resposta voltar, e ele tenta de novo. Do lado do servidor, a primeira já tinha dado certo. Sem cuidado, o dinheiro sai duas vezes.

O jeito comum de resolver é o cliente mandar uma chave única por operação, normalmente no header `Idempotency-Key`, e o servidor lembrar o que já fez com ela. É isso que o `@Idempotent` faz:

```java
@PostMapping("/transfers")
@Idempotent
@OwnedBy(Account.class)
public Transfer transfer(@ResourceId @RequestParam Long from,
                         @RequestBody TransferRequest request,
                         @IdempotencyKey @RequestHeader("Idempotency-Key") String key) { ... }
```

O que acontece com cada requisição:

- primeira vez com a chave: o método roda e o resultado fica guardado por 24 horas
- mesma chave depois disso: o método não roda de novo, e o cliente recebe o mesmo resultado da primeira vez
- mesma chave enquanto a primeira ainda está rodando: 409
- se o método lançar exceção, a chave é liberada e o cliente pode tentar de novo
- sem chave: 400; com `@Idempotent(required = false)` o método só roda normalmente

A chave vale por usuário e por método. Dois usuários mandando a mesma chave não se misturam, e ninguém consegue ler a resposta guardada de outra pessoa. As checagens de acesso rodam antes, então uma chamada negada não ocupa a chave.

O tempo de guarda muda na anotação:

```java
@Idempotent(ttl = 7, unit = ChronoUnit.DAYS)
```

Se você não quer a chave na assinatura do método, dá para ler o header num lugar só. No Spring:

```java
Portcullis.configure()
        .idempotencyKeyProvider(() -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(a -> ((ServletRequestAttributes) a).getRequest().getHeader("Idempotency-Key")))
        .install();
```

E aí basta `@Idempotent` no método. Um parâmetro com `@IdempotencyKey`, quando existe, tem prioridade.

### Onde os resultados ficam

Os mesmos três lugares do rate limit, com a mesma regra: se tem mais de uma instância, use Redis. Com memória ou Caffeine, uma repetição que cai em outra instância roda de novo.

```java
Portcullis.configure()
        .idempotencyStore(RedisIdempotencyStore.of(redisScripts, new JacksonResultCodec(objectMapper)))
        // ou CaffeineIdempotencyStore.create(), ou nada para ficar com o InMemoryIdempotencyStore padrão
        .install();
```

O Redis guarda texto, então o resultado do método precisa ser convertido. A lib não escolhe um formato por você: você passa um `ResultCodec`. Com Jackson fica assim:

```java
class JacksonResultCodec implements ResultCodec {
    private final ObjectMapper mapper;

    JacksonResultCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String encode(Object result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Object decode(String encoded, Type type) {
        try {
            return mapper.readValue(encoded, mapper.constructType(type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
```

O `redisScripts` é o mesmo `LettuceRedisScripts` ou `JedisRedisScripts` do rate limit.

Enquanto o método roda, a chave fica reservada por um prazo curto, 1 minuto por padrão. Se a instância cair no meio, a chave se libera sozinha depois disso, em vez de ficar presa pelo ttl inteiro. Se algum método seu demora mais que isso, aumente:

```java
Portcullis.configure()
        .idempotencyLease(Duration.ofMinutes(5))
        .install();
```

Uma limitação: a lib não compara o corpo da requisição. Se o cliente reusar a mesma chave com outro conteúdo, ele recebe a resposta da primeira chamada. Gere uma chave nova para cada operação.

## Erros

Toda exceção estende `PortcullisException` e tem `status()`, então um único handler resolve:

| Exceção | Status | Quando |
|---|---|---|
| `UnauthenticatedException` | 401 | ninguém logado |
| `ForbiddenException` | 403 | role, permissão, tenant ou posse; `check()` diz qual |
| `InvalidIdempotencyKeyException` | 400 | `@Idempotent` sem chave, ou chave com mais de 128 caracteres |
| `IdempotencyConflictException` | 409 | a mesma chave ainda está sendo processada |
| `RateLimitExceededException` | 429 | passou do limite; `retryAfter()` diz quanto esperar |
| `PolicyDefinitionException` | 500 | anotação mal usada ou resolver não registrado |

No Spring:

```java
@RestControllerAdvice
class PortcullisErrors {

    @ExceptionHandler(PortcullisException.class)
    ResponseEntity<ProblemDetail> handle(PortcullisException e) {
        var response = ResponseEntity.status(e.status());
        if (e instanceof RateLimitExceededException limit) {
            response.header("Retry-After", String.valueOf(limit.retryAfter().toSeconds() + 1));
        }
        return response.body(ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(e.status()), e.getMessage()));
    }
}
```

## Auditoria

Toda decisão sobre um método protegido vira um `AccessEvent`: quem chamou, o quê, se passou e, se não passou, qual checagem barrou e por quê. Registre quantos `AuditListener` quiser:

```java
Portcullis.configure()
        .auditListener(new LoggingAuditListener())                         // System.Logger
        .auditListener(AuditListener.deniedOnly(event -> alerts.send(event))) // só as negações
        .install();
```

Um listener que lança exceção é logado e não muda a decisão. Sem nenhum listener, nenhum evento é criado.

## Performance

Medi de forma simples, sem JMH: uma chamada com login, role e posse custa entre 25 e 30 ns a mais que a mesma chamada sem proteção. Isso porque:

- as anotações de cada método são lidas uma vez, viram uma lista de regras já ligadas aos resolvers e ficam em cache
- depois disso não tem reflexão, nem busca em mapa de resolvers, nem cópia dos argumentos quando nenhuma regra precisa deles
- respostas 401, 403 e 429 não montam stack trace, o que deixa barato recusar muita requisição

Com Redis, o custo dominante passa a ser a ida e volta de rede de cada `@RateLimit` e `@Idempotent`.

## Módulos

| Artefato | Conteúdo | Dependências |
|---|---|---|
| `portcullis-core` | anotações, `AccessGuard`, rate limit e idempotência em memória | nenhuma |
| `portcullis-aspectj` | aspecto que aplica as anotações | `aspectjrt` |
| `portcullis-caffeine` | `CaffeineRateLimiter`, `CaffeineIdempotencyStore` | `caffeine` |
| `portcullis-redis` | `RedisRateLimiter`, `RedisIdempotencyStore` e adaptadores | Lettuce ou Jedis, o que você já usa |

## Build

```bash
mvn verify
```

Os testes do módulo Redis sobem um Redis com Testcontainers, então precisam de Docker.

## Licença

Apache 2.0. Veja o arquivo [LICENSE](LICENSE).
