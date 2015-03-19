package client.nav

import biz.enef.angulate.Controller

import scala.scalajs.js
import scala.util.{Failure, Success}
import shared.models._
import js.JSConverters._
import client.core.CoreCtrl

class NavCtrl(navService: NavService, $window: js.Dynamic, $scope: js.Dynamic, $cookieStore: js.Dynamic) extends CoreCtrl {
  var accounts = js.Array[Account]()
  var selectedAccount = js.Array[Account]()

  navService.availableAccounts() onComplete {
    case Success(resp) =>
      accounts = resp.accounts.toJSArray.sortWith(_.label < _.label)
      accounts.length match {
        case 0 => js.Dynamic.global.console.error("No available accounts")
        case _ => updateChosenBox()				
      }
    case Failure(ex) => handleError(ex)
  }
  
  $scope.$watch(() => $window.accountsUpdated, (updated: Any) => {
		//CAN'T TEST FOR UNDEFINED ON PAGE LOAD
		if(updated.isInstanceOf[Boolean]){
			updateChosenBox()
		}
  })
	
	def updateChosenBox(): Unit = {
		selectedAccount = $cookieStore.get("accounts").asInstanceOf[js.Array[Account]]
	}
	
  def accountsUpdated(): Unit = {
		$cookieStore.put("accounts",selectedAccount)
		$window.accountsUpdated = true
	}
}
