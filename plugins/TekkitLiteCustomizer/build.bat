@echo off

cd "C:\Program Files\Java\jdk1.7.0_80\bin"

javac -cp "C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer\lib\craftbukkit-1.4.7-R1.1-SNAPSHOT.jar;C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer\lib\mcpcplus.jar" -d C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer\out C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer\src\me\ryanhamshire\TekkitCustomizer\*.java

cd "C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer"

jar -cf TekkitLiteCustomizer.jar -C C:\Users\starr\Documents\GitHub\TekkitLiteCustomizer\out .

echo Build completed.
