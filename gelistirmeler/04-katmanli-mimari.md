# 04 — Katmanlı Mimari (Controller / Service / Repository)

## Amaç
Sorumlulukları ayrıştıran klasik katmanlı mimari. Her katman bir alttakine yalnızca **interface** üzerinden bağlanır.

## Akış
```
HTTP → Controller → Service → Repository → Oracle (stored procedure)
                       ↕            ↕
                  DTO          Model (Product)
```

## Katmanlar

### controller/ — `ProductController`
- `@RestController`, `@RequestMapping("/api/products")`
- Endpoint'ler: `GET /`, `GET /{id}`, `POST /` (201), `PUT /{id}`, `DELETE /{id}` (204)
- Girişte `@Valid @RequestBody ProductRequest`. İş mantığı yok; servise devreder.

### service/ — `ProductService` (interface) + `ProductServiceImpl`
- `@Service`, `@Transactional`, constructor injection.
- DTO ↔ `Product` dönüşümü burada.
- Bulunamayan kayıt / etkilenmeyen satır → `ResourceNotFoundException`.

### repository/ — `ProductRepository` (interface)
- Domain-merkezli metotlar: `findAll`, `findById`, `create`, `update`, `delete`.
- **JpaRepository değildir.** İmplementasyonlar stored procedure çağırır (bkz. `05-stored-procedure-veri-erisimi.md`).

### dto/
- `ProductRequest` — giriş, Bean Validation (`@NotBlank`, `@NotNull`, `@Min`).
- `ProductResponse` — çıkış, `from(Product)` statik mapper. Model dışarı sızdırılmaz.

### model/ — `Product`
- Alanlar: `id`, `name`, `price` (BigDecimal), `stock` (int). Lombok `@Data/@Builder`.
- `@Entity` (PRODUCTS tablosu) — JPA stored-procedure result mapping için; ama Spring Data repository yok.

### exception/
- `ResourceNotFoundException` → 404.
- `GlobalExceptionHandler` (`@RestControllerAdvice`) → `ProblemDetail`; validation hataları `errors` alanında (400).

## Tasarım ilkesi
Üst katmanlar implementasyon detayından bağımsızdır: veri erişimi in-memory → JPA → stored procedure değişti ama controller/service/dto/exception aynı kaldı.

## İlgili
- Veri erişim detayları: `05-stored-procedure-veri-erisimi.md`
