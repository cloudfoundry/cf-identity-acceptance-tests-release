import org.junit.runner.JUnitCore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class TestRunner {
    public static void main(String[] yo) throws Exception {
        JUnitCore.main(new TestClassNames().get());
    }

    private static class TestClassNames {
        public String[] get() throws IOException {
            return getClasses()
                .filter(c -> hasTests(c))
                .map(c -> c.getName())
                .toArray(String[]::new);
        }

        private boolean hasTests(Class c) {
            for (Method m : c.getMethods()) {
                if (m.isAnnotationPresent(org.junit.Test.class)) {
                    return true;
                }
            }
            return false;
        }

        private Stream<Class> getClasses() throws IOException {
            return getTestClassNames()
                .map(resource -> resource.getFilename())
                .map(fileName -> {
                    try {
                        return Class.forName(fileName.substring(0, fileName.length() - 6));
                    } catch (ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });
        }

        private Stream<Resource> getTestClassNames() throws IOException {
            return Arrays.asList(
                new PathMatchingResourcePatternResolver().getResources("classpath*:*.class")
            ).stream();
        }
    }
}
