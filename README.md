# Portcullis

A portcullis is the heavy iron grille that drops down over a castle gate. Getting into the courtyard does not get you through it; the guard decides who passes. This library is that inner gate for your methods.

Being logged in does not mean you may touch that record.

Portcullis is a Java library I built around that idea. You annotate the method, and before it runs the library checks that someone is logged in, that this person has the right role or permission, that the requested resource belongs to them, that it is in the same tenant and that they have not gone over the call limit. It also makes sure a repeated request, like a payment resent after a timeout, runs only once.

```java
@GetMapping("/orders/{id}")
@OwnedBy(Order.class)
public Order get(@ResourceId @PathVariable Long id) { ... }
```

If the order does not belong to the logged in user, the call stops there with a 403. The method never runs.

## Why I built it

Most APIs are good at checking who is calling. The token is validated, the session exists, the gateway lets the request through. What is usually missing is the next question: does this specific record belong to whoever is asking? When nobody asks it, changing the id in the URL is enough to read someone else's data. There is nothing clever about it, and it is one of the most common security problems in APIs.

The fix is simple, but it has to be in every endpoint that takes an id, with no exceptions. A rule that depends on being remembered in every endpoint will be forgotten in one of them. It usually ends up scattered: an `if` in the controller, another in the service, each written differently, and looking at the method you cannot tell whether it is protected.

I wanted that rule declared on the method itself, with an annotation, so that forgetting it shows up in code review. And I wanted it without tying myself to a framework. So Portcullis follows a few rules:

- the core depends on nothing; interception is done with AspectJ, so it works the same in Spring, Micronaut, Quarkus or plain Java
- the ownership rule is a plain Java function that you write and test like any other code
- low cost: annotations are read once per method, and after that there is no reflection per call
- when in doubt, deny: a misconfigured annotation or a failing resolver blocks the call

## What it does not do

Portcullis does not authenticate anyone: it uses the user your framework has already authenticated. It also does not deal with HTTPS, input validation, CORS, CSRF or firewalls. It is the last check before the method runs, the one closest to the data.

## Full example

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

A few things this example shows:

- an annotation on the class applies to all its public methods, and one on the method replaces the one on the class
- `bypassRoles` lets an admin through without owning the resource
- when the method takes more than one id, each `@ResourceId` says which type it is
- if the id comes inside the request body, the DTO implements `HasResourceId`:

```java
record UpdateOrder(Long orderId, String note) implements HasResourceId<Long> {
    public Long resourceId() { return orderId; }
}
```

And the configuration, done once at application startup:

```java
Portcullis.configure()
        .principalProvider(currentUser)                                 // where the logged in user comes from
        .ownership(Order.class, (SecurityPrincipal user, Long id) -> orders.existsByIdAndOwnerId(id, user.id()))
        .ownership(Item.class, (SecurityPrincipal user, Long id) -> items.isOwnedBy(id, user.id()))
        .auditListener(new LoggingAuditListener())
        .install();
```

## Annotations

| Annotation | Where | What it requires |
|---|---|---|
| `@Authenticated` | method, class | someone logged in |
| `@RequiresRole({"ADMIN", "SUPPORT"})` | method, class | any of the roles (`match = Match.ALL` for all of them) |
| `@RequiresPermission({"orders:read"})` | method, class | all of the permissions (`match = Match.ANY` for any of them) |
| `@OwnedBy(Order.class)` | method | the `@ResourceId` resource belongs to the user |
| `@SameTenant(Invoice.class)` | method | the `@ResourceId` resource is in the user's tenant |
| `@RateLimit(requests = 60)` | method, class | at most N calls per user in the window |
| `@PublicAccess` | method | ignores login, role and permission set on the class |
| `@ResourceId` | parameter | marks the id used by `@OwnedBy` and `@SameTenant` |
| `@Idempotent` | method | the same repeated request runs only once |
| `@IdempotencyKey` | parameter | marks the idempotency key sent by the client |

Anything that needs a user already implies `@Authenticated`. The checks run in this order and stop at the first one that fails: login, rate limit, roles, permissions, tenant, ownership. I put the cheapest ones first, so a denied call never gets as far as querying the database.

Only public, non static methods are intercepted. Private methods and lambdas inside an annotated class are left out.

## Installation

Requires Java 25.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-aspectj</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Then the weaving. I use the mode where `javac` compiles as usual and `ajc` weaves the already compiled classes. That way there is no conflict with Lombok, MapStruct or any other annotation processor:

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

