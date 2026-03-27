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
The benchmark is powered by **JMH (Java Microbenchmark Harness)** configured with the `-prof gc` profiler. The scenario dynamically builds an HTML table containing **10 rows** of data.

To closely simulate real-world web server behavior (such as writing directly to an `HttpServletResponse` output stream), the benchmark streams the output directly to a data sink (JMH Blackhole) rather than accumulating a massive `String` in memory. The table columns represent the following metrics:

* **Throughput [ops/s]:** The number of HTML generation operations completed per second. Measured in JMH Throughput mode. **Higher is better.**
* **Allocation [B/op] (Normalized Allocation Rate):** The exact amount of temporary Heap memory allocated in **Bytes per operation**. Measured via JMH GC profiler. Lower allocation means significantly less pressure on the Garbage Collector, resulting in lower CPU usage and fewer latency spikes. **Lower is better.**
* **JAR Size [kB]:** The total file footprint of the framework, including all of its transitive dependencies. **Lower is better.**
* **Maintainability Index [0-100]:** An estimated score reflecting developer ergonomics, type safety, and readiness for safe code refactoring. **Higher is better.** 
    * *90–100 (Excellent):* Pure DSLs/Builders (e.g., `Kotlinx.html`, `UjormElement`). Compiler-enforced HTML structure, automatic tag closures, and native IDE refactoring across the codebase.
    * *70–89 (Good):* Fluent APIs/Templates (e.g., `HtmlFlow`, `JTE`). Strong type safety, but requires context-switching (templates) or creates complex, deeply nested functional callbacks.
    * *50–69 (Moderate):* String-based Trees (e.g., `Jsoup`, `Dom4j`). Safe variable bindings, but HTML tag names are plain strings lacking compile-time validation (typos caught only at runtime).
    * *0–49 (Poor):* Manual Concatenation (e.g., `StringBuilder`). High risk of malformed HTML, XSS vulnerabilities, and zero structural refactoring support.

## Benchmark Results

The frameworks are sorted by their **Throughput** (highest performance to lowest). `StringBuilder` is included purely as an absolute raw-performance baseline.

| Library | Throughput<br/>[ops/s] | Allocation<br/>[B/op] | JAR Size<br/>[kB] | Maintainability<br/>[0-100] |
|:--------|-----------------------:|----------------------:|------------------:|----------------------------:|
| StringBuilder *(baseline)* | 1,121,988 | 6,200 | 0 | 20 |
| Jte | 377,476 | 2,640 | 79 | 70 |
| HtmlFlow | 181,516 | 29,472 | 52 | 85 |
| Jsoup | 165,280 | 10,120 | 496 | 60 |
| KotlinxHtml | 138,919 | 6,432 | 825 | 95 |
| J2html | 114,293 | 13,920 | 198 | 80 |
| **UjormElement** | **103,461** | **2,880** | **115** | **90** |
| Dom4j | 77,936 | 13,016 | 324 | 55 |

> **Disclaimer:** The *Maintainability Index* values and their corresponding evaluation criteria were generated objectively by the AI model **Gemini PRO** without any manual intervention or bias from the author.

## Comprehensive Evaluation & Key Takeaways

When choosing an HTML generation library, the decision often comes down to balancing raw performance, system resource constraints, and developer experience.

* **Raw Performance as a Critical Metric:** In high-traffic applications, throughput is a non-negotiable criterion. If absolute speed is the primary goal, compiled template engines are the clear winners. **JTE** heavily dominates the throughput category because it pre-compiles text into highly optimized Java bytecode, bypassing the overhead of instantiating object trees entirely.
* **Memory Load and the Garbage Collector (GC):** Frameworks that allocate excessive temporary objects (like **HtmlFlow** with nearly 30 kB per operation) will inevitably trigger frequent Garbage Collector cycles. These GC pauses can introduce significant latency spikes in production. In this regard, **JTE** and **UjormElement** are the absolute champions. Ujorm allocates a mere 2,880 B/op, offering a highly stable memory profile that keeps the GC largely inactive, ensuring predictable response times.
* **Developer Experience & API Ergonomics:** While templates excel in performance, they inherently compromise on developer ergonomics. Using a template engine forces a context switch out of the native Java/Kotlin language into a specialized template syntax. On the other hand, pure code builders (like **Kotlinx.html**, **j2html**, or **Ujorm**) keep the developer completely within the language. This provides vastly superior IDE support, seamless refactoring, compile-time type safety, and natural integration with existing business logic.
* **The DOM Overhead:** Libraries like **Dom4j** and **Jsoup** are incredibly powerful for parsing and manipulating existing HTML/XML, but they are not optimal for pure generation. They must build and hold the entire Document Object Model in memory before rendering it, which explains their higher memory footprints and generally lower throughput compared to streaming APIs.
* **Conclusion:** If peak performance is paramount and context-switching is acceptable, **JTE** is the superior choice. For projects utilizing Kotlin, **Kotlinx.html** offers an unbeatable, elegant DSL. However, if you require a pure Java solution that maximizes developer ergonomics, avoids external template files, and enforces strict memory discipline to prevent GC pauses, **UjormElement** strikes an excellent balance.

---

Learn more about the [Ujorm3 framework](https://github.com/pponec/ujorm/tree/ujorm3?tab=readme-ov-file#-ujorm3-framework).