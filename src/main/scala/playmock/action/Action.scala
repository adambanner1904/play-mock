package playmock.action

import playmock.http.{Request, Result}

type ActionFn[A] = Request[A] => Result

final case class Action[A](run: ActionFn[A]):
  def invokeBlock(request: Request[A]): Result = run(request)