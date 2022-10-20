package prometheus4cats

import cats.kernel.{Eq, Hash, Order}
import cats.{Applicative, Contravariant, Show, ~>}

sealed abstract class Summary[F[_], -A] extends Metric[A] { self =>
  def observe(n: A): F[Unit]

  override def contramap[B](f: B => A): Summary[F, B] = new Summary[F, B] {
    override def observe(n: B): F[Unit] = self.observe(f(n))
  }

  def mapK[G[_]](fk: F ~> G): Summary[G, A] = new Summary[G, A] {
    override def observe(n: A): G[Unit] = fk(self.observe(n))
  }
}

object Summary {
  final class AgeBuckets(val value: Int) extends AnyVal {
    override def toString: String = s"""Summary.AgeBuckets(value: "$value")"""
  }

  object AgeBuckets extends internal.SummaryAgeBucketsFromIntLiteral {

    val Default: AgeBuckets = new AgeBuckets(5)

    /** Parse a [[AgeBuckets]] from the given string
      *
      * @param value
      *   value from which to parse a quantile value
      * @return
      *   a parsed [[AgeBuckets]] or failure message, represented by an [[scala.Either]]
      */
    def from(value: Int): Either[String, AgeBuckets] =
      Either.cond(
        value > 0,
        new AgeBuckets(value),
        s"AgeBuckets value $value must be greater than 0"
      )

    /** Unsafely parse a [[AgeBuckets]] from the given double
      *
      * @param value
      *   value from which to parse a quantile value
      * @return
      *   a parsed [[AgeBuckets]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(value: Int): AgeBuckets =
      from(value).fold(msg => throw new IllegalArgumentException(msg), identity)

    implicit val catsInstances: Hash[AgeBuckets] with Order[AgeBuckets] with Show[AgeBuckets] = new Hash[AgeBuckets]
      with Order[AgeBuckets]
      with Show[AgeBuckets] {
      override def hash(x: AgeBuckets): Int = Hash[Int].hash(x.value)

      override def compare(x: AgeBuckets, y: AgeBuckets): Int = Order[Int].compare(x.value, y.value)

      override def show(t: AgeBuckets): String = Show[Int].show(t.value)

      override def eqv(x: AgeBuckets, y: AgeBuckets): Boolean = Eq[Int].eqv(x.value, y.value)
    }
  }

  final class Quantile(val value: Double) extends AnyVal {
    override def toString: String = s"""Summary.Quantile(value: "$value")"""
  }

  object Quantile extends internal.SummaryQuantileFromDoubleLiteral {

    /** Parse a [[Quantile]] from the given string
      *
      * @param value
      *   value from which to parse a quantile value
      * @return
      *   a parsed [[Quantile]] or failure message, represented by an [[scala.Either]]
      */
    def from(value: Double): Either[String, Quantile] =
      Either.cond(
        value >= 0.0 && value <= 1.0,
        new Quantile(value),
        s"Quantile value $value must be between 0.0 and 1.0"
      )

    /** Unsafely parse a [[Quantile]] from the given double
      *
      * @param value
      *   value from which to parse a quantile value
      * @return
      *   a parsed [[Quantile]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(value: Double): Quantile =
      from(value).fold(msg => throw new IllegalArgumentException(msg), identity)

    implicit val catsInstances: Hash[Quantile] with Order[Quantile] with Show[Quantile] = new Hash[Quantile]
      with Order[Quantile]
      with Show[Quantile] {
      override def hash(x: Quantile): Int = Hash[Double].hash(x.value)

      override def compare(x: Quantile, y: Quantile): Int = Order[Double].compare(x.value, y.value)

      override def show(t: Quantile): String = Show[Double].show(t.value)

      override def eqv(x: Quantile, y: Quantile): Boolean = Eq[Double].eqv(x.value, y.value)
    }
  }

  final case class QuantileDefinition private (value: Quantile, error: Double) {
    override def toString: String = s"""Summary.QuantileDefinition(value: "${value.value}", error: "$error")"""
  }

  object QuantileDefinition extends internal.SummaryQuantileDefinitionFromDoubleLiterals {

    /** Parse a [[QuantileDefinition]] from the given string
      *
      * @param value
      *   quantile value
      * @param error
      *   error rate of the given quantile
      * @return
      *   a parsed [[QuantileDefinition]] or failure message, represented by an [[scala.Either]]
      */
    def from(value: Double, error: Double): Either[String, QuantileDefinition] = Quantile.from(value).flatMap { v =>
      Either.cond(
        error >= 0.0 && error <= 1.0,
        new QuantileDefinition(v, error),
        s"Quantile error rate $error must be between 0.0 and 1.0"
      )
    }

