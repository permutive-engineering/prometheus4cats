## Metric Registry

The `MetricRegistry` is an interface that may be implemented by different backends to provide concurrent access to
metrics. This is not designed for use by users of the API directly, they should use it with the [`MetricFactory`] to
create metrics using the [DSL](../interface/dsl.md).

### Development Notes

All methods on `MetricRegistry` return the desired [primitive metric] contained in a Cats-Effect `Resource`. This
should be used to register and un-register the metric with the underlying concurrent data structure. It should be
possible to allow the same metric to be requested multiple times without re-registering or throwing a runtime
exception; therefore we recommend implementing some form of reference counting to track claims on each metric.

[`MetricFactory`]: metric-factory.md
[primitive metric]: ../metrics/primitive-metric-types.md
