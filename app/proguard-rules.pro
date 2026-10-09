# Libraries supply their own consumer rules. Avoid blanket keeps and dontwarn.
# DataStore protobuf accesses generated fields reflectively.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
# WorkManager creates input mergers reflectively. R8 full mode does not keep the no-arg
# constructor implied by the library's class-only keep rule, which fails every work request.
-keepclassmembers class * extends androidx.work.InputMerger { public <init>(); }
