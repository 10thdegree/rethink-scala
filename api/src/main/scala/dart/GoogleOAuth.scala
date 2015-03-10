package bravo.api.dart

object DartAuth {
  import scalaz._
  import java.io.File
  import Scalaz._
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
  import bravo.api.dart._
  import bravo.api.dart.Data._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  import com.google.api.services.doubleclicksearch._
  import bravo.util.Util._
  
  /** Authorizes the installed application to access user's protected data.*/

  //Generic Google Authorization.: BravoM[DartConfig, (HttpTransport, JsonFactory, Credential)]  
  def getCredential: BravoM[DartConfig, (HttpTransport, JsonFactory, Credential)] = ((c: DartConfig) => Future {
      val jsonFactory = JacksonFactory.getDefaultInstance()
      val transport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
        try {
          val credential =
            new GoogleCredential.Builder()
              .setTransport(transport)
              .setJsonFactory(jsonFactory)
              .setServiceAccountId(c.accountId)
              .setServiceAccountScopes(List(DfareportingScopes.DFAREPORTING))
              .setServiceAccountPrivateKeyFromP12File(new java.io.File(c.filePath))
              // Set the user you are impersonating (this can be yourself).
              .setServiceAccountUser(c.userAccount)
              .build()
            if (credential.refreshToken)
              (transport, jsonFactory, credential).right[JazelError]
            else
              JazelError(None, "Error with refresh token for the credential").left[(HttpTransport, JsonFactory, Credential)]
        } catch {
          case ex: Throwable => JazelError(ex.some,"unable to create credential").left[(HttpTransport, JsonFactory, Credential)]
        }
     }).toBravoM

  //specific to Dart reporting service
  def getCredentialService: BravoM[DartConfig, Dfareporting] = 
    getCredential
      .map(t => { 
          val (transport, jsonFactory, c) = t
          (new Dfareporting(transport, jsonFactory, c))
       })

}
