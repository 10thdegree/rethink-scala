package bravo.api.dart

object DartAuth {
  import scalaz._
  import java.io.File
  import Scalaz._
  import com.google.api.client.auth.oauth2.Credential
  import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp
  import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver
  import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
  import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
  import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets
  import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
  import com.google.api.client.googleapis.json.GoogleJsonResponseException
  import com.google.api.client.http.HttpResponseException
  import com.google.api.client.http.HttpTransport
  import com.google.api.client.json.JsonFactory
  import com.google.api.client.json.jackson2.JacksonFactory
  import com.google.api.client.util.store.DataStoreFactory
  import com.google.api.client.util.store.FileDataStoreFactory
  import scala.collection.JavaConversions._
  import com.google.api.services.dfareporting.{Dfareporting, DfareportingScopes} 
  import bravo.core.Util._
  import bravo.api.dart._
  import bravo.api.dart.Data._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  
  //def unsafeGetReporting(): BravoM[Dfareporting] = 
  //  getCredentialService("/users/vmarquez/Bravo-44871094176f.p12","399851814004-9msbusp4vh24crdgrrltservs4u430uj@developer.gserviceaccount.com","bravo@10thdegree.com")
  
  /** Authorizes the installed application to access user's protected data.*/

  //Generic Google Authorization.  
  def getCredential: BravoM[(HttpTransport, JsonFactory, Credential)] = ((c: Config) => {
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
            (transport, jsonFactory, credential).right[JazelError]
        } catch {
          case ex: Throwable => ex.toJazelError.left[(HttpTransport, JsonFactory, Credential)]
        }
  }).toBravoM

  //specific to Dart reporting service
  def getCredentialService: BravoM[Dfareporting] = 
    getCredential.flatMap(t => { 
      val (transport, jsonFactory, c) = t
      if (c.refreshToken())
        (new Dfareporting(transport, jsonFactory, c).right[JazelError]).toBravoM
      else 
        (("Error with refreshTOken for the credential" ).toJazelError.left[Dfareporting]).toBravoM
    })
  
  /*
  def GoogleInstalledAppAuth(clientid: String, secret: String, user: String): \/[Exception, Credential] = {
    try {
      val scopes = List("https://www.googleapis.com/auth/dfareporting")
      val dataStoreDir = new java.io.File("temp/")
      val storeFactory = new FileDataStoreFactory(dataStoreDir)
      val jsonFactory = JacksonFactory.getDefaultInstance()
      //val GclientSecrets: GoogleClientSecrets  = GoogleClientSecrets.load(jsonFactory, "JSON" )
      val transport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
                                                                                    // set up authorization code flow
      val flow: GoogleAuthorizationCodeFlow = new GoogleAuthorizationCodeFlow.Builder(transport, jsonFactory, clientid, secret, scopes)
        .setDataStoreFactory(storeFactory)
        .build()
                                                                                                            // authorize
      return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(user).right  
    } catch {
      case ex: Exception => ex.left[Credential]
    }
  }*/
}