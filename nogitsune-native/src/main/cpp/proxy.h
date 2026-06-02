#pragma once

#include <android/native_window_jni.h>
#include <jni.h>

void proxy_set_paths(const char* rootfs, const char* log_path);
void proxy_set_lib_dir(const char* lib_dir);

void proxy_init_renderer(
    ANativeWindow* window,
    const char* loader_path,
    int surface_w,
    int surface_h,
    int virtual_w,
    int virtual_h,
    int xdpi,
    int ydpi,
    int fps
);

void proxy_reset_window(
    ANativeWindow* window,
    int top,
    int left,
    int width,
    int height,
    int fb_w,
    int fb_h
);

void proxy_remove_window(ANativeWindow* window);
void proxy_shutdown();
void proxy_reset_runtime();
