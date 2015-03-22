package client.core

import scala.scalajs.js
import org.scalajs.dom._
import org.scalajs.dom

object CoreEvent {
	val changeChosenAccounts = js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)("changeChosenAccounts", js.Dynamic.literal(detail = "elem.dataset.time")).asInstanceOf[dom.CustomEvent]
	val singleChosenAccount = js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)("singleChosenAccount", js.Dynamic.literal(detail = "elem.dataset.time")).asInstanceOf[dom.CustomEvent]
	val multipleChosenAccount = js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)("multipleChosenAccount", js.Dynamic.literal(detail = "elem.dataset.time")).asInstanceOf[dom.CustomEvent]
	val hideChosenAccounts = js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)("hideChosenAccounts", js.Dynamic.literal(detail = "elem.dataset.time")).asInstanceOf[dom.CustomEvent]
	val showChosenAccounts = js.Dynamic.newInstance(js.Dynamic.global.CustomEvent)("showChosenAccounts", js.Dynamic.literal(detail = "elem.dataset.time")).asInstanceOf[dom.CustomEvent]
}
