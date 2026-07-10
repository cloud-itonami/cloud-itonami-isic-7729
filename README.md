# cloud-itonami-7729

Open Business Blueprint for **ISIC Rev.5 7729**: renting and leasing
of other personal and household goods (furniture, appliances and
electronics rented to consumers, frequently under rent-to-own
arrangements).

This repository designs a forkable OSS business for community
household-goods rental: rental-purchase-agreement-scope and
total-cost-of-ownership-disclosure management, robotics-assisted
delivery/condition inspection and pickup, and rental/reconciliation
records — run by a qualified operator so a rent-to-own company keeps
its own disclosure and consumer-protection compliance history instead
of renting a closed rental-agreement platform.

## Scope note: household goods, not vehicles, recreational gear or tools

Distinct from `cloud-itonami-isic-7710` (motor vehicles),
`cloud-itonami-isic-7721` (recreational and sports goods) and
`cloud-itonami-unspsc-27` (construction tools): this repository is
deliberately scoped to personal and household goods -- furniture,
appliances and consumer electronics -- rented to individual
consumers, frequently under a rent-to-own structure. Rent-to-own
carries its own distinct legal regime, separate from ordinary
consumer credit/lending law: most US states have a specific
Rental-Purchase Agreement Act governing rent-to-own transactions as a
series of short-term rentals with a purchase option, rather than as
an installment loan, and the US Federal Trade Commission has issued
specific guidance requiring total-cost-of-ownership disclosure for
rent-to-own agreements.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a
**robot performs the physical domain work**. Here robots (delivery
and pickup logistics, condition inspection at delivery/return) operate
under an actor that proposes actions and an independent **Household
Rental Governor** that gates them. The governor never releases a
rental agreement or dispatches a delivery/pickup itself;
`:high`/`:safety-critical` actions (an agreement outside verified
disclosure scope, a delivery/pickup without a completed condition
check, a reconciliation record without verified evidence) require
human sign-off.

## Core Contract

```text
intake + identity + rental-purchase-agreement/disclosure scope + rental request
        |
        v
Household Rental Advisor -> Household Rental Governor -> match, rental record, or human approval
        |
        v
robot actions (gated) + delivery/inspection record + reconciliation record + audit ledger
```

No automated advice can release a rental agreement the governor
refuses, match a renter to an agreement outside its verified
disclosure scope, or publish a reconciliation record without governor
approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `7729`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/labor`](https://github.com/kotoba-lang/labor) — staff registration, dispatch, timesheet/follow-up contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
