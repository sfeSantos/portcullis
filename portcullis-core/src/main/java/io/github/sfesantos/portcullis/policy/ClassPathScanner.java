package io.github.sfesantos.portcullis.policy;

import io.github.sfesantos.portcullis.PolicyDefinitionException;

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class ClassPathScanner {

    private static final String CLASS_SUFFIX = ".class";

    private ClassPathScanner() {}

    static List<Class<?>> classesIn(String packageName) {
        var loader = classLoader();
        var path = packageName.replace('.', '/');
        var names = new TreeSet<String>();

        try {
            var resources = loader.getResources(path);

            while (resources.hasMoreElements()) {
                var url = resources.nextElement();

                switch (url.getProtocol()) {
                    case "file" -> addFromDirectory(Path.of(url.toURI()), packageName, names);
                    case "jar" -> addFromJar(url, path, names);
                    default -> throw new PolicyDefinitionException("cannot scan " + url + " for package " + packageName);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new PolicyDefinitionException("cannot scan package " + packageName, e);
        }

        // A typo in the package name must not look like a package with nothing to protect.
        if (names.isEmpty()) {
            throw new PolicyDefinitionException("no classes found in package " + packageName);
        }

        var classes = new ArrayList<Class<?>>(names.size());

        for (var name : names) {
            classes.add(load(name, loader));
        }

        return classes;
    }

    private static void addFromDirectory(Path directory, String packageName, Set<String> names) throws IOException {
        try (var files = Files.walk(directory)) {
            files.filter(file -> file.toString().endsWith(CLASS_SUFFIX))
                    .map(file -> directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "."))
                    .forEach(relative -> addClassName(packageName + "." + relative, names));
        }
    }

    private static void addFromJar(URL url, String path, Set<String> names) throws IOException {
        var connection = (JarURLConnection) url.openConnection();
        connection.setUseCaches(false);
        var prefix = path + "/";

        try (var jar = connection.getJarFile()) {
            var entries = jar.entries();

            while (entries.hasMoreElements()) {
                var entry = entries.nextElement().getName();

                if (entry.startsWith(prefix) && entry.endsWith(CLASS_SUFFIX)) {
                    addClassName(entry.replace('/', '.'), names);
                }
            }
        }
    }

    private static void addClassName(String fileName, Set<String> names) {
        var name = fileName.substring(0, fileName.length() - CLASS_SUFFIX.length());

        if (!name.endsWith("package-info") && !name.endsWith("module-info")) {
            names.add(name);
        }
    }

    // Initialization is skipped so that scanning never runs a static initializer.
    private static Class<?> load(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new PolicyDefinitionException("cannot load " + name, e);
        }
    }

    private static ClassLoader classLoader() {
        var loader = Thread.currentThread().getContextClassLoader();

        return loader != null ? loader : ClassPathScanner.class.getClassLoader();
    }
}
