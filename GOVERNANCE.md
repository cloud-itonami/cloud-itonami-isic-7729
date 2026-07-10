# Governance

`cloud-itonami-7729` is an OSS open-business blueprint for community
household goods rental operations, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Household Rental Governor remains independent of the advisor.
- hard policy violations (an agreement outside verified disclosure
  scope, an unverified delivery/pickup, an unverified reconciliation
  record) cannot be overridden by human approval.
- every dispatch, sign-off and reconciliation path is auditable.
- sensitive renter and payment data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or disclosure-scope checks
- mishandling renter or payment data
- misrepresenting certification status
- failing to respond to safety incidents
