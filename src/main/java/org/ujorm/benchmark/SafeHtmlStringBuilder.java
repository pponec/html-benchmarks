package org.ujorm.benchmark;

/** Minimal HTML facade over StringBuilder with safe-by-default text writes. */
final class SafeHtmlStringBuilder {
    private final StringBuilder builder;

    SafeHtmlStringBuilder(int capacity) {
        this.builder = new StringBuilder(capacity);
    }

    SafeHtmlStringBuilder openTag(String name) {
        builder.append('<').append(name).append('>');
        return this;
    }

    SafeHtmlStringBuilder closeTag(String name) {
        builder.append("</").append(name).append('>');
        return this;
    }

    SafeHtmlStringBuilder element(String name, Object value) {
        return openTag(name).text(value).closeTag(name);
    }

    /** Escapes user/content values for safe HTML text node output. */
    SafeHtmlStringBuilder text(Object value) {
        var text = String.valueOf(value);
        for (int i = 0; i < text.length(); i++) {
            var ch = text.charAt(i);
            switch (ch) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(ch);
            }
        }
        return this;
    }

    /**
     * Writes trusted HTML fragment directly.
     * Use only with pre-sanitized or constant markup.
     */
    SafeHtmlStringBuilder raw(String trustedHtml) {
        builder.append(trustedHtml);
        return this;
    }

    @Override
    public String toString() {
        return builder.toString();
    }
}
