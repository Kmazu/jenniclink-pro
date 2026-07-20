# Custom ProGuard rules for JennicLink Pro
# Add any keep rules here if needed

-keep class com.hoho.android.usbserial.driver.** { *; }
-keep interface com.hoho.android.usbserial.driver.** { *; }
-keep class com.example.jennicflasher.data.** { *; }
-keep class com.example.jennicflasher.ui.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep interface com.jcraft.jsch.** { *; }

-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
-dontwarn org.ietf.jgss.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.slf4j.**

