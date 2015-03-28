package bravo.api.adwords

import com.google.api.ads.adwords.axis.factory.AdWordsServices
import com.google.api.ads.adwords.axis.v201502.cm.ReportDefinitionField
import com.google.api.ads.adwords.axis.v201502.cm.ReportDefinitionReportType
import com.google.api.ads.adwords.axis.v201502.cm.ReportDefinitionServiceInterface
import com.google.api.ads.adwords.lib.client.AdWordsSession
import com.google.api.ads.common.lib.auth.OfflineCredentials
import com.google.api.ads.common.lib.auth.OfflineCredentials.Api
import com.google.api.client.auth.oauth2.Credential
import bravo.util.Util._
import bravo.util._

object Data {
  case class AdWordsConfig(userAccount: String, accountId: String, clientId: Int, filePath: String)
  
}

object Adwords {
  import Data._
  //def getCredentialService: BravoM[String, String]  
  val scope = "https://www.googleapis.com/auth/adwords"

  def getSession(): BravoM[AdWordsConfig, AdWordsSession] = 
    fctry((c:AdWordsConfig) => GoogleOAuth.getCredential(List(scope), c.accountId, c.filePath, c.userAccount)
      .map(t => 
        new AdWordsSession.Builder()
        .fromFile()
        .withOAuth2Credential(t._3)
        .build)
        .toBravoM[AdWordsConfig]
      ).flatMap(x => x)
      
}
