plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.spotless)
}

// Same formatter and style as tools/dev/ktfmt.sh, which is used inside the
// dev container where Gradle cannot reach Maven repositories.
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "tmp/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.kts")
        targetExclude("**/build/**", "tmp/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}
