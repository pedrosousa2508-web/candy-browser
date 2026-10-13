# AndroidJUnitRunner calls Trace before test discovery. R8 cannot see those callers
# while optimizing the application separately from the instrumentation APK.
# Loaded only with -Pcandy.androidTestBuildType=blackHoleTest; app R8 stays enabled.
-keep class androidx.tracing.Trace { *; }

# These APIs are called from the separately compiled test APK. They must remain
# callable even when R8 inlines their uses inside the app. Scope every rule to
# this instrumentation mode instead of reducing optimization in the phone APK.
-keep class dev.sk2andy.materialbrowser.data.AppIconSelection { *; }
-keep class dev.sk2andy.materialbrowser.data.AppIconOption { *; }
# AndroidX Test itself also calls the shared Kotlin runtime (for example LazyKt
# in TestDirCalculator). Keep its API in the instrumented app, since the runner
# is compiled separately and its callers are invisible to application R8.
-keep class kotlin.** { *; }

# CSV instrumentation calls the parser, authorization and encrypted store across APKs.
-keep class dev.sk2andy.materialbrowser.data.passwords.** { *; }
-keep class dev.sk2andy.materialbrowser.data.AppDataArchiveRules { *; }

# Native vault separation test uses its own preference namespace, never production sessions.
-keep class dev.sk2andy.materialbrowser.data.BrowserSessionStore {
    <init>(android.content.SharedPreferences);
    public boolean saveTabsImmediately(java.util.List,java.lang.String);
    public kotlin.Pair loadTabs(long);
}
-keep class dev.sk2andy.materialbrowser.browser.BrowserTab { *; }
