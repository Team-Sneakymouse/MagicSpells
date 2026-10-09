rootProject.name = "MagicSpellsParent"

val localGradleProperties = java.util.Properties().also { properties ->
    val localGradlePropertiesFile = file(".gradle/gradle.properties")
    if (localGradlePropertiesFile.isFile) {
        localGradlePropertiesFile.inputStream().use { properties.load(it) }
    }
}

// Expose repo-local sneakyrp credentials for findProperty() / PasswordCredentials.
gradle.beforeProject {
    localGradleProperties.forEach { key, value ->
        val name = key.toString()
        if (name.startsWith("sneakyrp") || findProperty(name) == null) {
            extensions.extraProperties.set(name, value.toString())
        }
    }
}

include("core")
include("factions")
include("memory")
include("shop")
include("teams")
include("towny")

include(":nms:shared")
include(":nms:v26_2")

startParameter.isParallelProjectExecutionEnabled = true

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
