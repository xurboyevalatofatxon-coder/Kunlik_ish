plugins { id("org.jetbrains.kotlin.jvm") }
kotlin { jvmToolchain(17) }
sourceSets.test { kotlin.srcDir("src/testSupport/kotlin") }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { testLogging { events("passed", "skipped", "failed") } }
