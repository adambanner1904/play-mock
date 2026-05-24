package playmock.http

opaque type Status = Int

extension (s: Status) 
  def code: Int = s

object Status:

  def apply(code: Int): Either[String, Status] =
    if code >= 100 && code <= 599 then Right(code)
    else Left(s"Unexpected status code: $code")

  private def makeStatus(code: Int): Status = code

  val Ok                  = makeStatus(200)
  val Created             = makeStatus(201)
  val NoContent           = makeStatus(204)
  val BadRequest          = makeStatus(400)
  val Unauthorized        = makeStatus(401)
  val Forbidden           = makeStatus(403)
  val NotFound            = makeStatus(404)
  val UnprocessableEntity = makeStatus(422)
  val InternalServerError = makeStatus(500)
