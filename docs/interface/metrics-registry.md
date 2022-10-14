## MetricsRegistry

The `MetricsRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metrics. This is not designed for use by users of the API directly, they should use it with the [`MetricsFactory`] to
create metrics using the [DSL](../interface/dsl.md).

[`MetricsFactory`]: metrics-factory.md
