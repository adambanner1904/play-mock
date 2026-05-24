package playmock.json

trait Format[A] extends Reads[A] with Writes[A]

object Format:
  def of[A](r: Reads[A], w: Writes[A]): Format[A] = new Format[A]: 
    def read(json: JsValue): JsResult[A] = r.read(json)
    def write(a: A): JsValue = w.write(a)

  given [A](using r: Reads[A], w: Writes[A]): Format[A] = of(r, w)