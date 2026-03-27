package org.ujorm.benchmark

import kotlinx.html.*
import kotlinx.html.stream.appendHTML

/** Helper object for Kotlinx HTML generation */
object KotlinHtml {

    /** Renders the fortunes list into the provided appendable */
    fun renderFortunes(fortunes: List<HtmlBenchmark.Fortune>, appendable: Appendable) {
        // Vypnutí pretty printing
        appendable.appendHTML(prettyPrint = false).html {
            body {
                table {
                    for (fortune in fortunes) {
                        tr {
                            td { +fortune.id().toString() }
                            td { +fortune.message() }
                            td { +fortune.author() }
                        }
                    }
                }
            }
        }
    }
}