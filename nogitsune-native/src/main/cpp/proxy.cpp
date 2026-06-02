#include "proxy.h"
#include "input.h"
#include "renderer.h"
#include "native_globals.h"

#include <cstring>
#include <pthread.h>

struct RenderArgs {
    void* window;
    int virtual_w;
    int virtual_h;
    int xdpi;
    int ydpi;
    int fps;
};

static void* render_thread(void* arg) {
    auto* a = static_cast<RenderArgs*>(arg);
    renderer_start(a->window, a->virtual_w, a->virtual_h, a->xdpi, a->ydpi, a->fps);
    delete a;
    return nullptr;
}

void proxy_set_paths(const char* rootfs, const char* log_path) {
    strncpy(g_paths.rootfs, rootfs, sizeof(g_paths.rootfs) - 1);
    strncpy(g_paths.log_path, log_path, sizeof(g_paths.log_path) - 1);
    snprintf(g_paths.touch_sock, sizeof(g_paths.touch_sock), "%s/dev/input/touch", rootfs);
    snprintf(g_paths.key_sock, sizeof(g_paths.key_sock), "%s/dev/input/key0", rootfs);
    input_set_paths(g_paths.touch_sock, g_paths.key_sock);
}

void proxy_set_lib_dir(const char* lib_dir) {
    strncpy(g_lib_dir, lib_dir, sizeof(g_lib_dir) - 1);
}

void proxy_init_renderer(
    ANativeWindow* window,
    const char* loader_path,
    int surface_w,
    int surface_h,
    int virtual_w,
    int virtual_h,
    int xdpi,
    int ydpi,
    int fps) {
    void* win_ptr = window;

    bool expected = false;
    if (!g_renderer_started.compare_exchange_strong(expected, true)) {
        renderer_set_native_window(win_ptr);
        renderer_reset_sub_window(win_ptr, 0, 0, surface_w, surface_h, virtual_w, virtual_h, 1.f, 0.f);
        return;
    }

    input_start_system(virtual_w, virtual_h);

    auto* args = new RenderArgs{win_ptr, virtual_w, virtual_h, xdpi, ydpi, fps};
    pthread_t tid;
    pthread_create(&tid, nullptr, render_thread, args);
    pthread_detach(tid);
    (void)loader_path;
}

void proxy_shutdown() {}

void proxy_reset_runtime() {
    g_renderer_started.store(false);
}

void proxy_reset_window(
    ANativeWindow* window,
    int top,
    int left,
    int width,
    int height,
    int fb_w,
    int fb_h) {
    renderer_reset_sub_window(window, left, top, width, height, fb_w, fb_h, 1.f, 0.f);
}

void proxy_remove_window(ANativeWindow* window) {
    renderer_remove_sub_window(window);
}
