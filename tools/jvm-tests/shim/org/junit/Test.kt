package org.junit

// Minimal stand-in for JUnit 4's `@Test` so that the very same test sources
// compile in the dev container, where Maven Central is unreachable. Gradle
// builds on the host use the real JUnit artifact instead of this shim.
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
