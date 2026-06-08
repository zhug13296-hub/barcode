# ZXing - keep all barcode encoding/decoding classes
-keep class com.google.zxing.** { *; }
-dontwarn com.google.zxing.**

# ZXing Android Embedded
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.journeyapps.barcodescanner.**

# Material Components
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Keep the app's classes
-keep class com.example.barcodeoffline.** { *; }
