# Membership behavior

- Subscriptions debit points and redeem vouchers in one transaction. A fully discounted subscription does not create a zero-value debit. Paid ledger entries reference the subscription ID.
- Role synchronization derives the desired membership roles from all currently active subscriptions. Expiring an older subscription must not remove a role still granted by another subscription. Non-membership roles are untouched.
- Editing a plan's role synchronizes its existing subscribers in the same transaction. Editing its price or duration only affects future purchases; existing expiry timestamps remain unchanged.
- New lifetime subscriptions store a null end date. Active subscriptions with a null end date remain valid and are excluded from expiry processing. Existing purchases are not retroactively converted when plan settings change.
- The expiry job runs daily at 00:00 in Asia/Ho_Chi_Minh by default. `MEMBERSHIP_EXPIRY_CRON` can override this schedule. Exam access checks the actual expiry on each access; role cleanup can lag until the next midnight execution.

Run regression tests from `backend`:

```sh
mvn -pl membership -am test -Dtest=MembershipRegressionTest -Dsurefire.failIfNoSpecifiedTests=false
```
