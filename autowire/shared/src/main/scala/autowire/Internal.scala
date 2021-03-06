package autowire


import scala.concurrent.Future
import language.experimental.macros


/**
 * Holds a bunch of implementation details, which need to be public
 * for various reasons, but really shouldn't be used directly.
 */
object Internal{

  /**
   * Low priority call-anything extension-method-holding trait, to give the
   * call-Future extension method a chance to run first
   */
  trait LowPri {
    implicit def clientCallable[T](t: T) = new Internal.ClientCallable[T]
  }

  /**
   * A synthetic type purely meant to hold the `.call()` macro; gets
   * erased completely when the macro-implementation of `.call()` runs
   */
  class ClientCallable[T]{
    @ScalaVersionStubs.compileTimeOnly(".call() method is synthetic and should not be used directly")
    def call(): Future[T] = macro Macros.clientMacro[T]
  }

  type FailMaybe = Either[Error.Param, Any]
  type FailAll = Either[List[Error.Param], List[Any]]

  def validate(current: List[FailMaybe]): FailAll = current match {
    case first :: rest =>
      (first, validate(rest)) match {
        case (Right(_), Left(errors)) => Left(errors)
        case (Right(success), Right(successes)) => Right(success :: successes)
        case (Left(error), Left(errors)) => Left(error :: errors)
        case (Left(error), Right(successes)) => Left(error :: Nil)
      }
    case Nil =>
      Right(Nil)
  }
  // HNil[FailMaybe] -> HNil[Identity]
  // HCon[A, HNil[FailMaybe], FailMaybe]
  def doValidate(current: List[FailMaybe]): List[Any] = {
    validate(current) match {
      case Left(failures) => throw autowire.Error.InvalidInput(failures.reverse: _*)
      case Right(res) => res
    }
  }
  def read[P, T](dict: Map[String, P], default: => FailMaybe, name: String, thunk: P => T): FailMaybe = {
    dict.get(name).fold[Either[autowire.Error.Param, Any]](default)( x =>
      util.Try(thunk(x)) match {
        case scala.util.Success(value) => Right(value)
        case scala.util.Failure(error) => Left(autowire.Error.Param.Invalid(name, error))
      }
    )
  }


  sealed trait RouteTree[PickleType] {
    def isValidPath(route: Seq[String]): Boolean
    def lookup(route: Seq[String]): Map[String,PickleType] => Future[PickleType]
  }

  class RouteNode[PickleType](children: Map[String,RouteTree[PickleType]]) extends RouteTree[PickleType] {

    override def isValidPath(route: Seq[String]): Boolean = {
      route.headOption.flatMap(children.get).exists(_.isValidPath(route.tail))
    }

    override def lookup(route: Seq[String]): Map[String,PickleType] => Future[PickleType] = {
      children(route.head).lookup(route.tail)
    }

    override def toString = children.toString()
  }

  class RouteLeaf[PickleType](result: Map[String,PickleType] => Future[PickleType]) extends RouteTree[PickleType] {
    override def isValidPath(route: Seq[String]): Boolean = route.isEmpty
    override def lookup(route: Seq[String]): Map[String,PickleType] => Future[PickleType] = result
  }

  class RouterContext[PickleType](tree: RouteTree[PickleType]) {
    type Router = Core.Router[PickleType]
    def router: Router = {
      case autowire.Core.Request(path,args) if tree.isValidPath(path) => tree.lookup(path)(args)
    }
  }

}