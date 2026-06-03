package playmock.action

import playmock.http.Request

class ActionBuilder[A](request: Request[A], block: Action[A]):

  def andThen[B, C](next: ActionBuilder[B]): ActionBuilder[C] = (request, block) => 
    
    
    
    