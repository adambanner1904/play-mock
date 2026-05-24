package playmock.json

trait Writes[T]:
  def write(t: T): JsValue

object Writes:
  given Writes[Int] = JsNumber(_)

  given Writes[String] = JsString(_)

  given Writes[Boolean] = JsBoolean(_)

  given Writes[BigDecimal] = JsNumber(_)

  given [A](using writer: Writes[A]): Writes[List[A]] = ls => 
    JsArray(ls.map(writer.write(_)))

  given [A](using writer: Writes[A]): Writes[Option[A]] = _ match 
    case None => JsNull
    case Some(value) => writer.write(value)
    