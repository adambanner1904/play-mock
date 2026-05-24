package playmock.json

case class JsPath(nodes: Seq[PathNode]): 

  def show: String = 
    nodes.map(_.show).mkString

  infix def \(key: String): JsPath = 
    JsPath(nodes :+ PathNode.KeyNode(key))
    
  infix def \(idx: Int): JsPath = 
    JsPath(nodes :+ PathNode.IdxNode(idx))

  def apply(json: JsValue): Option[JsValue] =
    nodes.foldLeft(Option(json))((j, node) => j.flatMap(node(_)))

object JsPath:
  def empty: JsPath = JsPath(Seq.empty)
  val __ : JsPath = empty
    

enum PathNode:
  case KeyNode(key: String)
  case IdxNode(idx: Int)

  def show: String = this match 
    case KeyNode(key) => s"/$key"
    case IdxNode(idx) => s"/[$idx]"

  def apply(json: JsValue): Option[JsValue] = this match 
    case KeyNode(key) => json \ key
    case IdxNode(idx) => json \ idx
    