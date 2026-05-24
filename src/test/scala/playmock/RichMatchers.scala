package playmock

import org.scalatest.matchers.should.Matchers
import org.scalatest.diagrams.Diagrams
import org.scalatest.{EitherValues, OptionValues, TryValues, AppendedClues}

trait RichMatchers
  extends Matchers
  with Diagrams
  with EitherValues
  with OptionValues
  with TryValues
  with AppendedClues