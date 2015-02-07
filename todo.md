# TODO list

## Report Engine / Formulae Evaluator

x Think about moving formatting out of AST and into Field

### AgencyFees

x DK: Should implicitly depend on `sum(field)`
x DK: Needs `.monthlyFees(field)` that simplifies: `.feesForAllMonths(sum(field)) * (impressions / sum(impressions))`
* Should maybe handle nested valid ranges for fees instead of consecutive only

### EvaluationCxt / ReportGenerator

* DK: Should be hierarchical:
    * Must include the explicit notion of time spans instead of instants
    * Bound (data) fields should be pushed-down/rolled-up
    * Must recompute derived fields at every level of hierarchy
    * Must handle multi-part keys
x Needs to compute footers -> probably client side
* Computation should not use mutable state
* Should not expose root-level functions to reduce name collisions?

### Charts

* Figure out how to serialise polymorphic class to RethinkDB so we can save/load subclasses of `Chart`
x Fill out `Chart` classes, which store config


## Report Grid UI

*xUse chart config classes to generate charts client side
* Fix filtering of report grid
x Show/compute footers (need formula evaluator in JS)
x Allow date range editing
* Allow trend reports (comparing two date ranges)
* Show active sort in column headers
