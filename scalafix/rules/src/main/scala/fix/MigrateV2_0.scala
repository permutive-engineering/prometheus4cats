package fix

import scala.meta._
import scalafix.v1._

class MigrateV2_0 extends SyntacticRule("MigrateV2_0") {

  override def fix(implicit doc: SyntacticDocument): Patch = doc.tree.collect {
    // Counter
    case tree @ Type.Apply(Type.Name("Counter"), List(f, a)) =>
      Patch.replaceTree(tree, s"Counter[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("Counter"), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"Counter[$f, $a, $b]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("Counter")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Counter[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("Counter")), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Counter[$f, $a, $b]")

    // Gauge
    case tree @ Type.Apply(Type.Name("Gauge"), List(f, a)) =>
      Patch.replaceTree(tree, s"Gauge[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("Gauge"), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"Gauge[$f, $a, $b]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("Gauge")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Gauge[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("Gauge")), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Gauge[$f, $a, $b]")

    // Histogram
    case tree @ Type.Apply(Type.Name("Histogram"), List(f, a)) =>
      Patch.replaceTree(tree, s"Histogram[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("Histogram"), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"Histogram[$f, $a, $b]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("Histogram")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Histogram[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("Histogram")), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Histogram[$f, $a, $b]")
    
    // Summary
    case tree @ Type.Apply(Type.Name("Summary"), List(f, a)) =>
      Patch.replaceTree(tree, s"Summary[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("Summary"), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"Summary[$f, $a, $b]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("Summary")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Summary[$f, $a, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("Summary")), Type.Name("Labelled")), List(f, a, b)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Summary[$f, $a, $b]")

    // CurrentTimeRecorder
    case tree @ Type.Apply(Type.Name("CurrentTimeRecorder"), List(f)) =>
      Patch.replaceTree(tree, s"CurrentTimeRecorder[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("CurrentTimeRecorder"), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"CurrentTimeRecorder[$f, $a]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("CurrentTimeRecorder")), List(f)) =>
      Patch.replaceTree(tree, s"prometheus4cats.CurrentTimeRecorder[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("CurrentTimeRecorder")), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.CurrentTimeRecorder[$f, $a]")

    // OutcomeRecorder
    case tree @ Type.Apply(Type.Name("OutcomeRecorder"), List(f)) =>
      Patch.replaceTree(tree, s"OutcomeRecorder[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("OutcomeRecorder"), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"OutcomeRecorder[$f, $a]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("OutcomeRecorder")), List(f)) =>
      Patch.replaceTree(tree, s"prometheus4cats.OutcomeRecorder[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("OutcomeRecorder")), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.OutcomeRecorder[$f, $a]")

    // Timer
    case tree @ Type.Apply(Type.Name("Timer"), List(f)) =>
      Patch.replaceTree(tree, s"Timer[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Name("Timer"), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"Timer[$f, $a]")
    case tree @ Type.Apply(Type.Select(Term.Name("prometheus4cats"), Type.Name("Timer")), List(f)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Timer[$f, Unit]")
    case tree @ Type.Apply(Type.Select(Term.Select(Term.Name("prometheus4cats"), Term.Name("Timer")), Type.Name("Labelled")), List(f, a)) =>
      Patch.replaceTree(tree, s"prometheus4cats.Timer[$f, $a]")
  }.asPatch

}
