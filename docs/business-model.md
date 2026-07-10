# Business Model: Community Household Goods Rental Operations

## Classification
- Repository: `cloud-itonami-7729`
- ISIC Rev.5: `7729` — renting and leasing of other personal and
  household goods
- Social impact: consumer protection, shared-asset access, financial
  inclusion

## Customer
- independent/community rent-to-own operators needing an auditable
  disclosure and rental-agreement platform
- renters needing verifiable delivery, condition and reconciliation
  records
- regulators needing verifiable Rental-Purchase Agreement Act and
  FTC total-cost-of-ownership disclosure compliance records
- programs that cannot accept closed, unauditable household-goods-
  rental platforms

## Offer
- rental-purchase-agreement and disclosure-scope management
- robotics-assisted delivery/condition inspection and pickup
- rental, delivery and reconciliation records
- renter billing and disclosure records
- role-based access and immutable audit ledger

## Revenue
- self-host setup fee
- managed hosting subscription per rental location
- support retainer with SLA
- delivery/condition-inspection robot integration and maintenance

## Trust Controls
- a robot action the governor refuses is never dispatched
- safety-critical actions (an agreement outside verified disclosure
  scope, a delivery/pickup without a completed condition check, an
  unverified reconciliation record) require human sign-off
- agreements cannot be released outside verified disclosure scope
- reconciliation records require verified evidence
- sensitive renter and payment data stays outside Git
