# HTML Builder Benchmark

This project compares the performance and memory efficiency of several Java HTML generation frameworks and template engines.

## Environment
* **Java Version:** 25.0.2 (java-25-amazon-corretto)
* **Hardware/Memory:** ~15 GiB total RAM available (tested on a machine with 15 GiB RAM)
* **Operation System:** Ubuntu 24.04.4 LTS

## Tested Frameworks
* **j2html:** `1.6.0`
* **HtmlFlow:** `5.0.3`
* **JTE (Java Template Engine):** `3.1.12`
* **Dom4j:** `2.2.0`
* **Jsoup:** `1.22.1`
* **Kotlinx.html:** `0.12.0`
* **Ujorm3:** `3.0.0-RC2`
* **StringBuilder:** *(Native JDK baseline)*

## Test Scenarios & Metrics
The benchmark is powered by **JMH (Java Microbenchmark Harness)** configured with the `-prof gc` profiler.

The current comparison focuses on two scalable simple scenarios:

* **SIMPLE_100** - simple HTML page with a table of 100 rows.
* **SIMPLE_1000** - same page shape, but with 1000 rows.

Special HTML-sensitive characters are injected into roughly 5% of text content in all scenarios to keep escaping behavior representative of production input.

To simulate real-world web server behavior, the benchmark streams output directly to a sink (JMH Blackhole) instead of building one giant in-memory string. The table columns represent:

* **Throughput [ops/s]:** The number of HTML generation operations completed per second. Measured in JMH Throughput mode. **Higher is better.**
* **Allocation [B/op] (Normalized Allocation Rate):** The exact amount of temporary Heap memory allocated in **Bytes per operation**. Measured via JMH GC profiler. Lower allocation means significantly less pressure on the Garbage Collector, resulting in lower CPU usage and fewer latency spikes. **Lower is better.**
* **JAR Size [kB]:** The total file footprint of the framework dependency artifact. **Lower is better.**

## Benchmark Results

The libraries are sorted by **SIMPLE_100 throughput** (highest to lowest). Values are rounded to whole numbers.

| Library | JAR Size [kB] | SIMPLE_100 Throughput [ops/s] | SIMPLE_100 Allocation [B/op] | SIMPLE_1000 Throughput [ops/s] | SIMPLE_1000 Allocation [B/op] |
|:--------|--------------:|------------------------------:|-----------------------------:|-------------------------------:|------------------------------:|
| Jte | 79 | 33,830 | 7,200 | 3,538 | 50,402 |
| StringBuilder *(baseline)* | 0 | 24,989 | 94,152 | 2,855 | 845,859 |
| Jsoup | 496 | 13,499 | 143,193 | 1,636 | 1,290,718 |
| HtmlFlow | 52 | 12,512 | 646,753 | 1,337 | 5,246,890 |
| KotlinxHtml | 825 | 12,205 | 67,873 | 1,268 | 627,319 |
| J2html | 198 | 11,460 | 124,657 | 1,086 | 1,229,822 |
| **UjormElement** | **124** | **10,203** | **28,841** | **1,060** | **244,128** |
| Dom4j | 324 | 6,365 | 166,553 | 647 | 1,616,589 |

### Evaluation

`Jte` delivers the strongest overall performance profile in both tested sizes, combining high throughput with very low allocation. `UjormElement` remains competitive in throughput and stands out with strong memory discipline among Java builder-style APIs, especially compared to most DOM-based or callback-heavy alternatives. `StringBuilder` is still a useful raw baseline, but it trades away API safety and maintainability for speed.

---

Learn more about the [Ujorm3 framework](https://github.com/pponec/ujorm/tree/ujorm3?tab=readme-ov-file#-ujorm3-framework).