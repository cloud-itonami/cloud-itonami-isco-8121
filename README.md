# cloud-itonami-isco-8121

Open Occupation Blueprint for **ISCO-08 8121**: Metal Processing Plant Operators.

This repository designs a forkable OSS business for an independent metal processing plant operator: a monitoring robot performs temperature sensing and sampling near hot processes under a governor-gated actor, so the operator keeps their own process and quality records instead of renting a closed plant-control SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a furnace/rolling-mill monitoring robot performs temperature sensing and sample collection near hot processes under an actor that proposes
actions and an independent **Metal Plant Governor** that gates them. The governor never
dispatches hardware itself; `:high`/`:safety-critical` actions (such as
operating near molten metal, furnaces or rolling equipment) require human sign-off.

A live sample of the operator console (robotics safety console, shared template) is rendered in [docs/samples/operator-console.html](docs/samples/operator-console.html) — pure-data HTML output of `kotoba.robotics.ui`.

## Core Contract

```text
production order + process specification + safety envelope
        |
        v
Process Advisor -> Metal Plant Governor -> process/monitor, or human sign-off
        |
        v
robot actions (gated) + operating records + audit ledger
```

No automated advice can dispatch a robot action the governor refuses, suppress
an operating record, or disclose sensitive data without governor approval and
audit evidence.

## Capability layer

Resolves via [`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation)
(ISCO-08 `8121`). Required capabilities:

- :robotics
- :telemetry
- :dmn
- :bpmn
- :audit-ledger

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Reference implementation

`src/metal_plant/{store,governor}.cljc` is a minimal but real
implementation of the Core Contract above (pure cljc, no external deps):

- `metal-plant.store` — `Store` protocol + `MemStore`: furnaces, batches,
  temperature readings, quality samples. A reading/sample can only be
  recorded against a registered batch on a registered furnace (batch
  provenance).
- `metal-plant.governor` — `MetalPlantGovernor`: `assess` gates a
  proposal against the batch/furnace env. Hard invariants force `:hold`
  (no batch, direct-write instead of `:propose`, or a temp-reading
  exceeding the furnace's `max-safe-temp-c` at below `:high`
  safety-class); an overtemp reading always requires `:high`+
  safety-class and thus `:human-approval` — it can never be
  auto-approved, only recorded and escalated; low-confidence proposals
  also escalate.

```bash
clojure -M:test   # 7 tests, 13 assertions, green
```

This is what backs this repo's `:maturity :implemented` entry in
[`kotoba-lang/occupation`](https://github.com/kotoba-lang/occupation) —
the 13th `cloud-itonami-isco-*` occupation to reach that tier, after
`cloud-itonami-isco-6112`, `-2221`, `-7126`, `-4321`, `-9312`, `-5322`,
`-8332`, `-1321`, `-3253`, `-6210`, `-5223` and `-7231` (ADR-2607012000).

## License

AGPL-3.0-or-later.
