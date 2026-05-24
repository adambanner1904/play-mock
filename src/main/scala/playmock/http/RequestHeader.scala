package playmock.http

// method, uri, path, query params, headers, version
trait RequestHeader:
  def method: Method
  def uri: String
  def path: String
  def queryParameters: Map[String, Seq[String]]
  def headers: Headers
  def version: HttpVersion