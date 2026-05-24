package playmock.http

enum Method:
  case GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS

object Method: 
  def apply(method: String): Either[String, Method] = 
    method match 
      case "GET" => Right(GET)
      case "POST" => Right(POST) 
      case "PUT" => Right(PUT) 
      case "PATCH" => Right(PATCH)
      case "DELETE" => Right(DELETE)
      case "HEAD" => Right(HEAD)
      case "OPTIONS" => Right(OPTIONS)
      case _ => Left(s"Unknown HTTP method: $method")