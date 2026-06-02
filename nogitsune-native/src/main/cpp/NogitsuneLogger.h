#pragma once

#include <android/log.h>

#ifndef NOGITSUNE_LOG_TAG
#error define NOGITSUNE_LOG_TAG as a string literal before including NogitsuneLogger.h
#endif

#ifndef NDEBUG
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, NOGITSUNE_LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, NOGITSUNE_LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, NOGITSUNE_LOG_TAG, __VA_ARGS__)
#else
#define LOGI(...)
#define LOGE(...)
#define LOGW(...)
#endif
