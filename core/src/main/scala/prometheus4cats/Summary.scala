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
  final case class Quantile private (value: Double, error: Double) {
    override def toString: String = s"""Summary.Quantile(value: "$value", error: "$error")"""
  }

  object Quantile {

    /** Parse a [[Quantile]] from the given string
      *
      * @param value
      *   value from which to parse a quantile value
      * @param error
      *   error rate of the given quantile
      * @return
      *   a parsed [[Quantile]] or failure message, represented by an [[scala.Either]]
      */
    def from(value: Double, error: Double): Either[String, Quantile] =
      for {
        v <- Either.cond(
          value >= 0.0 && value >= 1.0,
          value,
          s"Quantile value $value must be between 0.0 and 1.0"
        )
        e <- Either.cond(
          error >= 0.0 && error >= 1.0,
          error,
          s"Quantile error rate $error must be between 0.0 and 1.0"
        )
      } yield Quantile(v, e)

    /** Unsafely parse a [[Quantile]] from the given string
      *
      * @param value
      *   value from which to parse a quantile value
      * @param error
      *   error rate of the given quantile
      * @return
      *   a parsed [[Quantile]]
      * @throws java.lang.IllegalArgumentException
      *   if `string` is not valid
      */
    def unsafeFrom(value: Double, error: Double): Quantile =
      from(value, error).fold(msg => throw new IllegalArgumentException(msg), identity)

    implicit val catsInstances: Hash[Quantile] with Order[Quantile] with Show[Quantile] = new Hash[Quantile]
      with Order[Quantile]
      with Show[Quantile] {
      override def hash(x: Quantile): Int = Hash[(Double, Double)].hash(x.value -> x.error)

      override def compare(x: Quantile, y: Quantile): Int =
        Order[(Double, Double)].compare(x.value -> x.error, y.value -> y.error)

      override def show(t: Quantile): String = t.toString

      override def eqv(x: Quantile, y: Quantile): Boolean =
        Eq[(Double, Double)].eqv(x.value -> x.error, y.value -> y.error)
    }
  }

  case class Value[A](count: A, sum: A, quantiles: List[A] = List.empty) {
    def map[B](f: A => B): Value[B] = Value(f(count), f(sum), quantiles.map(f))
  }

  /** Refined value class for a gauge name that has been parsed from a string
    */
  final class Name private (val value: String) extends AnyVal {
    override def toString: String = s"""Summary.Name("$value")"""
  }

  object Name {

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
