## Callback Registry

The `CallbackRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metric callbacks. This is not designed for use by users of the API directly, they should use it with the
[`MetricFactory`] to create metrics using the [DSL](../interface/dsl.md#metric-callbacks).

Either individual metrics or a collection of metrics may be registered as a callback. This allows you to inspect
individual values or import entire metric registries where needed.

[`MetricFactory`]: metric-factory.md
