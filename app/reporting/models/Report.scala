package reporting.models

import java.util.UUID

import com.rethinkscala.Document
import core.models.Permission
import reporting.models.ds.dart.DartDS

case class Account(id: Option[UUID], name: String)

object Field {

  object FooterTypes {

    case class FooterType(name: String)

    val Average = FooterType("avg")
    val Summation = FooterType("sum")
    val Minimum = FooterType("min")
    val Maximum = FooterType("max")
  }

  object FormatTypes {

    case class FormatType(name: String)

    val Currency = FormatType("currency")
    val FractionalNumber = FormatType("fractional")
    val WholeNumber = FormatType("whole")
    val Percentage = FormatType("percentage")
  }

  // isRawTransform = true operates on raw data points instead of
  // points rolled up by (flattened) time ranges.
  case class Formula(value: String,
                     isRawTransform: Boolean = false)

  case class Display(title: Option[String],
                     format: FormatTypes.FormatType,
                     footer: Option[FooterTypes.FooterType] = None)

}

case class Field(id: Option[UUID],
                 varName: String,
                 formula: Option[Field.Formula] = None,
                 display: Option[Field.Display] = None) extends Document {

  def prettyVarName = if (varName.length < 4) varName.toUpperCase else varName.capitalize

  def label = display.flatMap(_.title).getOrElse(prettyVarName)
  def footer = display.flatMap(_.footer)
  def format = display.map(_.format)
  def formulaValue = formula.map(_.value)
}

case class Template(id: Option[UUID], label: String, fieldIds: List[UUID])
  extends Document
  with Joins[Field] {

  // TODO: Create this method someplace
  def verifyDependencies = ???
}

sealed trait Chart {
  def label: String
}

object Chart {
  case class Bar(label: String, domainField: UUID) extends Chart
  case class Pie(label: String, rangeField: UUID) extends Chart
}

case class FieldSort(fieldId: UUID, ascending: Boolean = false)

case class View(id: Option[UUID],
                label: String,
                templateId: UUID,
                fieldIds: List[UUID],
                defaultFieldSort: Option[FieldSort],
                permissionIds: List[UUID],
                charts: List[Chart])
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

case class DataSourceDoc(datasource: DartDS = null, id: Option[UUID] = None) extends Document
