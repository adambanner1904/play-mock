package playmock.http

// 0.9 not supported
enum HttpVersion:
  case HTTP1_0, HTTP1_1, HTTP2, HTTP3

object HttpVersion:
  def apply(version: String): Either[String, HttpVersion] = 
    version match 
      case "HTTP/1.0" => Right(HTTP1_0)
      case "HTTP/1.1" => Right(HTTP1_1)
      case "HTTP/2" => Right(HTTP2)
      case "HTTP/3" => Right(HTTP3)
      case _ => Left(s"Unknown HTTP version: $version")