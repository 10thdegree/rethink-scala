package client.nav

import biz.enef.angulate.Controller
import biz.enef.angulate.core.Location

import scala.scalajs.js
import org.scalajs.dom._
import org.scalajs.dom
import scala.util.{Failure, Success}
import shared.models._
import js.JSConverters._
import client.core.{CoreCtrl,CoreEvent,AccountJS}

class NavCtrl(navService: NavService, $window: js.Dynamic, $scope: js.Dynamic, 
	$cookieStore: js.Dynamic, $timeout: js.Dynamic, $location: Location) 
	extends CoreCtrl {
		
	var accounts = js.Array[Account]()
  var multipleSelectedAccount = js.Array[Account]()
  var singleSelectedAccount = AccountJS()
	var nav = NavModel(false,true)
		
	navService.availableAccounts() onComplete {
    case Success(resp) =>
      accounts = resp.accounts.toJSArray.sortWith(_.label < _.label)
      accounts.length match {
        case 0 => js.Dynamic.global.console.error("No available accounts")
        case _ => updateChosenBox()				
      }
    case Failure(ex) => handleError(ex)
  }
	
  def accountsUpdated(): Unit = {
		nav.multiple match {
			case true => $cookieStore.put("accounts", multipleSelectedAccount.map((x: Account) => x.asInstanceOf[AccountJS].id$1))
			case false =>$cookieStore.put("accounts", js.Array[String](singleSelectedAccount.id$1))
		}
		dom.document.dispatchEvent(CoreEvent.changeChosenAccounts)
	}
	
	def updateChosenBox(): Unit = {
		val cookieAcccounts: js.UndefOr[js.Array[String]] = $cookieStore.get("accounts").asInstanceOf[js.Array[String]]
		val selectedAccountIds = cookieAcccounts.getOrElse(js.Array[String]())
		multipleSelectedAccount = accounts.filter((x: Account) => selectedAccountIds.contains(x.id))
		singleSelectedAccount = multipleSelectedAccount.length match {
			case 0 => AccountJS()
			case _ => multipleSelectedAccount.head.asInstanceOf[AccountJS]
		}
	}
	
	dom.document.addEventListener("changeChosenAccounts", {(e: dom.Event) =>
		$timeout(() => {
			updateChosenBox()
		}, 100)
  })
	
	dom.document.addEventListener("hideChosenAccounts", {(e: dom.Event) =>
		nav.show = false
  })

	dom.document.addEventListener("showChosenAccounts", {(e: dom.Event) =>
		nav.show = true
  })

	dom.document.addEventListener("singleChosenAccount", {(e: dom.Event) =>
		$cookieStore.put("accounts", 
			js.Array[String](multipleSelectedAccount.map((x: Account) => x.asInstanceOf[AccountJS].id$1).head)
		)
		singleSelectedAccount = multipleSelectedAccount.head.asInstanceOf[AccountJS]		
		nav = NavModel(false,true)
  })

	dom.document.addEventListener("multipleChosenAccount", {(e: dom.Event) =>
		nav = NavModel(true,true)
  })
}
