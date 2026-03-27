@import org.ujorm.benchmark.HtmlBenchmark.Fortune
@param java.util.List<Fortune> fortunes

<html>
<body>
    <table>
        @for(Fortune fortune : fortunes)
        <tr>
            <td>${fortune.id()}</td>
            <td>${fortune.message()}</td>
            <td>${fortune.author()}</td>
        </tr>
        @endfor
    </table>
</body>
</html>