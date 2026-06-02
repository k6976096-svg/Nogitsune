#include "android_binder.h"
#include "jni_bridge.h"
#include "proxy.h"
#include "input.h"
#include "renderer.h"
#include "native_globals.h"

#define NOGITSUNE_LOG_TAG "nogitsune"
#include "NogitsuneLogger.h"

#include <android/native_window_jni.h>
#include <dlfcn.h>
#include <cstring>

static bool load_gl(const char* lib_dir) {
    const char* name = "libOpenglRender.so";
    char path[512];
    snprintf(path, sizeof(path), "%s/%s", lib_dir, name);

    g_gl_lib = dlopen(path, RTLD_NOW);
    if (!g_gl_lib) {
        g_gl_lib = dlopen(name, RTLD_NOW);
    }
    if (!g_gl_lib) {
        LOGE("dlopen %s failed: %s", name, dlerror());
        return false;
    }

    p_destroy = (fn_destroy)dlsym(g_gl_lib, "destroyOpenGLSubwindow");
    p_repaint = (fn_repaint)dlsym(g_gl_lib, "repaintOpenGLDisplay");
    p_set_native = (fn_set_native)dlsym(g_gl_lib, "setNativeWindow");
    p_reset = (fn_reset)dlsym(g_gl_lib, "resetSubWindow");
    p_start = (fn_start)dlsym(g_gl_lib, "startOpenGLRenderer");
    p_remove = (fn_remove)dlsym(g_gl_lib, "removeSubWindow");

    LOGI("loaded %s ok=%d", name, p_start != nullptr);
    return p_start && p_reset && p_set_native;
}

static void unload_gl() {
    if (g_gl_lib) {
        dlclose(g_gl_lib);
        g_gl_lib = nullptr;
    }
    p_destroy = nullptr;
    p_repaint = nullptr;
    p_set_native = nullptr;
    p_reset = nullptr;
    p_start = nullptr;
    p_remove = nullptr;
}

static void native_set_paths(JNIEnv* env, jclass, jstring rootfs, jstring log_path, jstring lib_dir) {
    const char* r = env->GetStringUTFChars(rootfs, nullptr);
    const char* l = env->GetStringUTFChars(log_path, nullptr);
    const char* d = env->GetStringUTFChars(lib_dir, nullptr);
    proxy_set_paths(r, l);
    proxy_set_lib_dir(d);
    if (!g_gl_lib && d[0]) {
        load_gl(d);
    }
    env->ReleaseStringUTFChars(rootfs, r);
    env->ReleaseStringUTFChars(log_path, l);
    env->ReleaseStringUTFChars(lib_dir, d);
}

static void native_init(
    JNIEnv* env,
    jclass,
    jobject surface,
    jstring loader,
    jint width,
    jint height,
    jfloat xdpi,
    jfloat ydpi,
    jint fps) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    int surface_w = ANativeWindow_getWidth(window);
    int surface_h = ANativeWindow_getHeight(window);
    const char* loader_path = env->GetStringUTFChars(loader, nullptr);
    proxy_init_renderer(
        window, loader_path, surface_w, surface_h,
        width, height, static_cast<int>(xdpi), static_cast<int>(ydpi), fps);
    env->ReleaseStringUTFChars(loader, loader_path);
}

static void native_reset_window(
    JNIEnv* env,
    jclass,
    jobject surface,
    jint top,
    jint left,
    jint width,
    jint height,
    jint fb_width,
    jint fb_height) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    proxy_reset_window(window, top, left, width, height, fb_width, fb_height);
}

static void native_remove_window(JNIEnv* env, jclass, jobject surface) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    proxy_remove_window(window);
}

static void native_handle_touch(JNIEnv*, jclass, jint action, jint pointer_id, jfloat x, jfloat y, jfloat pressure) {
    input_handle_touch(action, pointer_id, x, y, pressure);
}

static void native_send_keycode(JNIEnv*, jclass, jint keycode) {
    input_send_keycode(keycode);
}

static void native_set_debug_renderer(JNIEnv*, jclass, jint) {}

static void native_shutdown(JNIEnv*, jclass) {
    proxy_shutdown();
}

static void native_reset_runtime(JNIEnv*, jclass) {
    proxy_reset_runtime();
}

static void native_repaint(JNIEnv*, jclass) {
    renderer_repaint();
}

JNINativeMethod renderer_methods[] = {
    {"nativeSetPaths", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", (void*)native_set_paths},
    {"init", "(Landroid/view/Surface;Ljava/lang/String;IIFFI)V", (void*)native_init},
    {"resetWindow", "(Landroid/view/Surface;IIIIII)V", (void*)native_reset_window},
    {"removeWindow", "(Landroid/view/Surface;)V", (void*)native_remove_window},
    {"handleTouch", "(IIFFF)V", (void*)native_handle_touch},
    {"sendKeycode", "(I)V", (void*)native_send_keycode},
    {"setDebugRenderer", "(I)V", (void*)native_set_debug_renderer},
    {"shutdown", "()V", (void*)native_shutdown},
    {"resetRuntime", "()V", (void*)native_reset_runtime},
    {"repaint", "()V", (void*)native_repaint},
};

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    REGISTER_RENDERER_BINDINGS(env);
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNI_OnUnload(JavaVM*, void*) {
    unload_gl();
    g_vm = nullptr;
}
