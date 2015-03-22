package client.core

import biz.enef.angulate.Controller
import biz.enef.angulate.core.Location

class SubNavCtrl($location: Location) extends CoreCtrl {
  def isActive(viewLocation: String): Boolean = {
		viewLocation match {
			case "/" => $location.path() == viewLocation
			case _ => $location.path().indexOf(viewLocation) > -1
		}
  }
}
