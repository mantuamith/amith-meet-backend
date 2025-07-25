# Authentication Strategy for `meeting-service`

## Phase 1: Local JWT Verification (Initial Setup)

### Objective:

Secure endpoints in `meeting-service` using JWTs issued by `auth-service`. JWTs are verified locally using a shared secret.

### Required:

* Shared `jwt.secret` (same as used in `auth-service`)
* Add Spring Security and JWT libraries

### Steps:

1. **Add Dependencies in `pom.xml`**

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-api</artifactId>
  <version>0.11.5</version>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-impl</artifactId>
  <version>0.11.5</version>
  <scope>runtime</scope>
</dependency>
<dependency>
  <groupId>io.jsonwebtoken</groupId>
  <artifactId>jjwt-jackson</artifactId>
  <version>0.11.5</version>
  <scope>runtime</scope>
</dependency>
```

2. **Add to `application.yml`**

```yaml
jwt:
  secret: <shared-secret-from-auth-service>
```

3. **Create `JwtAuthenticationFilter`** to:

* Extract `Authorization` header
* Validate the JWT
* Populate `SecurityContext` with user ID

4. **Create `SecurityConfig`** to:

* Disable CSRF
* Require authentication for all endpoints except public ones
* Register `JwtAuthenticationFilter`

5. **Inject user identity in controller** via:

```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String userId = auth.getName();
```

## Phase 2: Add Feign/WebClient (Optional Extension)

### Use Cases:

* Get fresh user details from `auth-service`
* Validate revoked or expired tokens

### Strategy:

* Create Feign client:

```java
@FeignClient(name = "auth-service")
public interface AuthClient {
    @PostMapping("/internal/validate")
    UserInfo validateToken(@RequestHeader("Authorization") String token);
}
```

* Or use `WebClient`:

```java
webClient.post()
         .uri("http://auth-service/internal/validate")
         .header("Authorization", token)
         .retrieve()
         .bodyToMono(UserInfo.class);
```

## Phase 3: Centralized Gateway Validation (Future Upgrade)

### Use Cases:

* Use API Gateway to handle JWT auth (e.g., Spring Cloud Gateway, Kong, etc.)
* Services only trust gateway-provided headers (e.g., `X-User-ID`, `X-User-Role`)

### Benefits:

* Single place to manage auth
* No need to share JWT secret in each service
* Easier for logging/auditing and revoking tokens centrally

---

This approach is scalable, clean, and easy to build upon. Proceed with Phase 1 now, and use Phases 2 and 3 as needed based on future complexity and business needs.
