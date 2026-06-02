#pragma once

void renderer_set_lib_dir(const char* lib_dir);
int renderer_set_native_window(void* window);
int renderer_reset_sub_window(void* window, int wx, int wy, int ww, int wh, int fbw, int fbh, float dpr, float z_rot);
int renderer_start(void* win, int w, int h, int xdpi, int ydpi, int fps);
int renderer_remove_sub_window(void* window);
void renderer_repaint();
