#include "renderer.h"
#include "native_globals.h"

#define NOGITSUNE_LOG_TAG "nogitsune-render"
#include "NogitsuneLogger.h"

void renderer_set_lib_dir(const char*) {}

int renderer_set_native_window(void* window) {
    return p_set_native ? p_set_native(window) : -1;
}

int renderer_reset_sub_window(
    void* window, int wx, int wy, int ww, int wh, int fbw, int fbh, float dpr, float z_rot) {
    return p_reset ? p_reset(window, wx, wy, ww, wh, fbw, fbh, dpr, z_rot) : -1;
}

int renderer_start(void* win, int w, int h, int xdpi, int ydpi, int fps) {
    return p_start ? p_start(win, w, h, xdpi, ydpi, fps) : -1;
}

int renderer_remove_sub_window(void* window) {
    return p_remove ? p_remove(window) : -1;
}

void renderer_repaint() {
    if (p_repaint) p_repaint();
}
