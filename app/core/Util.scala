package bravo.core

object Util {
  import scalaz._
  import Scalaz._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  
  type BravoM[A] = EitherT[Future, JazelError, A]
  
  case class JazelError(ex: Option[Throwable], msg: String)

  case class StringErrorOps(s: String) {
    def toJazelError: JazelError =  JazelError(None, s)
    def liftJazelError[A]: EitherT[Future, JazelError, A] = EitherT( implicitly[Monad[Future]].point(s.toJazelError.left[A]) )
  }

  case class ThrowableErrorOps(t: Throwable) {
    def toJazelError: JazelError = JazelError(t.some, t.getMessage()) //dumb, I think we should use a different structure
  }
  
  case class FutureEither[A](ft: Future[\/[JazelError,A]]) {
    def toBravoM: BravoM[A] = EitherT(ft)
  }

  case class EitherF[A](e: \/[JazelError,A]) {
    def toBravoM: BravoM[A] = EitherT( scala.concurrent.Future.successful(e) )
  }

  implicit def toFutureEither[A](ft: Future[\/[JazelError, A]]): FutureEither[A] = FutureEither(ft)
  implicit def toError(s: String): StringErrorOps = StringErrorOps(s)
  implicit def toError(t: Throwable): ThrowableErrorOps = ThrowableErrorOps(t)
  implicit def toEitherF[A](e: \/[JazelError,A]) = EitherF(e)
  //Future try
  def ftry[A](f:  => A): EitherT[Future, JazelError, A] = futuretry(f, None) 
  
  def ftry[A](f: => A, s: String): EitherT[Future, JazelError, A] = futuretry(f, s.some)

  private def futuretry[A](f: =>A, s: Option[String]): EitherT[Future, JazelError, A] = {
    EitherT(Future {
      \/.fromTryCatchNonFatal(f).leftMap(nf => JazelError(nf.some, s.getOrElse(nf.getMessage()))) 
    })
  }

 //Typeclass Conversions
  implicit def FutureMonad: Monad[Future] = new Monad[Future] {
    
    def point[A](a: => A) = scala.concurrent.Future.successful(a) //we should use the non-threaded future here...
    
    def bind[A, B](f: Future[A])(fmap: A => Future[B]) = f.flatMap(fmap(_))
  }

  implicit def FutureFunctor: Functor[Future] = new Functor[Future] {
    def map[A, B](f: Future[A])(map: A => B): Future[B] = f.map(map(_))
  }

}
