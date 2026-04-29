# =============================================================================
# Sermon Timer — release proguard / R8 rules
# =============================================================================

# Keep stack traces useful in crash reports.
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes Signature
-keepattributes InnerClasses

# -----------------------------------------------------------------------------
# Wear ProtoLayout / Tile dynamic expressions.
# R8 silently strips these and tile inflate fails at runtime with no log.
# -----------------------------------------------------------------------------
-keep class androidx.wear.protolayout.** { *; }
-keep class androidx.wear.protolayout.expression.** { *; }
-keep class androidx.wear.protolayout.material.** { *; }
-keep class androidx.wear.tiles.** { *; }
-dontwarn androidx.wear.protolayout.**
-dontwarn androidx.wear.tiles.**

# -----------------------------------------------------------------------------
# Watch face complications: data sources are bound by the system via the
# manifest; their classes and entry points must survive shrinking.
# -----------------------------------------------------------------------------
-keep class androidx.wear.watchface.complications.** { *; }
-keep class androidx.wear.watchface.complications.data.** { *; }
-keep class androidx.wear.watchface.complications.datasource.** { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.ComplicationDataSourceService { *; }
-keep class * extends androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService { *; }

# -----------------------------------------------------------------------------
# Wear Ongoing Activity — Status / parts must survive (system parses parcels).
# -----------------------------------------------------------------------------
-keep class androidx.wear.ongoing.** { *; }
-dontwarn androidx.wear.ongoing.**

# -----------------------------------------------------------------------------
# Our Service / Activity entry points referenced by AndroidManifest.
# -----------------------------------------------------------------------------
-keep class com.example.sermontimer.service.TimerService { *; }
-keep class com.example.sermontimer.tile.SermonTileService { *; }
-keep class com.example.sermontimer.tile.TileActionActivity { *; }
-keep class com.example.sermontimer.complication.TimerComplicationService { *; }
-keep class com.example.sermontimer.presentation.MainActivity { *; }
-keep class com.example.sermontimer.SermonTimerApplication { *; }

# -----------------------------------------------------------------------------
# kotlinx.serialization — keep generated serializers and metadata.
# -----------------------------------------------------------------------------
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
    kotlinx.serialization.KSerializer serializer(...);
    static **$* *;
}
-keep class com.example.sermontimer.domain.model.** { *; }

# -----------------------------------------------------------------------------
# Compose / Wear Compose — already covered by AAR rules but pin defensively.
# -----------------------------------------------------------------------------
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.wear.compose.** { *; }
-dontwarn androidx.compose.**
-dontwarn androidx.wear.compose.**

# -----------------------------------------------------------------------------
# Foreground service / Notifications — Notification channels need defaults.
# -----------------------------------------------------------------------------
-keep class androidx.core.app.** { *; }

# -----------------------------------------------------------------------------
# Strip noisy logs in release.
# -----------------------------------------------------------------------------
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
}
