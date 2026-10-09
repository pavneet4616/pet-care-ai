# Libraries supply their own consumer rules. Avoid blanket keeps and dontwarn.
# DataStore protobuf accesses generated fields reflectively.
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*
