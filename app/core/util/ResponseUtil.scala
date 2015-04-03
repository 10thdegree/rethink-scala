package core.util

import prickle._
import java.util.UUID
import collection.mutable
import scala.util.Try

object ResponseUtil {

  implicit val prickleConfig = new JsConfig(areSharedObjectsSupported = false);

  implicit object UUIDUnpickler extends Unpickler[UUID] {
    def unpickle[P](pickle: P, state: mutable.Map[String, Any])(implicit config: PConfig[P]) = config.readString(pickle).flatMap(s => Try(UUID.fromString(s)))
  }

  implicit object UUIDPickler extends Pickler[UUID] {
    def pickle[P](x: UUID, state: PickleState)(implicit config: PConfig[P]): P = config.makeString(x.toString)
  }
}
