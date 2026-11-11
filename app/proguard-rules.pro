# Keep Room entities
-keep class com.wayars.app.data.local.entity.** { *; }

# Readable crash stack traces in Play Console (mapping.txt is uploaded with the AAB automatically).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Generic safety attributes (generics, annotations, nested classes).
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
