[TOC]

# BRAVO - Reporting (Functional)

## Overview

The goal of URP (unified reporting platform), is to take metrics from third parties, such as Dart (which in turn gets numbers from AdWords and Bing) and Marchex, and generate *unified* reports on them. Reports show metrics for some entities of interest (such as ad campaigns, or ad publishers), with various numbers (metrics) for each, including impressions, clicks, conversions, etc. One of the major motivating reasons behind the creation of URP is adding the ability to distribute fees across report metrics, as Dart does not do this for you today, making URP not only a helpful unified reporting engine, but an essential component in 10TH DEGREE’s services offering. As such, the mechanism behind creating fees and applying them must be robust or it precludes the neccessity of URP entirely.

### Fees

There are two categories of fees we need to work with, **serving** and **agency**. There should be default value for fees within each category if no account level values are specified; this default should not expire.

#### Serving Fees

Fees incurred by utilizing a 3rd party ad server (e.g. DoubleClick) to track conversions across multiple channels, publishers, and placements. There are currently two kinds of serving ads, banner ads, and video ads, but there may be others in the future. Fundamentally, there are only two serving fees:

* CPM Fee (cost per 1000 impressions)
* CPC Fee (cost per click)

Since we have two kinds of ads, initially we'll need two sets of the above fees:

* Banner ads
    * CPM Fee: E.g. $0.25 (per 1000 impressions)
    * CPC Fee: E.g. $0.25 (per click)
* Video ads
    * CPM Fee: E.g. $1.00 (per 1000 impressions)
    * CPC Fee: E.g. $0.25 (per click)

In addition, valid date ranges for when to apply the sets of fees is needed. (e.g. 01/2001-01/2010) This allows a shcedule to be inputted for automatically incrementing fees.

#### Agency Fees

Fees charged by us for our services. For this we bill in tiers, usually charging less as more ads are served. However, there is also a minimum fee charged if not enough ads are served. Thus, we minimally have a table of:

* Range (number of impressions, e.g. "0-10000")
* Percentage of Spend (e.g. "15%")

So we know for what quanity of impressions, what percentage of the total spend to apply. However, if there were too few impressions, we want to default to a minimum management fee instead:

* Monthly Management Fee: E.g. $750

Similarly to serving fees, a valid date range for when to apply these fees is needed. (e.g. 01/2001-01/2010)

Like how serving fees has different sets of fees (e.g. "banner" and "video"), agency fees can have different sets as well, e.g. "display" and "search".

## Reports

Ultimately, a report (viewed by a user within URP) is composed of one or more rows and one or more columns, forming a grid with the entities of interest (e.g. campaign name, or publisher name) forming rows, and the metrics/stats forming columns. 

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

An instance of a data source is essentially responsible for returning a matrix of "reportable entities" with attributes. URP is designed around having metrics for specific dates (individual days) and reportable entities (such as "ad campaigns" or "ad publishers"); therefore a data source must provide information on these two things so that its data can be combined with other data source data.

The system should be able to combine data from multiple data sources, including the following cases: 

1. when different data sources provide different attributes for the same entities.
2. when different data sources provide the same attributes for the same entities (sum these values).
3. when different data sources provide the same attributes for different entities (e.g. a Bing data source returns stats for Bing campaigns, and an AdWords data source returns stats for AdWords campaigns).

### Templates

Given that many clients will use the same reports, it is crucial that the system allow for configuring a report once and for it to be used by many clients; updating it should likewise apply to all clients using it.

A report template should be composed of fields, which will form the columns of the report displayed to the user. Fields should be specified in a manner similar to inputting formulae in an Excel spreadsheet. This makes defining a report template incredibly flexible. Some fields will come from data source attributes, and some will be formulae, e.g.:

```
#!javascript
spendWithFees = max(
    agencyFees("display").monthlyFee / row.totalDaysInMonth * row.monthDays,
    agencyFees("display").percentileMonth(sumMonth(impressions)) * sumMonth(spend))
```

Based on user permissions, fields in the report should be hidden/shown; this allows administrators to view reports with and without the agency/serving fees included, but for clients, to only show them included.

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

This will require interacting with *Bravo Billing* to get the approved budget for each client account. Once we have the *budget* for a client, the report will take the monthly budget and divide it by the number of days in the month to get the on-track MTD budget. This will be compared to the actual spend (`media cost + ad serving + agency fees`) to see how close we are to being on budget. The report should highlight in RED any accounts off by more than 10%, yellow all accounts from 5-9.99%. These ranges should be configurable by account and a great feature would be that the ranges change relative to where we are in the month. Being off by 15% on day 2 isn't as bad as bing off by 15% on day 20.

#### Rolling 7 Day Metrics (e.g. CTR, CPC, CPL) vs. Prior Period ROlling 7 Day Metrics

The 7 DAY period is somewhat configurable or at least we have several options to choose from like 7/14/30 day.

# BRAVO - Billing (Functional)

Billing involves integrating client billing/invoices. Today we generate an Excel spreadsheet by hand that lists out "spend by month" for N months, showing numbers per network/placement, for both search and display. It also totals all media spend and compares it to the authorised spend amount. Given the right permissions, a user should be able to see this dashboard for an account / edit budget sums. Ideally, a client could log in and approve the budget amounts. Further, given another permission, a user should be able to see a list of all accounts, their current spend vs. their predicted spend (`approved spend / number of days in month * current day`), and the difference highlighted in red if it is over 5% different; this allows immediate action to be taken. In future phases we may want the system to adjust ad campaigns (such as pausing them) depending on how the predicted spend matches the actual spend so that we can as closely as possible, meet the approved spend amount.

Approved budgets are delivered by way of a Media Authorization Form (MAF) today. This MAF would need to have an interface with client login so they can use Digi-sign to approve the budget electronically. 

Using the [Quickbooks API](https://developer.intuit.com/docs/0025_quickbooksapi/0005_introduction_to_quickbooksapi) we could also automatically generate invoices to send the client.