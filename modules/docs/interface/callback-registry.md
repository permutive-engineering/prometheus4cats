## Callback Registry

The `CallbackRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metric callbacks. This is not designed for use by users of the API directly, they should use it with the
[`MetricFactory`] to create metrics using the [DSL](../interface/dsl.md#metric-callbacks).

Either individual metrics or a collection of metrics may be registered as a callback. This allows you to inspect
individual values or import entire metric registries where needed.

### Development Notes

All methods on `MetricRegistry` return a Cats-Effect `Resource[F, Unit]`. This
should be used to register and un-register the callback with the underlying concurrent data structure. It should be
possible to allow a callback to be registered for the same metric multiple times without throwing a runtime
exception; therefore we recommend implementing some form of reference counting to track claims on each metric the
callback is providing.

[`MetricFactory`]: metric-factory.md
