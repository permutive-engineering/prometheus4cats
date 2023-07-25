# Exemplars

Exemplars provide a way of linking trace information to metrics. For a detailed explainer see the 
[Grafana labs introduction to exemplars](https://grafana.com/docs/grafana/latest/fundamentals/exemplars/).

Exemplars are implemented in Prometheus4Cats using the `Exemplar` typeclass, which returns an optional map of exemplar
labels. You can provide your own implementation for this, but beware that your label names and values can't be any
longer than 128 UTF-8 characters.

The `Exemplar` typeclass contains two methods in order to get the exemplar labels; where the rendered metric name, its
labels and value must be passed so the decision whether an exemplar should be recorded can be made by the implementer.
This helps keep the churn of exemplars to a minimum and allows implementers to link traces for certain metrics only
and under certain circumstances.