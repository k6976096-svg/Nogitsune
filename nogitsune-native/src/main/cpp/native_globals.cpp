#include "native_globals.h"

fn_destroy p_destroy = nullptr;
fn_repaint p_repaint = nullptr;
fn_set_native p_set_native = nullptr;
fn_reset p_reset = nullptr;
fn_start p_start = nullptr;
fn_remove p_remove = nullptr;

void* g_gl_lib = nullptr;
JavaVM* g_vm = nullptr;
RuntimePaths g_paths{};
std::atomic<bool> g_renderer_started{false};
char g_lib_dir[512]{};
