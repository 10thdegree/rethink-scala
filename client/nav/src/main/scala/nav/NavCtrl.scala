package nav

import biz.enef.angulate.Controller

import scala.scalajs.js
import scala.util.{Failure, Success}

class NavCtrl(navService: NavService, $window: js.Dynamic) extends Controller {
  var accounts = js.Array[Account]()
  var selectedAccount = Account()
  var emptyAccount = Account("", "Select Account...")

  navService.availableAccounts() onComplete {
    case Success(resp) =>
      accounts = resp.accounts.sortWith(_.label < _.label)
      accounts.length match {
        case 0 => js.Dynamic.global.console.error("No available accounts")
        case 1 => accountSelected(accounts.pop())
        case _ => {
          navService.accountSelected() onComplete {
            case Success(sel) =>
              accounts.find(_.id == sel.lastSelectedAccount) match {
                case Some(x) => selectedAccount = x
                case _ => selectedAccount = emptyAccount
              }
            case Failure(ex) => handleError(ex)
          }
        }
      }
    case Failure(ex) => handleError(ex)
  }

  def accountSelected(account: Account): Unit = {
    if (account.id != selectedAccount.id) {
      selectedAccount = account
      navService.selectAccount(account.id) onComplete {
        case Success(_) => $window.location.reload()
        case Failure(ex) => handleError(ex)
      }
    }
  }

  private def handleError(ex: Throwable): Unit = js.Dynamic.global.console.error(s"An error has occurred: $ex")

}