    /** Unsafely parse a [[QuantileDefinition]] from the given values
      *
      * @param value
      *   value from which to parse a quantile value
      * @param error
      *   error rate of the given quantile
      * @return
      *   a parsed [[QuantileDefinition]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(value: Double, error: Double): QuantileDefinition =
      from(value, error).fold(msg => throw new IllegalArgumentException(msg), identity)

    implicit val catsInstances: Hash[QuantileDefinition] with Order[QuantileDefinition] with Show[QuantileDefinition] =
      new Hash[QuantileDefinition] with Order[QuantileDefinition] with Show[QuantileDefinition] {
        override def hash(x: QuantileDefinition): Int = Hash[(Quantile, Double)].hash(x.value -> x.error)

        override def compare(x: QuantileDefinition, y: QuantileDefinition): Int =
          Order[(Quantile, Double)].compare(x.value -> x.error, y.value -> y.error)

        override def show(t: QuantileDefinition): String = t.toString

        override def eqv(x: QuantileDefinition, y: QuantileDefinition): Boolean =
          Eq[(Quantile, Double)].eqv(x.value -> x.error, y.value -> y.error)
      }
  }

  case class Value[A](count: A, sum: A, quantiles: Map[Quantile, A] = Map.empty) {
    def map[B](f: A => B): Value[B] = Value(f(count), f(sum), quantiles.view.mapValues(f).toMap)
  }

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {
    override def toString: String = s"""Summary.Name("$value")"""
  }

  object Name extends internal.SummaryNameFromStringLiteral {

    final private val regex = "^[a-zA-Z_:][a-zA-Z0-9_:]*$".r.pattern

    /** Parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a summary name
      * @return
      *   a parsed [[Name]] or failure message, represented by an [[scala.Either]]
      */
    def from(string: String): Either[String, Name] =
      Either.cond(
        regex.matcher(string).matches(),
        new Name(string),
        s"$string must match `$regex`"
      )

    /** Unsafely parse a [[Name]] from the given string
      *
      * @param string
      *   value from which to parse a summary name
      * @return
      *   a parsed [[Name]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(string: String): Name =
      from(string).fold(msg => throw new IllegalArgumentException(msg), identity)

    implicit val catsInstances: Hash[Name] with Order[Name] with Show[Name] = new Hash[Name]
      with Order[Name]
      with Show[Name] {
      override def hash(x: Name): Int = Hash[String].hash(x.value)

      override def compare(x: Name, y: Name): Int = Order[String].compare(x.value, y.value)

      override def show(t: Name): String = t.value

      override def eqv(x: Name, y: Name): Boolean = Eq[String].eqv(x.value, y.value)
    }

  }

  implicit def catsInstances[F[_]]: Contravariant[Summary[F, *]] = new Contravariant[Summary[F, *]] {
    override def contramap[A, B](fa: Summary[F, A])(f: B => A): Summary[F, B] = fa.contramap(f)
  }

  def make[F[_], A](_observe: A => F[Unit]): Summary[F, A] = new Summary[F, A] {
    override def observe(n: A): F[Unit] = _observe(n)
  }

  def noop[F[_]: Applicative, A]: Summary[F, A] = new Summary[F, A] {
    override def observe(n: A): F[Unit] = Applicative[F].unit
  }

  sealed abstract class Labelled[F[_], -A, -B] extends Metric[A] with Metric.Labelled[B] {
    self =>

    def observe(n: A, labels: B): F[Unit]

    def contramap[C](f: C => A): Labelled[F, C, B] = new Labelled[F, C, B] {
      override def observe(n: C, labels: B): F[Unit] = self.observe(f(n), labels)
    }

    def contramapLabels[C](f: C => B): Labelled[F, A, C] = new Labelled[F, A, C] {
      override def observe(n: A, labels: C): F[Unit] = self.observe(n, f(labels))
    }

    final def mapK[G[_]](fk: F ~> G): Labelled[G, A, B] =
      new Labelled[G, A, B] {
        override def observe(n: A, labels: B): G[Unit] = fk(
          self.observe(n, labels)
        )
      }

  }

  object Labelled {
    implicit def catsInstances[F[_], C]: Contravariant[Labelled[F, *, C]] =
      new Contravariant[Labelled[F, *, C]] {
        override def contramap[A, B](fa: Labelled[F, A, C])(f: B => A): Labelled[F, B, C] = fa.contramap(f)
      }

    implicit def labelsContravariant[F[_], C]: LabelsContravariant[Labelled[F, C, *]] =
      new LabelsContravariant[Labelled[F, C, *]] {
        override def contramapLabels[A, B](fa: Labelled[F, C, A])(f: B => A): Labelled[F, C, B] = fa.contramapLabels(f)
      }

    def make[F[_], A, B](_observe: (A, B) => F[Unit]): Labelled[F, A, B] =
      new Labelled[F, A, B] {
        override def observe(n: A, labels: B): F[Unit] = _observe(n, labels)
      }

    def noop[F[_]: Applicative, A, B]: Labelled[F, A, B] =
      new Labelled[F, A, B] {
        override def observe(n: A, labels: B): F[Unit] = Applicative[F].unit
      }
  }
}
