package client.report

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle.Unpickle
import scala.scalajs.js
import shared.models._
import client.core.PicklerHttpService
import client.core.PicklerHttpService._

class ReportService($http: PicklerHttpService) extends Service {

  def searchReport(advertiserId: Int) : HttpPromise[String] = $http.get("/reporting/ds/dart/report/search/"+advertiserId.toString)

  implicit val reportPickle = Unpickle[Report]

  def getReports(accounts: js.Array[String]) : js.Array[Report] = {
		//quick generated report items
		val reports = js.Array(
			new Report("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			),
			new Report("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			),
			new Report("a8687a74-78e6-48fb-aac3-ce87ae69c2f9","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			),
			new Report("a8687a74-78e6-48fb-aac3-ce87ae69c2f9","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			),
			new Report("a8687a74-78e6-48fb-aac3-ce87ae69c2f9","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			),
			new Report("45cbc0d4-5c1d-4f47-a27d-796383675c59","0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd",
				List(new DataSourceBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd")),
				List(new FieldBinding("0af9e689-647c-4f95-a6c5-06298fdccbcd","0af9e689-647c-4f95-a6c5-06298fdccbcd","SourceAttribute",None))
			)
		)
		// reports
		accounts.length match {
			case 0 => reports
			case _ => reports.filter((x: Report) => accounts.contains(x.accountId))
		}
	}

  // def getReports(accounts: Seq[String]) : HttpPromise[Seq[Report]] = $http.post(s"/lastselectedaccount/$accountId")

}