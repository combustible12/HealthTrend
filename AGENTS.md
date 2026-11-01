# HealthTrend development entry

Before changing code, tests, UI, storage, release metadata, or documentation, read
[`docs/PRODUCT_RULES.md`](docs/PRODUCT_RULES.md) in full. It is the product-level
source of truth and points to the detailed domain rules that also apply.

When a new user-confirmed requirement is accepted:

1. update `docs/PRODUCT_RULES.md` in the same change;
2. update the relevant detailed document when the rule belongs to a specialist domain;
3. add or update focused regression coverage;
4. preserve old data and backups unless the user explicitly approves a destructive migration;
5. do not publish an APK unless the user explicitly requests publication.

If implementation and documentation disagree, stop and reconcile them before release.
The newest explicit user-confirmed rule wins over older notes; record the superseded rule
instead of silently leaving contradictory instructions in the repository.
