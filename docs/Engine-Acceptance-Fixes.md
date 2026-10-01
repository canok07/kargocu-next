# Engine acceptance fixes

## Scope

Engine-only correctness work on `cc/m2-m7-engine`. No Android UI, Android persistence, router/bridge/config, dependency, toolchain, or `GameStore` changes were made.

## Correctness changes

- The reducer validates incoming state before command dispatch. Invalid package ranges, negative cash, unknown catalog references, invalid offer/model ranges, and one-sided vehicle/driver assignments return typed `StateInvariantViolation` rejections without mutation. Command handlers retain specific typed `NotFound` and domain rejections for valid-state command errors.
- Daily offer generation persists `offersGeneratedGameDay`. A second command on the same game day is an idempotent no-op even after all offers are accepted. `AdvanceDay` closes elapsed days and generates the next board once.
- Generated boards deterministically replace one generated slot only when necessary to guarantee a starter-compatible and affordable job. The policy consumes no additional RNG. The 100 EUR rental panel van remains unsellable.
- Acceptance snapshots and reserves maximum obligatory fuel, rental, penalty, owned-vehicle upkeep, and driver wage for every calendar game day intersected by the work interval. Failed acceptance does not consume the offer, RNG, or assignments.
- Worked days use a half-open `[startedAt, completionAt)` interval. Owned-vehicle upkeep and assigned-driver wages are charged once per worked calendar day, never per job and never on idle days. Reserved jobs settle while available cash is zero.
- Settlement uses only accepted-job snapshots. Reservation and refund remain explicit ledger transfers; they are excluded from business revenue and operating costs. Daily revenue includes only `REVENUE`; operating costs exclude escrow, purchases, sales, and opening cash.

## Persistence

New serialized fields have safe defaults: `GameState.offersGeneratedGameDay`, `VehicleState.upkeepChargedGameDays`, and the upkeep/wage/penalty snapshot fields in `JobInvoice`. Save/state versions remain unchanged because legacy payloads decode with defaults. The existing v1 migration fixture remains unchanged and passes. In-flight accepted jobs round-trip and settle identically after catalog repricing.

## Test evidence

Command: `./gradlew -PgameOnly=true :game:test`

Final JUnit XML total: 72 tests, 0 failures, 0 errors, 0 skipped across 10 suites. This preserves the prior 55 tests and adds the unchanged 10-method independent acceptance artifact plus 7 focused behavior regressions.

The public-command simulation uses `newGame`, generated offers, normal acceptance/completion, purchase, hire, reciprocal assignment, simultaneous deliveries, day advancement, `SaveCodec` save/load, and continued play. It does not copy state, rewrite offers, inject money, or use a modified catalog.

| Seed | Day 14 jobs | First owned after jobs | Day 14 cash | Day 30 jobs | Day 30 cash |
|---:|---:|---:|---:|---:|---:|
| 7 | 37 | 9 | 287803 cents | 79 | 711901 cents |
| 99 | 39 | 10 | 289154 cents | 83 | 744996 cents |
| 20260930 | 42 | 10 | 330523 cents | 90 | 828943 cents |

All six runs own a vehicle, hire and reciprocally assign a driver, start two simultaneous deliveries while retaining one independent player-manual rental job, pass save/load continuation, keep nonnegative cash, and avoid soft lock. No catalog balance values required adjustment.

## Fixture repairs

- The capability boundary fixture now uses the package type's valid minimum weight so it reaches the intended capability rejection.
- The day-4 driver fixture now has matching day-4 game time and advances by a duration delta.
- Synthetic parallel jobs remain narrow ordering/idempotency fixtures; zero reservation/refund rows are omitted so ledger validation remains meaningful.

## Unresolved criteria

None within the requested engine scope. Android persistence and UI were intentionally not implemented or validated.