If you would rather not touch the build, you can use load time weaving by running the application with `-javaagent:aspectjweaver-1.9.25.jar`. The jar already ships `META-INF/aop.xml`.

If your project already has its own interception mechanism, you can skip AspectJ and call the check directly:

```java
Portcullis.guard().check(method, targetClass, arguments);
```

## Who is logged in

The library does not authenticate anyone. It asks a `PrincipalProvider` who is logged in, and you wire that to whatever your framework already has. The user is a `SecurityPrincipal` with `id()`, `roles()`, `permissions()` and `tenantId()`. You can implement the interface on your own user type or use `SimplePrincipal.of("42").withRoles("ADMIN").withTenant("acme")`.

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

With no framework at all, the default is to read from `PortcullisContext`, which you fill in a filter:

```java
try (var scope = PortcullisContext.bind(principal)) {
    chain.doFilter(request, response);
}
```

## Rate limit

`@RateLimit` counts calls per user on each method. Anonymous calls share a single counter. The algorithm is a token bucket: with `requests = 60` and a one minute window, a person can make 60 calls in a row, and then gets a new one every second.

```java
@RateLimit(requests = 60)                                         // 60 per minute
public List<Order> list() { ... }

@RateLimit(requests = 3, window = 10, unit = ChronoUnit.MINUTES)  // 3 every 10 minutes
public void resendConfirmationEmail() { ... }

@RateLimit(requests = 100, key = "search")                        // both methods share the same limit
public List<Product> searchByName(String name) { ... }

@RateLimit(requests = 100, key = "search")
public List<Product> searchByTag(String tag) { ... }
```

When the limit is exceeded you get a `RateLimitExceededException` (429) with `retryAfter()`, which goes straight into the `Retry-After` header.

Where the counters live depends on what you configure:

| Option | Module | Covers |
|---|---|---|
| `InMemoryRateLimiter` | ships with core, the default | a single instance |
| `CaffeineRateLimiter` | `portcullis-caffeine` | a single instance, with bounded memory |
| `RedisRateLimiter` | `portcullis-redis` | any number of instances |

If the application runs on more than one instance, use Redis. With the other two, each instance counts on its own, and three instances with a limit of 60 let 180 through.

### Caffeine

Good for a single instance when you want to be sure memory does not grow without bound, for example under an attack with many different ids. Counters expire on their own after a window without use.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-caffeine</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
Portcullis.configure()
        .rateLimiter(CaffeineRateLimiter.create())               // up to 100 thousand counters
        // .rateLimiter(CaffeineRateLimiter.withMaximumSize(10_000))
        .install();
```

### Redis

The counter lives in Redis, so every instance sees the same limit. Each call is one atomic Lua script, a single round trip to Redis. The script uses Redis's own clock, so clock differences between machines do not mess up the count. Counters expire on their own after an idle window.

```xml
<dependency>
    <groupId>io.github.sfesantos</groupId>
    <artifactId>portcullis-redis</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The module does not bring a Redis client. It uses the one your application already has, and I left ready made adapters for Lettuce and Jedis.

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

Spring Boot with `spring-boot-starter-data-redis` already uses Lettuce underneath. You can reuse the client Spring configured, with host, password and TLS:

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

With Redis Cluster, pass `RedisClusterClient.connect().sync()` to `LettuceRedisScripts`. Each counter is a single key, so it works with no extra configuration.

For another client, implement `RedisScripts`: it has two methods, `evalSha` and `eval`.

What if Redis goes down? By default the call is denied, in line with the rest of the library. If availability matters more to you than the limit, you can let it through:

```java
RedisRateLimiter.builder(new LettuceRedisScripts(connection.sync()))
        .onFailure(OnRedisFailure.ALLOW)   // logs a warning and lets the call through
        .keyPrefix("myapp:rl:")            // default: portcullis:rl:
        .build();
```

## Idempotency

Think of a money transfer. The client sends the request, the network drops before the response comes back, and it tries again. On the server, the first one had already gone through. Without care, the money leaves twice.

The usual fix is for the client to send a unique key per operation, normally in the `Idempotency-Key` header, and for the server to remember what it already did with it. That is what `@Idempotent` does:

```java
@PostMapping("/transfers")
@Idempotent
@OwnedBy(Account.class)
public Transfer transfer(@ResourceId @RequestParam Long from,
                         @RequestBody TransferRequest request,
                         @IdempotencyKey @RequestHeader("Idempotency-Key") String key) { ... }
```

What happens to each request:

