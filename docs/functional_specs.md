[TOC]

# BRAVO - Reporting (Functional)

## Overview

The goal of URP (unified reporting platform), is to take metrics from third parties, such as Dart (which in turn gets numbers from AdWords and Bing) and Marchex, and generate *unified* reports on them. Reports show metrics for some entities of interest (such as ad campaigns, or ad publishers), with various numbers (metrics) for each, including impressions, clicks, conversions, etc. One of the major motivating reasons behind the creation of URP is adding the ability to distribute fees across report metrics, as Dart does not do this for you today, making URP not only a helpful unified reporting engine, but an essential component in 10TH DEGREE’s services offering. As such, the mechanism behind creating fees and applying them must be robust or it precludes the neccessity of URP entirely.

### Basic Features

Ability to aggregate and coalesce data from a variety of sources (only those marked with "phase 1" are targeted for initial release):

* AdWords
* Bing
* Dart (Phase 1)
* Google Analytics website reporting
* Social media tracking
* Phone tracking
	* Marchex (Phase 1)
* SEO reporting

In addition, the system needs to be able to:

* smartly manage fees
* output screen and printable reports
* schedule and email reports (PDF/HTML5)
* expose client facing login with 24/7 access
* use a responsive design for mobile access
* show different reports to differents users based on permissions
* mimimise repetitive configuration across accounts
* group accounts that all use the same reports
* cache underlying data to prevent repeated access
* easily compare any report to same for different interval (trend)

### Fees

There are two categories of fees we need to work with, **serving** and **agency**. There should be default value for fees within each category if no account level values are specified; this default should not expire.

#### Serving Fees

Fees incurred by utilizing a 3rd party ad server (e.g. DoubleClick) to track conversions across multiple channels, publishers, and placements. There are currently three kinds of serving ads, PPC (search), banner/flash ads (display), and video ads (display), but there may be others in the future. However, regardless of type, there are only two possible fees for each:

* CPM Fee (cost per 1000 impressions)
* CPC Fee (cost per click)

CPM is always computed using impressions, and CPC always using clicks.

For example:

* PPC ads
    * CPC Fee: E.g. $0.05 (per click)
* Banner ads / HTML5 ads
    * CPM Fee: E.g. $0.25 (per 1000 impressions)
    * CPC Fee: E.g. $0.05 (per click)
* Video ads
    * CPM Fee: E.g. $1.00 (per 1000 impressions)

In addition, valid date ranges for when to apply the sets of fees is needed. (e.g. 01/2001-01/2010) This allows a shcedule to be inputted for automatically incrementing fees.

Note that in the case both fees (CPM and CPC) apply, both must be added when the serving fee of that kind is used in a given calculation.

In the future, CPM may shift to a percentage of total spend instead, making it more like agency fees (see below).

#### Agency Fees

Fees charged by us for our services. For this we bill in tiers, usually charging a lower percentage bracket as more dollars are spent. However, there is also a minimum fee charged if not enough ads are served. Thus, we minimally have a table of:

* Range (of total spend, e.g. "0.00-10000.00")
* Percentage of Spend (e.g. "15%")

So we know for what amount of total spend, what percentage of that total spend to apply. However, if there was too little total spend, we want to default to a minimum management fee instead:

* Monthly Management Fee: E.g. $750

Similarly to serving fees, a valid date range for when to apply these fees is needed. (e.g. 01/2001-01/2010)

Like how serving fees has different sets of fees (e.g. "banner" and "video"), agency fees can have different sets as well, e.g. "display" and "search".

## Reports

Ultimately, a report (viewed by a user within the system) is composed of one or more rows and one or more columns, forming a grid with the entities of interest (e.g. campaign name, or publisher name) forming rows, and the metrics/stats forming columns. 

In order to create such a report, two things must happen:

1. Data must be provided from one or more *data sources*, and
2. There must be a configuration, or a *template*, for the report that specifies:
	1. what fields are present in the final report, and
	2. how to pivot the source data into the fields defined above.

Reports can optionally also have visual elements, such as pie, line, or bar charts.

### Data Sources

In order to provide data for a report, one or more data sources must be available.

Currently, only two data source types are needed:

1. Dart
2. Marchex

An instance of a data source is essentially responsible for returning a matrix of "reportable entities" with attributes. URP is designed around having metrics for specific datetimes and reportable entities (such as "ad campaigns" or "ad publishers"); therefore a data source must provide information on these two things so that its data can be combined with other data source data.

The system should be able to combine data from multiple data sources, including the following cases: 

1. when different data sources provide different attributes for the same entities
2. when different data sources provide the same attributes for the same entities (sum these values)
3. when different data sources provide the same attributes for different entities (e.g. a Bing data source returns stats for Bing campaigns, and an AdWords data source returns stats for AdWords campaigns)

However, different data sources will not always provide the same granularity of metrics; the system must still be able to reconcile this, coalescing the data into the same report. For example, Marchex campaigns may only exist for publisher, but those numbers may need to be distributed across all the ad campaigns within each. In such cases, the system must provide a means for aligning data sources that provide different levels of granularity on reportable entities; further, a dependant field to be used in distributing the metrics provided by less granular data source(s).

