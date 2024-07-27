## Design

This library is specifically designed to work with Prometheus metrics and as such it provides an opinionated API that
mirrors the functionality in the [Prometheus Java client]. Unlike the Java client, the API, [derived metrics] and
[recording](../interface/metric-registry.md) [functionality](../interface/callback-registry.md) are separated.

By separating the functionality of providing an API to construct metrics and an interface to register them against
some concurrent registry we are able to provide implementations other than just the [Prometheus Java client],
although this is [supported](../implementations/java.md).

The API is separated into a few components (follow the links for more details):

- The [`MetricRegistry`] and [`CallbackRegistry`] provide an interface which may be implemented by backends such as the
  [Prometheus Java client]
- The [`MetricFactory`] uses the [`MetricRegistry`] and optionally the [`CallbackRegistry`] to create [primitive] and
  [derived metrics] using a [DSL]

[primitive]: ../metrics/primitive-metric-types.md
[derived metrics]: ../metrics/derived-metric-types.md
[`MetricFactory`]: ../interface/metric-factory.md
[`MetricRegistry`]: ../interface/metric-registry.md
[`CallbackRegistry`]: ../interface/callback-registry.md
[DSL]: ../interface/dsl.md

[Prometheus Java client]: https://github.com/prometheus/client_java/
