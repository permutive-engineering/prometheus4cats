## Design

This library is specifically designed to work with Prometheus metrics and as such it provides an opinionated API that
mirrors the functionality in the [Prometheus Java client]. Unlike the Java client, the API and recording functionality
are separated.

By separating the functionality of providing an API to construct metrics and an interface to register them against
some concurrent registry.

[Prometheus Java client]: https://github.com/prometheus/client_java/
