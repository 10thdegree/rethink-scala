package client.core

import biz.enef.angulate._
import biz.enef.angulate.core.{Timeout, Attributes, JQLite}
import org.scalajs.dom.{KeyboardEvent,Event}
import org.scalajs.jquery.{JQuery, JQueryStatic}

import scala.scalajs.js
import scala.scalajs.js.UndefOr


class NgEnterDirective extends Directive {

  def postLink(scope: Scope, elem: JQLite, attrs: Attributes, controller: js.Dynamic): Unit = {
    elem.on[KeyboardEvent]("keydown keypress", {(evt: KeyboardEvent) =>
      if(evt.keyCode == 13) {
        scope.$apply(attrs("ngEnter"))
        evt.preventDefault()
      }
    })
  }
}


class FocusMeDirective($timeout: Timeout) extends Directive {
  def postLink(scope: Scope, element: JQLite, attrs: Attributes, controller: js.Dynamic): Unit = {
    val elem = element.asInstanceOf[js.Dynamic]

    scope.$watch(attrs("focusMe"),
      (newVal: UndefOr[js.Any]) => if(newVal.isDefined) $timeout( () => elem.focus() ) )
  }
}
