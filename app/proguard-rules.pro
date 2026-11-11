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
# Gson models of the pending edits (drafts stored in the database): field and enum names must stay
# the same from one release to the next, or the drafts saved by the previous version are lost.
-keep class com.ninereader.annotations.DraftData { *; }
-keep class com.ninereader.annotations.Annotation { *; }
-keep enum com.ninereader.annotations.Annotation$Type { *; }
-keep class com.ninereader.edit.StyleRun { *; }
# Custom preference referenced from res/xml/preferences.xml
-keep class com.ninereader.ui.settings.ThemePreference { <init>(...); }
