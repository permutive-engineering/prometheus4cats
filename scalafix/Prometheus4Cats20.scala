package fix

import scala.meta._
import scalafix.v1._

class Prometheus4Cats20 extends SyntacticRule("Prometheus4Cats") {

  object MetricType {

    def unapply(value: String): Option[String] =
      List("Counter", "Gauge", "Histogram", "Summary", "CurrentTimeRecorder", "OutcomeRecorder", "Timer")
        .find(_ == value)

  }

  override def fix(implicit doc: SyntacticDocument): Patch = doc.tree.collect {
    case tree @ Type.Apply(Type.Name(MetricType(name)), args) =>
      Patch.replaceTree(tree, s"$name[${args.mkString(", ")}, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name(MetricType(name)), Type.Name("Labelled")), args) =>
      Patch.replaceTree(tree, s"$name${args.mkString("[", ", ", "]")}")
  }.asPatch

}
