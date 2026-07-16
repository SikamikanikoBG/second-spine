# R8 is on because the unminified APK carries ~67 MB of dex. See app/build.gradle.kts.

# The coach brain is reflected over by nothing, but its enum names ARE the moral spine: the frozen
# Target enum test, the banned-lexicon lint and the register grammar all key on enum identity.
# Keep them intact so a release build cannot quietly differ from the one the tests proved.
-keepclassmembers enum com.secondspine.coach.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** entries;
}

# Room generates implementations reflectively resolved at runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# WorkManager instantiates Workers by name from the DB. Losing these means the ledger never purges
# and the export never runs, silently, in release only.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# ML Kit loads its pipelines via native/reflective glue.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**

# Kotlin metadata used by kotlin.test-free reflection paths and serialization of enum names.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
