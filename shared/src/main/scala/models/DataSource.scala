package shared.models

import java.util.UUID

case class ProviderInfo(id: String, displayName: String, imagePath: String)

case class DartAccountCfg(
  label: String,
  dsAccountId: Int,
  dsId: Option[UUID]
)

case class AdvertiserInfo(label: String, id: Int)

