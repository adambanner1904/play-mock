package playmock.http

case class Request[A](
  method: Method,
  uri: String,
  path: String,
  queryParameters: Map[String, Seq[String]],
  headers: Headers,
  version: HttpVersion,
  body: A
) extends RequestHeader