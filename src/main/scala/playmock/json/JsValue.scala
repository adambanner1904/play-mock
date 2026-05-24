package playmock.json

sealed trait JsValue: 
  infix def \(key: String): Option[JsValue] = this match 
    case JsObject(children) => children.get(key)
    case _ => None
  
  infix def \(idx: Int): Option[JsValue] = this match 
    case JsArray(value) => value.lift(idx)
    case _ => None

  def as[A : Reads]: JsResult[A] = summon[Reads[A]].read(this)

case object JsNull extends JsValue

case class JsBoolean(value: Boolean) extends JsValue

case class JsNumber(value: BigDecimal) extends JsValue

case class JsString(value: String) extends JsValue

case class JsArray(value: Seq[JsValue]) extends JsValue

case class JsObject(value: Map[String, JsValue]) extends JsValue

