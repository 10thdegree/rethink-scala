package reporting.models

//import DataSources.Row

import java.util.UUID

import com.rethinkscala.Document

// XXX: DELETE ME
trait Permission {}

trait Account {}

object FooterTypes {

  case class FooterType(name: String)

  val IncludesAllData = FooterType("all_data")
  val IncludesFilteredData = FooterType("filtered_data")
}

case class Field(id: Option[UUID],
                 label: String,
                 formula: Option[String], // NOTE: bindable fields don't have formulae
                 footerFormula: Option[String],
                 footerType: FooterTypes.FooterType) extends Document

case class Template(id: Option[UUID], label: String, fieldIds: List[UUID])
  extends Document
  with Joins[Field] {

  // TODO: Create this method someplace
  def verifyDependencies = ???
}

case class Chart(id: Option[UUID] /* TODO */)

case class View(id: Option[UUID],
                label: String,
                templateId: UUID,
                fieldIds: List[UUID],
                permissionIds: List[UUID],
                chartIds: List[UUID])
  extends Joins4[Template, Field, Permission, Chart] with Document

case class FieldBinding(fieldId: UUID,
                        dataSourceId: UUID,
                        dataSourceAttribute: String,
                        dependantFieldId: Option[UUID]) extends Document

case class DataSourceBinding(dataSourceId: UUID) extends Document

case class Report(accountId: UUID,
                  templateId: UUID,
                  viewId: UUID,
                  dsBindings: List[DataSourceBinding],
                  fieldBindings: List[FieldBinding])
  extends Joins3[Account, Template, View] with Document

//case class ReportDisplay(report: Report, rows: DataSource.Row)
