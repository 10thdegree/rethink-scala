package bravo.core

object Util {
  import scalaz._
  import Scalaz._
  import scala.concurrent.Future
  import scala.concurrent.ExecutionContext.Implicits.global
  import bravo.api.dart._ 
  import bravo.api.dart.Data._
  import org.joda.time._
  
  /*
  *  Kleisli is the equivalent of Function1, so this 
  *  reprsents a computation of type 
  *  (Config) => Future[\/[JazelError, A]] 
  *  Kleisli is useful for threading a 'config' (Di) 
  *  through multiple methods chained together moandically
  */
 
  type KFuture[A] = Kleisli[Future, Config, A]

  type SFuture[A] = StateT[Future, Config, A]

  type BravoM[A] = EitherT[SFuture, JazelError, A]

  case class JazelError(ex: Option[Throwable], msg: String) 
  
  //This is not so modular. we should think about how to define the individual configs in each module, then combine them
  // the instantiation site. ugh, F bound polymorphism with the 'update cache' thing, we need to fix. typeclass? 
  trait Config extends GoogleConfig with MarchexConfig {
    
    val api: DartInternalAPI
    
    val m: Map[Long, List[ReportDay]]
    
    protected def updateCache(m: Map[Long, List[ReportDay]]): Config 
    
    def cache(id: Long) = 
      m.get(id).getOrElse(List[ReportDay]())
    
    def updateCache(id: Long, drs: List[ReportDay]): Config = {
      val merged = m.get(id).fold(drs)(old => old |+| drs)
      updateCache(m + (id -> merged))
   }    
  }
  
  trait GoogleConfig {
    val filePath: String
    val accountId: String
    val userAccount: String
    val clientId: Int 
  }

  trait MarchexConfig {
    val marchexurl: String 
    val marchexuser: String
    val marchexpass: String

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

  case class KleisliFHolder[A](f: (Config) => Future[\/[JazelError,A]]) {
    def toBravoM: BravoM[A] = liftBravoM(f) 
  }
 
  implicit def toBravoMFromState[A](s: StateT[Future, Config, A]): BravoM[A] =
    EitherT[SFuture, JazelError, A](s.map(_.right[JazelError]))

  private def liftBravoM[A](f: Config => Future[\/[JazelError, A]]): BravoM[A] =
    EitherT[SFuture, JazelError, A](
       StateT((c => f(c).map(e => (c,e))))
    )

  implicit def toStateHolder[A](s: StateT[Future, Config, \/[JazelError, A]]): BravoM[A] = EitherT[SFuture, JazelError, A](s)
 
  implicit def toBravoMFromIdState[A](s: StateT[Id, Config, A]): BravoM[A] = StateT[Future, Config, \/[JazelError, A]](c => Future { 
      val (c2,b) = s.run(c)
      (c2, b.right[JazelError]) 
    } ) 
  
  /*
  * Lifting to BravoM and related  
  * converions
  */
  case class StringErrorOps(s: String) {
    def toJazelError: JazelError =  JazelError(None, s)
  }

  case class EThrowableErrorOps[A](t: \/[Throwable,A]) {
    def mapJazelError: \/[JazelError,A] = t.leftMap(e => JazelError(e.some, e.getMessage())) //dumb, I think we should use a different structure
  }
 
  case class ThrowableErrorOps(t: Throwable) {
    def toJazelError: JazelError = JazelError(t.some, t.getMessage()) //dumb, I think we should use a different structure
  }
  
  case class KleisliHolder[A](f: (Config) => \/[JazelError,A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Future { f(c) }) 
  }

  case class KleisliBHolder[A](f: Config => BravoM[A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Future { f(c).right[JazelError] }).flatMap(bm => bm)
  }

  //TODO: scalaz may have a GENERIC way of lifting shit to the right monadic context, need to research
  case class EitherHolder[A](e: \/[JazelError,A]) {
    def toBravoM: BravoM[A] = liftBravoM(c => Monad[Future].point(e))
  }

