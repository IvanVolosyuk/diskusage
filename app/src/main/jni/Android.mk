# Copyright (C) 2009 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := scan
LOCAL_SRC_FILES := scan.c

# It seems that Google will not allow to run executable from app private
# directory like `/data/data` or /data/user/0` since Android 10. A workaround
# for this is that renaming executable like `libxxx.so`, and Gradle build
# tools will pack it into apk, and app can run it from native library
# directory. But it seems that marking BUILD_SHARED_LIBRARY is the only way to
# do so for Android.mk.
include $(BUILD_SHARED_LIBRARY)
