# PdfiumAndroid (JNI)
-keep class io.legere.pdfiumandroid.** { *; }
# PdfBox-Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
# Gson models (backup)
-keep class com.ninereader.data.backup.** { *; }
-keepattributes Signature, *Annotation*
