LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := boringLibSharedSystemUI
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_CLASS := JAVA_LIBRARIES
LOCAL_SRC_FILES := libs/sysui_shared.jar
LOCAL_UNINSTALLABLE_MODULE := true
LOCAL_SDK_VERSION := current
include $(BUILD_PREBUILT)

include $(CLEAR_VARS)

LOCAL_USE_AAPT2 := true

LOCAL_PACKAGE_NAME := BoringdroidSystemUI

LOCAL_JAVA_LIBRARIES := SystemUIPluginLib

LOCAL_STATIC_JAVA_LIBRARIES := libSharedSystemUI

LOCAL_STATIC_ANDROID_LIBRARIES := \
    androidx.recyclerview_recyclerview

LOCAL_PRIVATE_PLATFORM_APIS := true
LOCAL_CERTIFICATE := platform
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_SRC_FILES := $(call all-java-files-under, src)

include $(BUILD_PACKAGE)
