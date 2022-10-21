## Callback Registry

The `CallbackRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metric callbacks. This is not designed for use by users of the API directly, they should use it with the
[`MetricFactory`] to create metrics using the [DSL](../interface/dsl.md#metric-callbacks).

[`MetricFactory`]: metric-factory.md
