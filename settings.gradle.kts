pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack hosts the FOSS Tesseract4Android OCR engine (not on Maven Central). Scoped to
        // that one group so it can't shadow other dependencies (screen-context fallback_engine).
        maven {
            url = uri("https://jitpack.io")
            content { includeGroup("com.github.adaptech-cz.Tesseract4Android") }
        }
    }
}

rootProject.name = "Equerry"
include(":app")