- first time with the key: the method runs and the result is kept for 24 hours
- same key after that: the method does not run again, and the client gets the same result as the first time
- same key while the first one is still running: 409
- if the method throws, the key is released and the client can try again
- no key: 400; with `@Idempotent(required = false)` the method just runs normally

The key is scoped per user and per method. Two users sending the same key do not get mixed up, and nobody can read someone else's stored response. Access checks run first, so a denied call does not take the key.

The retention time is set on the annotation:

```java
@Idempotent(ttl = 7, unit = ChronoUnit.DAYS)
```

If you do not want the key in the method signature, you can read the header in a single place. In Spring:

```java
Portcullis.configure()
        .idempotencyKeyProvider(() -> Optional.ofNullable(RequestContextHolder.getRequestAttributes())
                .map(a -> ((ServletRequestAttributes) a).getRequest().getHeader("Idempotency-Key")))
        .install();
```

Then `@Idempotent` on the method is enough. A parameter with `@IdempotencyKey`, when present, takes priority.

### Where results are stored

The same three places as the rate limit, with the same rule: if you have more than one instance, use Redis. With memory or Caffeine, a retry that lands on another instance runs again.

```java
Portcullis.configure()
        .idempotencyStore(RedisIdempotencyStore.of(redisScripts, new JacksonResultCodec(objectMapper)))
        // or CaffeineIdempotencyStore.create(), or nothing to keep the default InMemoryIdempotencyStore
        .install();
```

Redis stores text, so the method's result has to be converted. The library does not pick a format for you: you pass a `ResultCodec`. With Jackson it looks like this:

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

`redisScripts` is the same `LettuceRedisScripts` or `JedisRedisScripts` used for the rate limit.

While the method runs, the key is reserved for a short lease, 1 minute by default. If the instance dies halfway, the key frees itself after that instead of staying locked for the whole ttl. If any of your methods take longer than that, raise it:

```java
Portcullis.configure()
        .idempotencyLease(Duration.ofMinutes(5))
        .install();
```

One limitation: the library does not compare the request body. If the client reuses the same key with different content, it gets the response from the first call. Generate a new key for each operation.

## Errors

Every exception extends `PortcullisException` and has `status()`, so a single handler covers them all:

| Exception | Status | When |
|---|---|---|
| `UnauthenticatedException` | 401 | nobody logged in |
| `ForbiddenException` | 403 | role, permission, tenant or ownership; `check()` says which |
| `InvalidIdempotencyKeyException` | 400 | `@Idempotent` with no key, or a key longer than 128 characters |
| `IdempotencyConflictException` | 409 | the same key is still being processed |
| `RateLimitExceededException` | 429 | over the limit; `retryAfter()` says how long to wait |
| `PolicyDefinitionException` | 500 | misused annotation or unregistered resolver |

In Spring:

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

## Auditing

Every decision on a protected method becomes an `AccessEvent`: who called, what, whether it was allowed and, if not, which check blocked it and why. Register as many `AuditListener`s as you like:

```java
Portcullis.configure()
        .auditListener(new LoggingAuditListener())                         // System.Logger
        .auditListener(AuditListener.deniedOnly(event -> alerts.send(event))) // denials only
        .install();
```

A listener that throws is logged and does not change the decision. With no listener at all, no event is created.

## Performance

I measured it in a simple way, without JMH: a call with login, role and ownership costs between 25 and 30 ns more than the same call without protection. That is because:

- each method's annotations are read once, turned into a list of rules already bound to their resolvers, and cached
- after that there is no reflection, no lookup in a resolver map, and no copy of the arguments when no rule needs them
- 401, 403 and 429 responses do not build a stack trace, which keeps turning away lots of requests cheap

With Redis, the dominant cost becomes the network round trip for each `@RateLimit` and `@Idempotent`.

## Modules

| Artifact | Contents | Dependencies |
|---|---|---|
| `portcullis-core` | annotations, `AccessGuard`, in memory rate limit and idempotency | none |
| `portcullis-aspectj` | aspect that enforces the annotations | `aspectjrt` |
| `portcullis-caffeine` | `CaffeineRateLimiter`, `CaffeineIdempotencyStore` | `caffeine` |
| `portcullis-redis` | `RedisRateLimiter`, `RedisIdempotencyStore` and adapters | Lettuce or Jedis, whichever you already use |

## Build

```bash
mvn verify
```

The Redis module tests start a Redis with Testcontainers, so they need Docker.

## License

Apache 2.0. See the [LICENSE](LICENSE) file.