#### Dart

TODO

#### Marchex

TODO

#### Examples

##### Example 1: Multiple data sources, with varying granularity

Let's say we have the following data sources: Bing, AdWords, and Marchex. Bing and AdWords provide metrics by ad campaign. For Marchex, for bing-sourced campaigns, we only have a single phone number, whil for AdWords, we thought through the setup better and have one number per ad campaign. This means that for Bing, we need to take those phone calls and distribute all the calls proportionally (e.g. by # of clicks) across the Bing ad campaigns; for AdWords, on the other hand, we have a one-to-one mapping for ad campaigns with Marchex.

If we alter the example to instead want to report on keywords rather than ad campaigns, the Marchex numbers for both Bing and AdWords no longer match one-to-one. In this case, all of the numbers will need to be proportionally distributed; for Bing, this means taking the publisher level metrics from Marchex and distributing them across all Bing ad campaigns; for AdWords, this means taking the metrics from Marchex for each ad campaign, and distributing them across the keywords.

### Templates

Given that many clients will use the same reports, it is crucial that the system allow for configuring a report once and for it to be used by many clients; updating it should likewise apply to all clients using it.

A report template should be composed of fields, which will form the columns of the report displayed to the user. Fields should be specified in a manner similar to inputting formulae in an Excel spreadsheet. This makes defining a report template incredibly flexible. Some fields will come from data source attributes, and some will be formulae, e.g.:

```
#!groovy
// Serving fees are simple products:
servingFees = servingFees(adType).cpc(clicks) + (servingFees(adType).cpm(impressions)

// During evaluation, the engine knows the time-span for each row,
// so it can implicitly compute the agency fees.
agencyFees = agencyFees("display").summedMonthlyFees(sum(impressions)) * (impressions / sum(impressions))

// We could introduce a shorthand for the above:
// (NOTE that it would still implicitly rely on sum(impressions) because
// we need to know if we are adding in the base monthly fee or percentile)
agencyFees1 = agencyFees("display").monthlyFees(impressions)

// Combined field with all the fees + spend:
spendWithBothFees = spend + servingFees + agencyFees
```

Based on user permissions, different reports should be visible to the user.

Also, it may be desirable, based on user permissions, for fields in the report to be hidden/shown; this allows administrators to view reports with and without the agency/serving fees included, but for clients, to only show them included.

There must be a way to specify the formatting for fields, so that money can be formatted as money, whole numbers without decimal places, and fractional numbers with them (and with a configurable amount of precision).

### Viewing Reports

In the UI, the user selects from a list of reports they have permissions for, selects a date range (30 days, etc.) and then the system generates that report; hitting the data sources as needed to get the underlying data. 

#### Comparison (Trend) Reports

Comparison reports work exactly the same way, except the user either:

1. selects two or more date ranges (this may be done automatically, such as selecting the previous time period of the same type, e.g. 30 days, or last month), or
2. selects two or more accounts to compare to one another.

### Report Caching

Report data should be cached to prevent requesting the data repeatedly from the data sources.  

Each data source should also be schedulable to run at defined intervals (e.g. every day at 3am). We cache data from data sources rather than just caching the generated reports, because it allows us:

1. to reuse the same underlying data from a data source in multiple reports, and
2. to modify the report template and regenerate the report *without* having to re-request data from the data source.

## Phase 1.1

### Daily Reports

Upon completion of the existing customer facing reports, URP2 will need to deliver the daily report which is effectively and operational scorecard of every account. It is a report that combines two key measurement criteria:

1. MTD Budget vs. MTD Actual Spend
2. Rolling 7 Day Metrics (e.g. CTR, CPC, CPL) vs. Prior Period ROlling 7 Day Metrics

#### MTD Budget vs. MTD Actual Spend

This will require interacting with *Bravo Billing* to get the approved budget for each client account. Once we have the *budget* for a client, the report will take the monthly budget and divide it by the number of days in the month to get the on-track MTD budget. This will be compared to the actual spend (`media cost + ad serving + agency fees`) to see how close we are to being on budget. The report should highlight in RED any accounts off by more than 5%, yellow all accounts from 3-5%. These ranges should be configurable by account and a great feature would be that the ranges change relative to where we are in the month. Being off by 15% on day 2 isn't as bad as being off by 15% on day 20.

#### Rolling N Day Metrics (e.g. CTR, CPC, CPL) vs. Prior Period ROlling N Day Metrics

The N day period is configurable, i.e. there are several options to choose from like 7/14/30 day.

# BRAVO - Billing (Functional)

## Overview

The billing portion of Bravo should enable administrators to prepare MAFs (see below for definition) and for clients to review, potentially edit, and approve. Once a MAF has been approved (i.e. e-signed), the budgeted amounts can be used by other parts of the BRAVO system to spend the money. After a month has passed, viewing a MAF should optionally show the actual spend amounts (queried from another part of BRAVO) for those previous months. In the case that actuals were below/above the budget, the system should adjust following months so that spendable budgets include this +/-. A feature for providing refunds to clients must also be present.

