package login

import biz.enef.angulate.Service
import biz.enef.angulate.core.{HttpPromise, HttpService}

class LoginService($http: HttpService) extends Service {

  def authenticate(credential: Credential) : HttpPromise[Authenticated] = $http.post("/authenticate", credential)

}
