package playmock.json

trait Reads[T]:
  def read(json: JsValue): JsResult[T]

object Reads:

  given Reads[Int] = _ match 
    case JsNumber(n) => JsSuccess(n.toInt)
    case _  => JsError(Seq(JsPath.empty -> "Could not read to int"))

  given Reads[String] = _ match 
    case JsString(value) => JsSuccess(value)
    case _ => JsError(Seq(JsPath.empty -> "Could not read to string"))

  given Reads[Boolean] = _ match 
    case JsBoolean(b) => JsSuccess(b)
    case _ => JsError(Seq(JsPath.empty -> "Could not read to boolean"))

  given Reads[BigDecimal] = _ match 
    case JsNumber(n) => JsSuccess(n)
    case _ => JsError(Seq(JsPath.empty -> "Could not read to big decimal"))

  given [A : Reads]: Reads[List[A]] = _ match 
    case JsArray(ls) => 
      ls.foldLeft[JsResult[List[A]]](JsSuccess(List.empty[A])): (z, elem) => 
          (z, elem.as[A]) match 
            case (JsSuccess(list), JsSuccess(value)) => JsSuccess(value :: list)
            case (JsError(e1), JsError(e2)) => JsError(e1 ++ e2)
            case (err: JsError, _) => err 
            case (_, err: JsError) => err
        .map(_.reverse)
    case _ => JsError(Seq(JsPath.empty -> s"Could not read to list"))

  given [A : Reads]: Reads[Option[A]] = json => json match 
    case JsNull => JsSuccess(None) 
    case _ => json.as[A].map(Some(_))
    