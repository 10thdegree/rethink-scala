package bravo.api.dart
  
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
import com.google.api.client.googleapis.auth.oauth2.{GoogleAuthorizationCodeFlow,GoogleCredential,GoogleClientSecrets}
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.{HttpTransport,HttpResponseException}
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.store.{FileDataStoreFactory,DataStoreFactory}
import scala.collection.JavaConversions._
import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
import bravo.util.GoogleOAuth._
import bravo.util._
import bravo.util.Util._
import bravo.api.dart.Data._

object DartAuth {

  val dfaScopes = List(DfareportingScopes.DFAREPORTING, DfareportingScopes.DFATRAFFICKING)

  //specific to Dart reporting service
  def getCredentialService: BravoM[DartConfig, Dfareporting] = fctry((c:DartConfig) => 
    getCredential(dfaScopes, c.accountId, c.filePath, c.userAccount)
      .map(t => { 
        val (transport, jsonFactory, c) = t
        (new Dfareporting(transport, jsonFactory, c))
      })
      .toBravoM[DartConfig]
    ).flatMap(x => x)
}