  case class KleisliAHolder[A](f: (Config) => A) {
    def toBravoM: BravoM[A] = fctry(f) 
  }
  //implicit def toKBM(f: Kleisli[Confi^g,  
  implicit def toKFH[A](f: Config => Future[\/[JazelError,A]]) = KleisliFHolder(f)
  implicit def toKH[A](f: Config => \/[JazelError,A]) = KleisliHolder(f)
  implicit def toKH[A](f: Config => BravoM[A]) = KleisliBHolder(f)
  implicit def toError(s: String): StringErrorOps = StringErrorOps(s)
  implicit def toError[A](t: \/[Throwable,A]): EThrowableErrorOps[A] = EThrowableErrorOps(t)
  implicit def toError(t: Throwable): ThrowableErrorOps = ThrowableErrorOps(t)
  implicit def toBM[A](et: \/[JazelError,A]) = EitherHolder(et) 
  implicit def toKH[A](f: Config => A) = KleisliAHolder(f)
  
  /*
  * General typeclass declarations so we can abstract over BravoM and Future 
  */

  //implicit def bravoMonad: Monad[BravoM] = EitherT.eitherTMonad[({ type l[a] = Kleisli[Future, Config, a]})#l, JazelError]
  
  class EKHolder[A](et: EitherT[({ type l[a] = Function1[Config,a]})#l, JazelError, A]) {
    def toBravoM: BravoM[A] = et
  }
  
  implicit def toEKHolder[A](et: EitherT[({ type l[a] = Function1[Config,a]})#l, JazelError, A]): EKHolder[A] = new EKHolder(et)
  
  implicit def toBravoMFromEK[A](et: EitherT[({ type l[a] = Function1[Config,a]})#l, JazelError, A]): BravoM[A] = 
    liftBravoM((c: Config) => Future { et.run(c) })
  
  implicit def bravoMonad: Monad[BravoM] = EitherT.eitherTMonad[SFuture, JazelError]

  implicit def bravoBind: Bind[BravoM] = EitherT.eitherTMonad[SFuture, JazelError]

  //implicit def kfutureMonad: Monad[SFuture] = Kleisli.kleisliMonadReader[Future, Config]
 
  /*implicit def bravoMonad: Monad[BravoM] = EitherT.eitherTMonad[KFuture, JazelError]

  implicit def bravoBind: Bind[BravoM] = EitherT.eitherTMonad[KFuture, JazelError]

  implicit def kfutureMonad: Monad[KFuture] = Kleisli.kleisliMonadReader[Future, Config]
 */
  implicit def FutureMonad: Monad[Future] = new Monad[Future] {
    
    def point[A](a: => A) = scala.concurrent.Future.successful(a) //we should use the non-threaded future here...
    
    def bind[A, B](f: Future[A])(fmap: A => Future[B]) = f.flatMap(fmap(_))
  }

  implicit def FutureFunctor: Functor[Future] = new Functor[Future] {
    def map[A, B](f: Future[A])(map: A => B): Future[B] = f.map(map(_))
  }

  //from scalaz
  def separateSequence[A, B, F[_], G[_]](g: G[EitherT[F, A, B]])(implicit F: Monad[F], G: Foldable[G], M: MonadPlus[G]): EitherT[F, A, (G[A],G[B])] = 
    EitherT(G.foldRight(g, F.point((M.empty[A],M.empty[B])))( (a, l) =>
      for {
        tup <- l
        e   <- a.run
      } yield 
        e.fold(le => (M.plus(M.point(le),tup._1), tup._2), re => (tup._1, M.plus(M.point(re), tup._2)))
    ).map(_.right[A])
   )

  def separateSeqL[F[_], A, B](g: List[EitherT[F, A, B]])(implicit F: Monad[F]): EitherT[F, A, (List[A], List[B])] = 
    EitherT(g.foldRight(F.point((List[A](), List[B]())))( (a, l) =>
      for {
        tup <- l
        e   <- a.run
      } yield 
        e.fold(le => (le :: tup._1, tup._2), re => (tup._1, re :: tup._2))
    ).map(_.right[A])
   )

  class FoldableExtensionOps[A, B, F[_], G[_]](value: G[EitherT[F, A, B]]) {
    def separateSequence(implicit F: Monad[F], G: Foldable[G], M: MonadPlus[G]): EitherT[F, A, (G[A],G[B])] = Util.separateSequence[A, B, F, G](value)
  }
  
  implicit def toFoldableExtensions[A, B, F[_], G[_]](value: G[EitherT[F, A, B]])(implicit F: Monad[F], G: Foldable[G], M: MonadPlus[G]): FoldableExtensionOps[A, B, F, G] = new FoldableExtensionOps(value)
}
