# AndroidJUnitRunner/JUnit discover the harness by reflection. These rules apply ONLY to
# the instrumentation APK through testProguardFiles, never the optimized application.
-dontshrink
-dontoptimize
-dontobfuscate
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
