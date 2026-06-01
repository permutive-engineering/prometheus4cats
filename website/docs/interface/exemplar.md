# Exemplar

Exemplars attach trace context (typically a `trace_id`) to individual observations of counters and
histograms. The Prometheus wire format propagates the exemplar alongside the metric sample, and
visualisation tools like Grafana surface them as clickable references in panels — letting you jump
from a spike on a chart back to the trace that caused it.

For a conceptual introduction see the
[Grafana labs introduction to exemplars](https://grafana.com/docs/grafana/latest/fundamentals/exemplars/).

## The `Exemplar` typeclass

`Exemplar[F]` produces optional labels at observation time:

```scala
trait Exemplar[F[_]] {
  def get: F[Option[Exemplar.Labels]]
}
```

The simplest implementation returns a fixed label set:

```scala mdoc:silent
import cats.effect._
import prometheus4cats._

implicit val exemplar: Exemplar[IO] = new Exemplar[IO] {
  override def get: IO[Option[Exemplar.Labels]] =
    IO.pure(Exemplar.Labels.of(Exemplar.LabelName("trace_id") -> "abc1234").toOption)
}

val factory: MetricFactory[IO] = MetricFactory.noop[IO]

val counterResource = factory
  .counter("requests_total")
  .ofDouble
  .help("Counter that attaches an exemplar to every inc")
  .build
```

Realistic implementations read from a tracing context — pull the current `trace_id` and `span_id`
out of `IOLocal`, an otel4s `Tracer`, or whatever your application already uses for trace
propagation. The `modules/sandbox/` project has a `Random`-backed example useful for testing.

> ℹ️ Exemplar label sets are limited to 128 UTF-8 characters total per the OpenMetrics spec.
> Oversized exemplars are dropped by Prometheus at ingest.

## Attaching an exemplar to an observation

Three variants of the `inc` / `observe` family attach an exemplar — each with a different policy
for where the label data comes from.

### Implicit — `.incWithExemplar` / `.observeWithExemplar`

Picks up the `Exemplar` instance from implicit scope and calls `.get` on every observation.

```scala mdoc:silent
counterResource.use(_.incWithExemplar(1.0))
```

This is the right variant when the exemplar source is a stable context that's always available
(e.g. a tracer that returns the current span on every call).

### Sampler-based — `.incWithSampledExemplar` / `.observeWithSampledExemplar`

Uses an `ExemplarSampler` typeclass to decide *whether* to emit an exemplar on a given observation
— useful for downsampling when not every observation should carry one. The sampler returns
`Option[Exemplar.Data]`; `None` means skip.

```scala mdoc:silent
implicit val sampler: ExemplarSampler.Counter[IO, Double] = new ExemplarSampler.Counter[IO, Double] {
  override def sample(previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] =
    IO.pure(None)
  override def sample(value: Double, previous: Option[Exemplar.Data]): IO[Option[Exemplar.Data]] =
    IO.pure(None)
}

counterResource.use(_.incWithSampledExemplar(1.0))
```

Use this when you want rate-limited or value-conditional exemplar emission — e.g. "one exemplar per
10 seconds", "only on observations above a latency threshold". The `modules/sandbox/` project has a
probabilistic implementation that emits roughly one in every N calls.

### Explicit — `.incProvidedExemplar` / `.observeProvidedExemplar`

The caller passes the exemplar labels directly — no implicit typeclass involved. Useful when the
trace context is already in hand at the call site and you don't want to thread it through an
implicit.

```scala mdoc:silent
val explicitLabels = Exemplar.Labels
  .of(Exemplar.LabelName("trace_id") -> "abc1234")
  .toOption
  .get

counterResource.use(_.incProvidedExemplar(1.0, Some(explicitLabels)))
```

## Limitations

A few caveats worth knowing before you wire exemplars into production code:

- **Retention period (upstream)**. The `prometheus-metrics-core` library enforces a minimum
  interval (~7 seconds) between consecutive exemplars landing in the *same* classic-histogram
  bucket. Rapid-fire observations keep the first exemplar; later ones are dropped silently.
- **Not propagated on callbacks**. The pull-mode `.callback(...)` path constructs snapshots with no
  exemplar attached, regardless of any `Exemplar` instance in scope. For exemplar-bearing
  pull-mode metrics, switch the relevant metric to the active path and use one of the
  `*WithExemplar` / `*WithSampledExemplar` / `*ProvidedExemplar` variants above.
- **Wire format**. Exemplars travel only on the OpenMetrics text format or the protobuf scrape
  protocol. The classic Prometheus text format drops them silently. Modern Prometheus deployments
  negotiate a compatible format automatically — see [Java Registry] for the scrape config required
  for native histograms (which has the same protocol requirement).

[Java Registry]: ../implementations/java.md
