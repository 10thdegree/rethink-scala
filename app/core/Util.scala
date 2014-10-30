package bravo.core

object Util {
  import scalaz._
  import Scalaz._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  import bravo.api.dart._ 
  /*
  *  Kleisli is the equivalent of Function1, so this 
  *  reprsents a computation of type 
  *  (Config) => Future { \/[JazelError, A] }
  *  Kleisli is useful for threading a 'config' (Di) 
  *  through multiple methods chained together moandically
  */
  type BravoM[A] = EitherT[({ type l[a] = Kleisli[Future, Config, a]})#l, JazelError, A]

  case class JazelError(ex: Option[Throwable], msg: String) 

  trait Config extends GoogleConfig {
    val api: DartInternalAPI
  }
  
  trait GoogleConfig {
    val filePath: String
    val accountId: String
    val userAccount: String
    val clientId: String
 }
  /*
  * For wrapping external APIs
  * and executing them asynchronously 
  */
  def ftry[A](a: => A): BravoM[A] = fctry(c => a)
  
  def fctry[A](f: Config => A): BravoM[A] = fctry(f, None) 
  
  def fctry[A](f: Config => A, s: String): BravoM[A] = fctry(f, s.some)
  
  def fctry[A](f: Config => A, s: Option[String]): BravoM[A] =
    liftBravoM(c => Future {
        \/.fromTryCatchNonFatal( f(c) ).leftMap(nf => JazelError(nf.some, s.getOrElse(nf.getMessage())))
      }
    )

  def liftBravoM[A](f: Config => Future[\/[JazelError, A]]): BravoM[A] =
    EitherT[({ type l[a] = Kleisli[Future, Config, a]})#l, JazelError, A]( 
        Kleisli(f) 
    )


  /*
  def liftBravoM[A](f: Config => \/[JazelError, A]): BravoM[A] = 
    EitherT[({ type l[a] = Kleisli[Future, Config, a]})#l, JazelError, A]( 
      Kleisli( (c: Config) => Future { 
        f(c) 
      } ))
  */
  /*
  * Lifting to BravoM and related  
  * converions
  */
  case class StringErrorOps(s: String) {
    def toJazelError: JazelError =  JazelError(None, s)
  }

  case class ThrowableErrorOps(t: Throwable) {
    def toJazelError: JazelError = JazelError(t.some, t.getMessage()) //dumb, I think we should use a different structure
  }

  case class KleisliHolder[A](f: (Config) => \/[JazelError,A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Future { f(c) }) 
  }

  case class KleisliFHolder[A](f: (Config) => Future[\/[JazelError,A]]) {
    def toBravoM: BravoM[A] = liftBravoM(f) 
  }
  
  case class KleisliBHolder[A](f: Config => BravoM[A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Future { f(c).right[JazelError] }).flatMap(bm => bm)
  }

  //TODO: scalaz may have a GENERIC way of lifting shit to the right monadic context, need to research
  case class EitherHolder[A](e: \/[JazelError,A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Future { e })
  }
  
  implicit def toKFH[A](f: Config => Future[\/[JazelError,A]]) = KleisliFHolder(f)
  implicit def toKH[A](f: Config => \/[JazelError,A]) = KleisliHolder(f)
  implicit def toKH[A](f: Config => BravoM[A]) = KleisliBHolder(f)
  implicit def toError(s: String): StringErrorOps = StringErrorOps(s)
  implicit def toError(t: Throwable): ThrowableErrorOps = ThrowableErrorOps(t)
  implicit def toBM[A](et: \/[JazelError,A]) = EitherHolder(et) 
  
  /*
  * General typeclass declarations so we can abstract over BravoM and Future 
  */

  implicit def bravoMonad: Monad[BravoM] = EitherT.eitherTMonad[({ type l[a] = Kleisli[Future, Config, a]})#l, JazelError]
  
  implicit def FutureMonad: Monad[Future] = new Monad[Future] {
    
    def point[A](a: => A) = scala.concurrent.Future.successful(a) //we should use the non-threaded future here...
    
    def bind[A, B](f: Future[A])(fmap: A => Future[B]) = f.flatMap(fmap(_))
  }

  implicit def FutureFunctor: Functor[Future] = new Functor[Future] {
    def map[A, B](f: Future[A])(map: A => B): Future[B] = f.map(map(_))
  }
}
