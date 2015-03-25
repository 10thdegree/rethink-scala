package reporting.core

import play.api.mvc.{ Result, AnyContent, Request }
import play.api.Play
import concurrent.Future

import bravo.util.Util._
import bravo.api.dart._
import reporting.models.ds._
import scalaz.Scalaz._
import scalaz._


abstract class IdentityDataProvider {

  val id: String

  override def toString = id

  def getAdvertisers: Future[(Data.DartConfig, \/[JazelError,List[(String, Int)]])]
}

object IdentityDataProvider {
  private val logger = play.api.Logger("reporting.core.IdentityDataProvider")
}

