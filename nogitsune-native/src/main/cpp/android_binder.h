#pragma once

#include <jni.h>

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved);
extern "C" JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved);
