## Metric Registry

The `MetricRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metrics. This is not designed for use by users of the API directly, they should use it with the [`MetricFactory`] to
create metrics using the [DSL](../interface/dsl.md).

[`MetricFactory`]: metric-factory.md
