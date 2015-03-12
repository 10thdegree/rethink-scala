package userManage

import biz.enef.angulate.Controller
import biz.enef.angulate.core.Location

class SubNavCtrl($location: Location) extends Controller {
  def isActive(viewLocation: String): Boolean = {
    viewLocation == $location.path()
  }
}
