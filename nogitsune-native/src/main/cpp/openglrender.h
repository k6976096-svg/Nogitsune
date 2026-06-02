#pragma once

extern int destroyOpenGLSubwindow();
extern void repaintOpenGLDisplay();
extern int setNativeWindow(void*);
extern int resetSubWindow(void* p_window, int wx, int wy, int ww, int wh, int fbw, int fbh, float dpr, float zRot);
extern int startOpenGLRenderer(void* win, int width, int height, int xdpi, int ydpi, int fps);
extern int removeSubWindow(void*);
