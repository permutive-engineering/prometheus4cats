# Exemplars

Exemplars provide a way of linking trace information to metrics. For a detailed explainer see the 
[Grafana labs introduction to exemplars](https://grafana.com/docs/grafana/latest/fundamentals/exemplars/).

Exemplars are implemented in Prometheus4Cats using the `Exemplar` typeclass, which returns an optional map of exemplar
labels. You can provide your own implementation for this, but beware that your label names and values can't be any
longer than 128 UTF-8 characters.