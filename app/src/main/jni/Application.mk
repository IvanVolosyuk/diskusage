# ndk-build configuration for the scanner helper.
#
# The library is loaded through System.loadLibrary(), no longer executed as a
# child process, so the default (compressed, aligned) packaging is fine.

APP_PLATFORM := android-24
APP_ABI := arm64-v8a armeabi-v7a x86 x86_64

# Build every 64-bit library 16 KB page-size compatible (required on Android 15+
# devices with 16 KB pages). This makes the linker align LOAD segments to 16 KB.
APP_SUPPORT_FLEXIBLE_PAGE_SIZES := true

APP_CFLAGS := -O2 -Wall -Wextra -Wno-unused-parameter
