# AndroidJUnitRunner calls Trace before test discovery. R8 cannot see those callers
# while optimizing the application separately from the instrumentation APK.
# Loaded only with -Pcandy.androidTestBuildType=blackHoleTest; app R8 stays enabled.
-keep class androidx.tracing.Trace { *; }
