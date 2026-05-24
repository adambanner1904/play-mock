package playmock.json

sealed trait JsResult[+A]:
  def map[B](f: A => B): JsResult[B] = this match 
    case JsSuccess(value) => JsSuccess(f(value))
    case e: JsError => e

  def flatMap[B](f: A => JsResult[B]): JsResult[B] = this match 
    case JsSuccess(value) => f(value)
    case e: JsError => e

  def getOrElse[B >: A](default: => B): B = this match 
    case JsSuccess(value) => value
    case _: JsError => default

case class JsSuccess[A](value: A) extends JsResult[A]

case class JsError(errors: Seq[(JsPath, String)]) extends JsResult[Nothing]