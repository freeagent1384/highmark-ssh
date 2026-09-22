-keep class com.trilead.ssh2.** { *; }
-dontwarn com.trilead.ssh2.**
-dontwarn org.slf4j.**
-dontwarn com.google.errorprone.annotations.CanIgnoreReturnValue
-dontwarn com.google.errorprone.annotations.Immutable

# Gson-persisted models: keep field names and @SerializedName annotations so the
# stored JSON format doesn't change between builds. See ConnectionProfile.
-keepattributes Signature, *Annotation*
-keepclassmembers class com.questterm.data.ConnectionProfile { <fields>; }
-keepclassmembers enum com.questterm.data.AuthMethod { <fields>; }
