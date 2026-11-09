import com.sun.source.util.JavacTask;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SyntaxCheck {
    public static void main(String[] paths) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(collector, null, null)) {
            List<String> args = new ArrayList<>();
            args.addAll(Arrays.asList(paths));
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromStrings(args);
            JavacTask task = (JavacTask) compiler.getTask(null, files, collector,
                    Arrays.asList("-proc:none", "-encoding", "UTF-8"), null, units);
            task.parse();
        }
        int errors = 0;
        for (Diagnostic<? extends JavaFileObject> d : collector.getDiagnostics()) {
            if (d.getKind() == Diagnostic.Kind.ERROR) { System.err.println(d); errors++; }
        }
        if (errors > 0) throw new IllegalStateException(errors + " syntax errors");
        System.out.println("PASS: Java syntax parse of " + paths.length + " source files");
    }
}
