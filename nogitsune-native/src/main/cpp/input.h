#pragma once

#include <android/input.h>

void input_set_paths(const char* touch_sock, const char* key_sock);
void input_start_system(int width, int height);
void input_handle_touch(int action, int pointer_id, float x, float y, float pressure);
void input_send_keycode(int keycode);
