# Room genera implementazioni via annotation processor: nessuna regola aggiuntiva necessaria.

# ML Kit scarica i modelli a runtime; mantiene le classi referenziate via reflection.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# WorkManager istanzia i Worker per nome classe.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
