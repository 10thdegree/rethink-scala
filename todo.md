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

## GENERAL TASK LIST

* Polymorphic type deserialisation (VM-1)
* Database cache (VM-2)
* API (VM-3)
	* Marchex
	* Dart Display
	* AdWords
	* Bing
	* GA
* Ongoing API code review (DK-*)
* DS façades (DK-3/VM)
* Integrate Scala-js (MO-1)
* UI
	* User/account management (MO)
	* Account fees management (MO)
	* Dart DS management (MO)
	* Report configuration
		* Report templates (MO)
		* Report views (MO)
		* Report bindings (MO)
	* Report viewing
		* Report viewing within admin (MO/DK)
		* Enable column filters (DK/MO)
		* Better (more interactive) graphs/charts (DK)
		* Use same logic for footers as for report generation (via scala-js) (DK)
		* Trend reports, i.e. selecting multiple date ranges (DK)
* Engine
	* Media type handling, e.g. video vs. banner metrics (DK-1)
	* Flush out key-selector API (DK-2)
	* Arbitrary key collapsing, i.e. view report for any partial key (DK)
	* Support for non-additive fields, including for footers, e.g. "avg pos", "media cost" (DK)
	* Trend report support (DK)