# Nothing special yet. Add rules here only when R8 breaks something, with a comment saying what.

# pdfbox-android lists optional image codecs it does not need to read text. Without this R8 stops on the missing classes.
-dontwarn com.gemalto.jp2.**
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.pdfbox.**
