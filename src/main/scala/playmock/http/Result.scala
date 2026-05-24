package playmock.http

case class Result(
  status: Status,
  headers: Headers,
  body: Vector[Byte]
)