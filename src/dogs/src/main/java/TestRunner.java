/*******************************************************************************
 *     Cloud Foundry
 *     Copyright (c) [2009-2017] Pivotal Software, Inc. All Rights Reserved.
 *
 *     This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *     You may not use this product except in compliance with the License.
 *
 *     This product includes a number of subcomponents with
 *     separate copyright notices and license terms. Your use of these
 *     subcomponents is subject to the terms and conditions of the
 *     subcomponent's license, as noted in the LICENSE file.
 *******************************************************************************/
import org.junit.runner.JUnitCore;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Stream;

public class TestRunner {
    public static void main(String[] yo) throws Exception {
        java.util.logging.Logger.getLogger("com.gargoylesoftware.htmlunit").setLevel(java.util.logging.Level.OFF);
        System.setProperty("TERM", "dumb");
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
