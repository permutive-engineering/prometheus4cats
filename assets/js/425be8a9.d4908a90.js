"use strict";(self.webpackChunk=self.webpackChunk||[]).push([[125],{6728:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>s,contentTitle:()=>r,default:()=>h,frontMatter:()=>l,metadata:()=>c,toc:()=>o});var i=t(4848),a=t(8453);const l={},r="Metrics DSL",c={id:"interface/dsl",title:"Metrics DSL",description:"The metrics DSL provides a fluent API for constructing [primitive] and [derived] metrics from a [MetricFactory]. It",source:"@site/target/mdoc/interface/dsl.md",sourceDirName:"interface",slug:"/interface/dsl",permalink:"/prometheus4cats/docs/interface/dsl",draft:!1,unlisted:!1,editUrl:"https://github.com/permutive-engineering/prometheus4cats/edit/main/website/docs/interface/dsl.md",tags:[],version:"current",frontMatter:{},sidebar:"defaultSidebar",previous:{title:"Callback Registry",permalink:"/prometheus4cats/docs/interface/callback-registry"},next:{title:"Exemplar",permalink:"/prometheus4cats/docs/interface/exemplar"}},s={},o=[{value:"Expected Behaviour",id:"expected-behaviour",level:2},{value:"Refined Types",id:"refined-types",level:2},{value:"Choosing a Primitive Metric",id:"choosing-a-primitive-metric",level:2},{value:"Specifying the Underlying Number Format",id:"specifying-the-underlying-number-format",level:2},{value:"Defining the Help String",id:"defining-the-help-string",level:2},{value:"Building a Simple Metric",id:"building-a-simple-metric",level:2},{value:"Adding Labels",id:"adding-labels",level:2},{value:"Adding Individual Labels",id:"adding-individual-labels",level:3},{value:"Compile-Time Checked Sequence of Labels",id:"compile-time-checked-sequence-of-labels",level:3},{value:"Unchecked Sequence of Labels",id:"unchecked-sequence-of-labels",level:3},{value:"Contramapping a Metric Type",id:"contramapping-a-metric-type",level:2},{value:"Simple Metric",id:"simple-metric",level:3},{value:"Labelled Metric",id:"labelled-metric",level:3},{value:"Contramapping Metric Labels",id:"contramapping-metric-labels",level:2},{value:"Metric Callbacks",id:"metric-callbacks",level:2},{value:"Metric Collection",id:"metric-collection",level:3}];function d(e){const n={a:"a",blockquote:"blockquote",code:"code",em:"em",h1:"h1",h2:"h2",h3:"h3",li:"li",p:"p",pre:"pre",strong:"strong",ul:"ul",...(0,a.R)(),...e.components};return(0,i.jsxs)(i.Fragment,{children:[(0,i.jsx)(n.h1,{id:"metrics-dsl",children:"Metrics DSL"}),"\n",(0,i.jsxs)(n.p,{children:["The metrics DSL provides a fluent API for constructing ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/metrics/primitive-metric-types",children:"primitive"})," and ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/metrics/derived-metric-types",children:"derived"})," metrics from a ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-factory",children:(0,i.jsx)(n.code,{children:"MetricFactory"})}),". It\nis designed to provide ",(0,i.jsx)(n.em,{children:"some"})," compile time safety when recording metrics in terms of matching label values to label\nnames. It ",(0,i.jsx)(n.strong,{children:"does not"})," check that a metric is unique - this is only checked at runtime, the uniqueness of a\nmetric (name & labels combination) depends on the ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-factory",children:(0,i.jsx)(n.code,{children:"MetricFactory"})})," implementation."]}),"\n",(0,i.jsxs)(n.p,{children:["The examples in this section assume you have imported the following and have created a ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-factory",children:(0,i.jsx)(n.code,{children:"MetricFactory"})}),":"]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:"import cats.effect._\nimport prometheus4cats._\n\nval factory: MetricFactory.WithCallbacks[IO] = MetricFactory.builder.noop[IO]\n"})}),"\n",(0,i.jsx)(n.h2,{id:"expected-behaviour",children:"Expected Behaviour"}),"\n",(0,i.jsxs)(n.p,{children:["Every metric or callback created/registered using this DSL returns a Cats-Effect ",(0,i.jsx)(n.code,{children:"Resource"})," which indicates\nthe lifecycle of that metric. When the ",(0,i.jsx)(n.code,{children:"Resource"})," is allocated the metric/callback is registered and when it is\nfinalized it is de-registered."]}),"\n",(0,i.jsxs)(n.p,{children:[(0,i.jsx)(n.strong,{children:"It should be possible"})," to request/register the same metric or callback multiple times without error, where you will\nbe returned the currently registered instance rather than a new instance. This does depend on the implementation of\n",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-registry",children:(0,i.jsx)(n.code,{children:"MetricRegistry"})})," and ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/callback-registry",children:(0,i.jsx)(n.code,{children:"CallbackRegistry"})})," however, the provided\n",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/implementations/java#implementation-notes",children:"Java wrapper implementation"})," implements this via reference counting\nand implementers of ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-registry",children:(0,i.jsx)(n.code,{children:"MetricRegistry"})})," and ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/callback-registry",children:(0,i.jsx)(n.code,{children:"CallbackRegistry"})})," are advised to do the same in order to preserve this\nexpected behaviour at runtime."]}),"\n",(0,i.jsx)(n.h2,{id:"refined-types",children:"Refined Types"}),"\n",(0,i.jsxs)(n.p,{children:["Value classes exist for metric and label names that are refined at compile time from string literals. It is also\npossible to refine at runtime, where the result is returned in an ",(0,i.jsx)(n.code,{children:"Either"}),"."]}),"\n",(0,i.jsx)(n.p,{children:"The value classes used by the DSL are as follows:"}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Counter.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Gauge.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Histogram.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Summary.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Info.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Label.Name"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.code,{children:"Metric.Help"})}),"\n"]}),"\n",(0,i.jsx)(n.p,{children:"When used in the DSL with string literals the value classes are implicitly resolved, so there is no need to wrap every\nvalue."}),"\n",(0,i.jsx)(n.h2,{id:"choosing-a-primitive-metric",children:"Choosing a Primitive Metric"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'factory.counter("counter_total")\nfactory.gauge("gauge")\nfactory.histogram("histogram")\nfactory.summary("summary")\nfactory.info("info_info")\n'})}),"\n",(0,i.jsx)(n.h2,{id:"specifying-the-underlying-number-format",children:"Specifying the Underlying Number Format"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'factory.counter("counter_total").ofDouble\nfactory.counter("counter_total").ofLong\n'})}),"\n",(0,i.jsx)(n.h2,{id:"defining-the-help-string",children:"Defining the Help String"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'factory.counter("counter_total").ofDouble.help("Describe what this metric does")\n'})}),"\n",(0,i.jsx)(n.h2,{id:"building-a-simple-metric",children:"Building a Simple Metric"}),"\n",(0,i.jsxs)(n.p,{children:["Once you have specified all the parameters with which you want to create your metric you can call the ",(0,i.jsx)(n.code,{children:"build"})," method.\nThis will return a ",(0,i.jsx)(n.code,{children:"cats.effect.Resource"})," of your desired metric, which will de-register the metric from the underlying\n",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-registry",children:(0,i.jsx)(n.code,{children:"MetricRegistry"})})," or ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/callback-registry",children:(0,i.jsx)(n.code,{children:"CallbackRegistry"})})," upon finalization."]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'val simpleCounter = factory\n  .counter("counter_total")\n  .ofDouble\n  .help("Describe what this metric does")\n\nsimpleCounter.build\n'})}),"\n",(0,i.jsxs)(n.p,{children:["While not recommended, it is possible to build the metric without a ",(0,i.jsx)(n.code,{children:"cats.effect.Resource"}),", which will not de-register\nfrom the underlying ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-registry",children:(0,i.jsx)(n.code,{children:"MetricRegistry"})}),":"]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:"simpleCounter.unsafeBuild\n"})}),"\n",(0,i.jsx)(n.h2,{id:"adding-labels",children:"Adding Labels"}),"\n",(0,i.jsx)(n.h3,{id:"adding-individual-labels",children:"Adding Individual Labels"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'case class MyClass(value: String)\n\nval tupleLabelledCounter = factory\n  .counter("counter_total")\n  .ofDouble\n  .help("Describe what this metric does")\n  .label[String]("this_uses_show")\n  .label[MyClass]("this_doesnt_use_show", _.value)\n\ntupleLabelledCounter.build.evalMap(_.inc(2.0, ("label_value", MyClass("label_value"))))\n'})}),"\n",(0,i.jsx)(n.h3,{id:"compile-time-checked-sequence-of-labels",children:"Compile-Time Checked Sequence of Labels"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'case class MyMultiClass(value1: String, value2: Int)\n\nval classLabelledCounter = factory\n  .counter("counter_total")\n  .ofDouble\n  .help("Describe what this metric does")\n  .labels[MyMultiClass](\n    Label.Name("label1") -> (_.value1),\n    Label.Name("label2") -> (_.value2.toString)\n  )\n\nclassLabelledCounter.build.evalMap(_.inc(2.0, MyMultiClass("label_value", 42)))\n'})}),"\n",(0,i.jsx)(n.h3,{id:"unchecked-sequence-of-labels",children:"Unchecked Sequence of Labels"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'val unsafeLabelledCounter = factory\n  .counter("counter_total")\n  .ofDouble\n  .help("Describe what this metric does")\n  .unsafeLabels(Label.Name("label1"), Label.Name("label2"))\n\nval labels = Map(Label.Name("label1") -> "label1_value", Label.Name("label2") -> "label1_value")\nunsafeLabelledCounter.build.evalMap(_.inc(3.0, labels))\n'})}),"\n",(0,i.jsx)(n.h2,{id:"contramapping-a-metric-type",children:"Contramapping a Metric Type"}),"\n",(0,i.jsx)(n.h3,{id:"simple-metric",children:"Simple Metric"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'val intCounter: Resource[IO, Counter[IO, Int, Unit]] = factory\n  .counter("counter_total")\n  .ofLong\n  .help("Describe what this metric does")\n  .contramap[Int](_.toLong)\n  .build\n'})}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:"val shortCounter: Resource[IO, Counter[IO, Short, Unit]] = intCounter.map(_.contramap[Short](_.toInt))\n"})}),"\n",(0,i.jsx)(n.h3,{id:"labelled-metric",children:"Labelled Metric"}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'val intLabelledCounter: Resource[IO, Counter[IO, Int, (String, Int)]] = factory\n  .counter("counter_total")\n  .ofLong\n  .help("Describe what this metric does")\n  .label[String]("string_label")\n  .label[Int]("int_label")\n  .contramap[Int](_.toLong)\n  .build\n'})}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:"val shortLabelledCounter: Resource[IO, Counter[IO, Short, (String, Int)]] =\n  intLabelledCounter.map(_.contramap[Short](_.toInt))\n"})}),"\n",(0,i.jsx)(n.h2,{id:"contramapping-metric-labels",children:"Contramapping Metric Labels"}),"\n",(0,i.jsxs)(n.p,{children:["This can work as a nice alternative to\n",(0,i.jsx)(n.a,{href:"#compile-time-checked-sequence-of-labels",children:"providing a compile-time checked sequence of labels"})]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'case class LabelsClass(string: String, int: Int)\n\nval updatedLabelsCounter: Resource[IO, Counter[IO, Long, LabelsClass]] = factory\n  .counter("counter_total")\n  .ofLong\n  .help("Describe what this metric does")\n  .label[String]("string_label")\n  .label[Int]("int_label")\n  .contramapLabels[LabelsClass](c => (c.string, c.int))\n  .build\n'})}),"\n",(0,i.jsx)(n.h2,{id:"metric-callbacks",children:"Metric Callbacks"}),"\n",(0,i.jsxs)(n.p,{children:["The callback DSL is only available with the ",(0,i.jsx)(n.code,{children:"MetricFactory.WithCallbacks"})," implementation of ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/metric-factory",children:(0,i.jsx)(n.code,{children:"MetricFactory"})}),"."]}),"\n",(0,i.jsx)(n.p,{children:"Callbacks are useful when you have some runtime source of a metric value, like a JMX MBean, which will be loaded when\nthe current values for each metric is inspected for export to Prometheus."}),"\n",(0,i.jsxs)(n.p,{children:[(0,i.jsx)(n.strong,{children:"Callbacks are both extremely powerful and dangerous, so should be used with care"}),". Callbacks are assumed to be\nside-effecting in that each execution of the callback may yield a different underlying value, this also means that\nthe operation could take a long time to complete if there is I/O involved (this is strongly discouraged). Therefore,\nimplementations of ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/interface/callback-registry",children:(0,i.jsx)(n.code,{children:"CallbackRegistry"})})," may include a timeout."]}),"\n",(0,i.jsxs)(n.blockquote,{children:["\n",(0,i.jsx)(n.p,{children:"\u2139\ufe0f Some general guidance on callbacks:"}),"\n",(0,i.jsxs)(n.ul,{children:["\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.strong,{children:"Do not perform any complex calculations as part of the callback, such as an I/O operation"})}),"\n",(0,i.jsx)(n.li,{children:(0,i.jsx)(n.strong,{children:"Make callback calculations CPU bound, such as accessing a concurrent value"})}),"\n"]}),"\n"]}),"\n",(0,i.jsxs)(n.p,{children:["All ",(0,i.jsx)(n.a,{href:"/prometheus4cats/docs/metrics/primitive-metric-types",children:"primitive"})," metric types, with exception to ",(0,i.jsx)(n.code,{children:"Info"})," can be implemented as callbacks, like so for ",(0,i.jsx)(n.code,{children:"Counter"})," and\n",(0,i.jsx)(n.code,{children:"Gauge"}),":"]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'factory\n  .counter("counter_total")\n  .ofDouble\n  .help("Describe what this metric does")\n  .callback(IO(1.0))\n\nfactory\n  .gauge("gauge")\n  .ofDouble\n  .help("Describe what this metric does")\n  .callback(IO(1.0))\n'})}),"\n",(0,i.jsxs)(n.p,{children:[(0,i.jsx)(n.code,{children:"Histogram"})," and ",(0,i.jsx)(n.code,{children:"Summary"})," metrics are slightly different as they need a special value to contain the calculated\ncomponents of each metric type:"]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'import cats.data.NonEmptySeq\n\nfactory\n  .histogram("histogram")\n  .ofDouble\n  .help("Describe what this metric does")\n  .buckets(0.1, 0.5)\n  .callback(\n    IO(Histogram.Value(sum = 2.0, bucketValues = NonEmptySeq.of(0.0, 1.0, 1.0)))\n  )\n'})}),"\n",(0,i.jsxs)(n.blockquote,{children:["\n",(0,i.jsxs)(n.p,{children:["\u26a0\ufe0f\ufe0f Note that with a histogram value there must always be one more bucket value than defined when creating the metric,\nthis is to provide a value for ",(0,i.jsx)(n.code,{children:"+Inf"}),"."]}),"\n"]}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:'factory\n  .summary("summary")\n  .ofDouble\n  .help("Describe what this metric does")\n  .callback(\n    IO(Summary.Value(count = 1.0, sum = 1.0, quantiles = Map(0.5 -> 1.0)))\n  )\n'})}),"\n",(0,i.jsxs)(n.blockquote,{children:["\n",(0,i.jsx)(n.p,{children:"\u26a0\ufe0f\ufe0f Note that is you specify quantiles, max age or age buckets for the summary, you cannot register a callback. This is\nbecause these parameters are used when configuring a summary metric type which would be returned you, whereas the\nsummary implementation may be configured differently."}),"\n"]}),"\n",(0,i.jsx)(n.h3,{id:"metric-collection",children:"Metric Collection"}),"\n",(0,i.jsx)(n.p,{children:"It is possible to submit multiple metrics in a single callback, this may be useful where the metrics available in some\ncollection may not be known at compile time. As with callbacks in general, this should be used carefully to ensure that\ncollisions at runtime aren't encountered, it is suggested that you use a custom prefix for all metrics in a given\ncollection to avoid this."}),"\n",(0,i.jsx)(n.pre,{children:(0,i.jsx)(n.code,{className:"language-scala",children:"val metricCollection: IO[MetricCollection] = IO(MetricCollection.empty)\n\nfactory.metricCollectionCallback(metricCollection)\n"})})]})}function h(e={}){const{wrapper:n}={...(0,a.R)(),...e.components};return n?(0,i.jsx)(n,{...e,children:(0,i.jsx)(d,{...e})}):d(e)}},8453:(e,n,t)=>{t.d(n,{R:()=>r,x:()=>c});var i=t(6540);const a={},l=i.createContext(a);function r(e){const n=i.useContext(l);return i.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function c(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:r(e.components),i.createElement(l.Provider,{value:n},e.children)}}}]);