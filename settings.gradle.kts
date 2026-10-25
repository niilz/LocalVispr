pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // sherpa-onnx publishes its Android AAR (native libs + Kotlin API) only via JitPack.
        maven("https://jitpack.io") { content { includeGroup("com.github.k2-fsa") } }
    }
}

rootProject.name = "LocalVispr"

include(":app")
