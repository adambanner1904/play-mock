package playmock.http

class Headers private (private val headers: Map[String, Seq[String]]): 
      
  def get(name: String): Option[Seq[String]] = 
    headers.get(name.toLowerCase)

  def getFirst(name: String): Option[String] = 
    get(name).flatMap(_.headOption)

  def exists(name: String): Boolean = 
    get(name).isDefined

  def values: Map[String, Seq[String]] = headers

object Headers:
  def apply(headers: Seq[(String, String)]): Headers = 
    new Headers(
      headers
        .groupBy: (key, _) => 
          key.toLowerCase()
        .map: (lowerCasedKey, innerSeq) => 
          lowerCasedKey -> innerSeq.map((_, value) => value)
    )