Using the [Quickbooks API](https://developer.intuit.com/docs/0025_quickbooksapi/0005_introduction_to_quickbooksapi), invoices should be automatically created in QuickBooks.

## Accounts

The system is composed of accounts for which MAFs should be managed. Users can be assigned to one or more accounts.

## Users

The billing system has two types of users, administrators (which administer 1+ accounts) and clients (which access 1+ accounts).

### Administrators

* Can generate/edit proposed MAFs for an account
* Can approve modified MAF from client user
* View list of accounts with approval statuses
* View list of accounts' invoice statuses

Administrators need 2 views, a dashboard to view current status of approvals for upcoming months' MAFs, and also an account level view similar to the client view of an account with more functionality.

### Clients

* View proposed MAF
* Can respond to generated MAFs: approve, deny, modify
* View MAF previous months with actuals & future budget amounts
* Request refunds

## Media Authorization Form (MAF)

A MAF is a form that details a set of budgets (e.g. search, display, and video) for a period of N months; this form is used to receive client permission to spend the budgetted money.

### Generation

Create a MAF for an account for N months and set monthly budget for each budgeted item (will default values across all months with option to modify specific month)

Input **budgeted items**, e.g.:

1. search
2. display
3. pre-roll
4. ...
 
* Button should be available for sending "approval needed" notification to client users for account.

### Approval

Approval happens by a client user. They log in and see the MAF for future months, where they can either decline, modify, or approve it.

#### Declined or Modified

If client declines or modifies, notificaiton should be sent to account administrator(s). In the case of declining, client user must provide a textual reason (e.g. "this isn't what we discussed and is too high", etc.)

#### Approved

Account user accepts and e-signs MAF.

## Handling Bugdets and Actuals: Invoice Creation, Balance Rollover, and Refunds

Invoice creation, balance rollover (actuals - budgetted), and refunds are all interrelated, and must be handled together, as explained below.

X day of the N month generates invoice for M+1 month, if M+1 month has approved budget. Monitors new approved budgets till end of month generating invoices.

### Invoice Creation

Invoices should be generated for a given month M prior to entering that month, i.e. generated during month M-1, with enough time for the client to approve the invoice so we can use those funds. The generation of the invoice is not dependant on the previous month, but the spendable budget (approved budget + rollover amount) used by reporting system is.

### Balance Rollover (Tracking Over/underspending)

Actuals as reported by reporting system for a given month M may vary up to N days through the following month M+1. Thus, we retroactively calculate the balance that must be rolled over during a month M for the previous month M-1. However, since we are within a given month M already, the earliest we can use this rolled over amount is the following month, M+1. We therefore store the sum of `budgeted - actual` for month M-1 into month M+1. This sum may be positive or negative, depending on how spending went during month M-1. Therefore any given month M will only possibly ever have an impact on the budget for month M+2, as actuals for that month M are not known until partway through M+1.

#### Handling Rollover in Budgets

When the budget amount for a month is requested, such as by the BRAVO reporting system, the rollover amount will be added to the budgeted sum of all budgeted items. Note that this may effectively raise or lower the budgeted amount, depending on if the rollover amount was positive or negative (under or over spent).

In the case the budgeted amount for a month of a particular budget item is requested, the system uses the budgeted item amount plus the percent of the budgeted item over the total budget times the rollover amount; i.e., `budget item + (budget item)/sum(all budgeted items) * (rollover amount)`

### Refunds

If a user requests a refund in month M, then the user will be cut a check for the rollover amount in month M+1, after this has been calculated (N days into the month, once actuals are properly known for month M-1). If the rollover amount is negative the user will be refunded $0.

* Client user screen to request refund for remaining funds at the end of the month

### Example

The current month is January. When we hit the 15th of the month, we can compute the rollover amount from the previous month, December, since the actuals will no longer change after the 15th. Let's say our budget for December was $15,000, and our actual was $14,000. This means there is a rollover amount of $1,000. Since the current month is January (and it is the 15th), the roll over amount can only be applied to a future month, in this case February. If the client has approved the MAF for Febuary, we issue the invoice, otherwise we remind them they need to approve the MAF. If the budgeted amount is the same as for December, $15,000, then the available budget for February will now be $16,000 ($15,000 + $1,000 (rollover amount)). If, however, the user had requested a refund during January, then this $1,000 would be refunded to them IFF the month of January does not go over budget (In the case that January's actuals are $16,000 and its budget was $15,000, then no more will be refunded to user).

# BRAVO - Auditing (Functional)

The goal of auditing is to show budgeted vs. actual spend and allow administrators to push/schedule a kill switch when the difference is too great.

A user should be able to see a list of all accounts, their current spend vs. their predicted spend (`approved spend / number of days in month * current day`), and the difference highlighted in red if it is over 5% different; this allows immediate action to be taken. In future phases we may want the system to adjust ad campaigns (such as pausing them) depending on how the predicted spend matches the actual spend so that we can as closely as possible, meet the approved spend amount.

Ideally, the report would show numbers per network/placement, for for budgeted items (e.g. search and display).