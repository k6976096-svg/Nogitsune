#pragma once

#include <jni.h>

#define REGISTER_RENDERER_BINDINGS(env) \
    do { \
        jclass cls = env->FindClass("io/kitsuri/nogitsune/globals/Renderer"); \
        if (!cls) return JNI_ERR; \
        if (env->RegisterNatives(cls, renderer_methods, \
                sizeof(renderer_methods) / sizeof(renderer_methods[0])) < 0) { \
            return JNI_ERR; \
        } \
    } while (0)

extern JNINativeMethod renderer_methods[];
