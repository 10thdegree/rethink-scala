package login

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}
import prickle._

import shared.models._
import client.core.PicklerHttpService
import client.core.PicklerHttpService._

class LoginService($http: PicklerHttpService) extends Service {

  implicit val authenticatedPickle = Unpickle[Authenticated]

  def authenticate(credential: Credential) : HttpPromise[Authenticated] = $http.postObject("/authenticate", credential)

}
