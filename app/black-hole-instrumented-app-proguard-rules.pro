# AndroidJUnitRunner calls Trace before test discovery. R8 cannot see those callers
# while optimizing the application separately from the instrumentation APK.
# Loaded only with -Pcandy.androidTestBuildType=blackHoleTest; app R8 stays enabled.
-keep class androidx.tracing.Trace { *; }

# These APIs are called from the separately compiled test APK. They must remain
# callable even when R8 inlines their uses inside the app. Scope every rule to
# this instrumentation mode instead of reducing optimization in the phone APK.
-keep class dev.sk2andy.materialbrowser.data.AppIconSelection { *; }
-keep class dev.sk2andy.materialbrowser.data.AppIconOption { *; }
-keep class kotlin.collections.CollectionsKt* {
    public static java.util.List listOf(java.lang.Object[]);
    public static java.lang.Object single(java.util.List);
    public static int collectionSizeOrDefault(java.lang.Iterable, int);
}
-keep class kotlin.text.StringsKt* {
    public static boolean contains$default(java.lang.CharSequence, java.lang.CharSequence, boolean, int, java.lang.Object);
}
