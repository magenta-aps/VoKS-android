VoKS-android
============

This project constitutes the Android app as part of the BComeSafe alarm system. Its purpose is to let users trigger the alarm, stream audio and video to the Shelter, and communicate textually with the shelter.



Building the app
----------------

The project is set up for compilation with Gradle (https://gradle.org), which must be installed on the build machine. At least version 1.2.3 of the Gradle build system is required.

The Android NDK is also required for compilation; If you don't already have it, download from __ https://developer.android.com/ndk/downloads/index.html and install it. Then create a "local.properties" file in the VoKS root folder, and add the line ndk.dir=<path-to-android-ndk>, e.g. "ndk.dir=/opt/android-ndk".

To compile the project, run the following command in the VoKS-android folder: ::

	gradle build

This should create apk files in the app/build/outputs/apk folder



Signing the app
---------------

To create a signed APK file, checkout the "build-with-keystore" branch (note the changes to app/build.gradle), replace the voks.keystore file with a keystore file or symlink, and run gradle build. You will be prompted for the keystore password, key alias and key password, and the compilation will place a signed apk in the app/build/outputs/apk folder.
