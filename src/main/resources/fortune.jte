@import org.ujorm.benchmark.Fortune
@import java.util.List
@param List<Fortune> fortunes

<html><body><table>
@for(var fortune : fortunes)
<tr><td>${fortune.id()}</td><td>${fortune.message()}</td></tr>
@endfor
</table></body></html>