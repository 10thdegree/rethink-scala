package login

import biz.enef.angular.Service
import biz.enef.angular.core.{HttpPromise, HttpService, Location}

class LoginService($http: HttpService, $location: Location) extends Service {

  def authenticate(credential: Credential) : HttpPromise[Authenticated] = $http.post("/authenticate", credential)

}
