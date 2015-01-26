package reporting.models

import java.util.UUID

import com.rethinkscala.Document
import core.models.Permission
import reporting.models.FooterTypes.FooterType

case class Account(id: Option[UUID], name: String)

object FooterTypes {

  case class FooterType(name: String)

  val IncludesAllData = FooterType("all_data")
  val IncludesFilteredData = FooterType("filtered_data")
}

case class Field(id: Option[UUID],
                 displayName: Option[String],
                 varName: String,
                 formula: Option[String],
                 footerFormula: Option[String] = None,
                 footerType: FooterType = FooterTypes.IncludesAllData) extends Document {
  def label = displayName.getOrElse(varName)
}

case class Template(id: Option[UUID], label: String, fieldIds: List[UUID])
  extends Document
  with Joins[Field] {

  // TODO: Create this method someplace
  def verifyDependencies = ???
}

case class Chart(id: Option[UUID] /* TODO */)

case class FieldSort(fieldId: UUID, ascending: Boolean = false)

case class View(id: Option[UUID],
                label: String,
                templateId: UUID,
                fieldIds: List[UUID],
                defaultFieldSort: Option[FieldSort],
                permissionIds: List[UUID],
                chartIds: List[UUID])
  extends Joins4[Template, Field, Permission, Chart] with Document

case class FieldBinding(fieldId: UUID,
                        dataSourceId: UUID,
                        dataSourceAttribute: String, // What about activities??
                        dependantFieldId: Option[UUID] = None) extends Document

case class DataSourceBinding(dataSourceId: UUID) extends Document

case class Report(accountId: UUID,
                  templateId: UUID,
                  viewId: UUID,
                  dsBindings: List[DataSourceBinding],
                  fieldBindings: List[FieldBinding])
  extends Joins3[Account, Template, View] with Document

//case class ReportDisplay(report: Report, rows: DataSource.Row)
