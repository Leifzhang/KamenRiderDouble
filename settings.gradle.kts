pluginManagement {
    repositories {
        maven { setUrl("https://maven.aliyun.com/repository/central/") }
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "binary-compatibility-validator" -> {
                    useModule("org.jetbrains.kotlinx:binary-compatibility-validator:${requested.version}")
                }
                "com.android.application", "com.android.library" -> {
                    useModule("com.android.tools.build:gradle:${requested.version}")
                }
            }
        }
    }
}
include(":joker")
include(":cyclone")
include(":henshin")
