#pragma once

#include <atomic>
#include <jni.h>

struct RuntimePaths {
    char rootfs[512];
    char log_path[512];
    char touch_sock[512];
    char key_sock[512];
};

typedef int (*fn_destroy)(void);
typedef void (*fn_repaint)(void);
typedef int (*fn_set_native)(void*);
typedef int (*fn_reset)(void*, int, int, int, int, int, int, float, float);
typedef int (*fn_start)(void*, int, int, int, int, int);
typedef int (*fn_remove)(void*);

extern fn_destroy p_destroy;
extern fn_repaint p_repaint;
extern fn_set_native p_set_native;
extern fn_reset p_reset;
extern fn_start p_start;
extern fn_remove p_remove;

extern void* g_gl_lib;
extern JavaVM* g_vm;
extern RuntimePaths g_paths;
extern std::atomic<bool> g_renderer_started;
extern char g_lib_dir[512];
