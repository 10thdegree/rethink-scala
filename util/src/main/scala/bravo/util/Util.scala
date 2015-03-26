package bravo.util

object Util {
  import scalaz._
  import Scalaz._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  
  trait BravoConfig
  
  type SFuture[A,B] = StateT[Future, A, B]

  type BravoM[A,B] = EitherT[({ type l[a] = SFuture[A,a]})#l, JazelError, B]

  type BravoHoist[A[_],B] = EitherT[A, JazelError, B] //useful for lifting an SFuture to a BravoM with liftM[BravoHoist] 

  case class JazelError(ex: Option[Throwable], msg: String) 
    
  def ftry[A,B](b: => B): BravoM[A,B]= fctry((c:A) => b)
  
  def fctry[A,B](f: A => B): BravoM[A,B] = fctry(f, None) 
 
  def fctry[A,B](f: A => B, s: String): BravoM[A,B] = fctry(f, s.some)
  
  def fctry[A,B](f: A => B, s: Option[String]): BravoM[A,B] = 
    liftBravoM(c => Future {
        \/.fromTryCatchNonFatal( {
        try {
          f(c) 
        } catch {
          case ex: Exception => println("error = " + ex)
                                throw ex
        }
        }).leftMap(nf => JazelError(nf.some, s.getOrElse(nf.getMessage())))
      }
    )

  private def liftBravoM[A,B](f: A => Future[\/[JazelError, B]]): BravoM[A,B] =
    EitherT[({ type l[a] = SFuture[A,a]})#l, JazelError, B](
       StateT(((c:A) => f(c).map(e => (c,e))))
  )
 
  case class EitherHolder[B](e: \/[JazelError,B]) {
    def toBravoM[A]: BravoM[A,B] = EitherT[({ type l[a] = SFuture[A,a]})#l, JazelError, B](sFutureMonad[A].point(e))
  }

  case class BMHolder[A,B](bm: BravoM[A, B]) {
    def zoom[C](lens: Lens[C,A]): BravoM[C,B] = EitherT[({ type l[a] = SFuture[C,a]})#l, JazelError, B](bm.run.zoom(lens)) //   .liftM[BravoHoist]
  } 
 
  implicit def toBMHolder[A,B](bm: BravoM[A,B]): BMHolder[A,B] = BMHolder(bm) 

  case class FuncHolder[A,B](f: A => B) {
    def toBravoM: BravoM[A,B] = fctry(f, None)
  }

  case class ReaderHolder[A,B](f: A => Future[\/[JazelError,B]]) {
    def toBravoM: BravoM[A,B] = liftBravoM(f)
  }
  
  implicit def toFH[A,B](f: A => B): FuncHolder[A,B] = FuncHolder(f)

  implicit def toBM[B](et: \/[JazelError, B]): EitherHolder[B] = EitherHolder(et) 

  implicit def toRH[A,B](f: A => Future[\/[JazelError,B]]): ReaderHolder[A,B] = ReaderHolder(f)
  
  def btry[A](a: => A): \/[JazelError, A] = \/.fromTryCatchNonFatal(a).leftMap(e => JazelError(e.some, e.getMessage()))

  implicit def toST[A,B](f: StateT[Future, A, \/[JazelError, B]]): BravoM[A,B] = 
    EitherT[({ type l[a] = SFuture[A,a]})#l, JazelError, B](f)
  
  /****** OUR TYPE CLASSES *****/

  implicit def BravoMonad[A]: Monad[({ type l[a] = BravoM[A,a]})#l] = 
    EitherT.eitherTMonad[({ type l[a] = SFuture[A,a]})#l, JazelError]

  def sFutureMonad[A]() = Monad[({ type l[a] = SFuture[A,a]})#l]

  implicit def FutureMonad: Monad[Future] = new Monad[Future] {
    
    def point[A](a: => A) = scala.concurrent.Future.successful(a) //we should use the non-threaded future here...
    
    def bind[A, B](f: Future[A])(fmap: A => Future[B]) = f.flatMap(fmap(_))
  }

}
