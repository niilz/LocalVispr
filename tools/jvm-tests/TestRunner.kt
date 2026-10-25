// ==============================================================================
// Tiny JUnit-compatible test runner for the offline dev container
// ==============================================================================
//
// It discovers every compiled class below the given directory, runs each method
// annotated with `org.junit.Test` on a fresh instance (same semantics as JUnit 4),
// and exits non-zero if anything failed. Intentionally omitted: `@Before`,
// `@After`, rules, and parameterized tests. Our pure-logic tests do not need them.

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val classesDir = File(args[0])
    val loader =
        URLClassLoader(
            arrayOf(classesDir.toURI().toURL()),
            Thread.currentThread().contextClassLoader,
        )
    @Suppress("UNCHECKED_CAST")
    val testAnnotation = loader.loadClass("org.junit.Test") as Class<out Annotation>

    var passed = 0
    val failures = mutableListOf<String>()
    classesDir
        .walkTopDown()
        .filter { it.isFile && it.name.endsWith("Test.class") }
        .map {
            it.relativeTo(classesDir).path.removeSuffix(".class").replace(File.separatorChar, '.')
        }
        .sorted()
        .forEach { className ->
            val testClass = loader.loadClass(className)
            testClass.declaredMethods
                .filter { it.isAnnotationPresent(testAnnotation) }
                .sortedBy { it.name }
                .forEach { method ->
                    try {
                        method.invoke(testClass.getDeclaredConstructor().newInstance())
                        passed++
                    } catch (e: InvocationTargetException) {
                        failures +=
                            "FAIL ${testClass.simpleName}.${method.name}: ${e.targetException}"
                    }
                }
        }

    failures.forEach(::println)
    println("$passed passed, ${failures.size} failed")
    if (failures.isNotEmpty()) exitProcess(1)
}
