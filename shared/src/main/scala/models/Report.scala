package shared.models

case class FieldBinding(fieldId: String,
                        dataSourceId: String,
                        dataSourceAttribute: String,
                        dependantFieldId: Option[String] = None)

case class DataSourceBinding(dataSourceId: String)

case class Report(accountId: String,
                  templateId: String,
                  viewId: String,
                  dsBindings: List[DataSourceBinding],
                  fieldBindings: List[FieldBinding])