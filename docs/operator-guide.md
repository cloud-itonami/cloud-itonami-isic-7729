# Operator Guide

## First Deployment
1. Register operator, rental locations, rental-purchase-agreement/
   disclosure scope, staff and delivery robots.
2. Import existing rental and billing history.
3. Run read-only disclosure-scope and delivery/condition-inspection
   robot mission dry-runs.
4. Configure safety-class allowed sets and human sign-off paths.
5. Publish a dry-run reconciliation record and audit export.

## Minimum Production Controls
- rental-purchase-agreement/disclosure-scope validation before any
  agreement release
- governor gate on every robot action before dispatch
- human sign-off for :high/:safety-critical actions (an agreement
  outside verified disclosure scope, an unverified delivery/pickup, an
  unverified reconciliation record)
- evidence-backed reconciliation records
- audit export for every dispatch, sign-off and reconciliation record
- backup manual household-goods-rental process

## Certification
Certified operators must prove robot-safety integrity, disclosure
discipline, evidence-backed reconciliation records and human review
for dispatch-affecting actions.
