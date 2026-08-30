# Project specific ProGuard rules.
# minify is disabled in this template, but if you ever enable it keep these rules:

# SLF4J binding must survive (logging from emailverifier-kt).
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# The verification library uses reflection-free plain Kotlin, keep everything.
-keep class io.github.mbalatsko.emailverifier.** { *; }
-dontwarn io.github.mbalatsko.emailverifier.**
