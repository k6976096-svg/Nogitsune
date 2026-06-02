#include "input.h"

#define NOGITSUNE_LOG_TAG "nogitsune-input"
#include "NogitsuneLogger.h"
#include <linux/input.h>
#include <sys/time.h>
#include <atomic>
#include <cstring>
#include <mutex>
#include <pthread.h>
#include <queue>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <cerrno>
#include <unistd.h>

#ifndef FF_MAX
#define FF_MAX 0x7f
#endif

struct nogitsune_input_id {
    uint16_t bustype;
    uint16_t vendor;
    uint16_t product;
    uint16_t version;
};

struct device_info {
    char name[80];
    int32_t driver_version;
    nogitsune_input_id id;
    char physical_location[80];
    char unique_id[80];
    uint8_t key_bitmask[(KEY_MAX + 1) / 8];
    uint8_t abs_bitmask[(ABS_MAX + 1) / 8];
    uint8_t rel_bitmask[(REL_MAX + 1) / 8];
    uint8_t sw_bitmask[(SW_MAX + 1) / 8];
    uint8_t led_bitmask[(LED_MAX + 1) / 8];
    uint8_t ff_bitmask[(FF_MAX + 1) / 8];
    uint8_t prop_bitmask[(INPUT_PROP_MAX + 1) / 8];
    uint32_t abs_max[ABS_CNT];
    uint32_t abs_min[ABS_CNT];
};

static char g_touch_path[512];
static char g_key_path[512];
static int g_touch_w = 720;
static int g_touch_h = 1280;

static std::mutex g_touch_mtx;
static int g_touch_fd = -1;
static std::queue<input_event> g_touch_q;
static int g_mt_active[5] = {};

static std::mutex g_key_mtx;
static int g_key_fd = -1;
static std::queue<input_event> g_key_q;

static void copy_cstr(char* dst, size_t n, const char* src) {
    if (!dst || n == 0) return;
    strncpy(dst, src, n - 1);
    dst[n - 1] = '\0';
}

static device_info make_touch_device(int width, int height) {
    device_info info{};
    info.driver_version = 1;
    info.id = {0, 0, 1, 0};
    copy_cstr(info.name, sizeof(info.name), "vtouch");
    copy_cstr(info.physical_location, sizeof(info.physical_location), g_touch_path);
    copy_cstr(info.unique_id, sizeof(info.unique_id), "<vtouch 0>");
    info.prop_bitmask[0] = static_cast<uint8_t>(INPUT_PROP_BUTTONPAD);
    info.abs_bitmask[ABS_RZ] = 0x80;
    info.abs_bitmask[ABS_THROTTLE] = 0x60;
    info.abs_bitmask[ABS_RUDDER] = 0x02;
    info.abs_min[ABS_MT_POSITION_X] = 0;
    info.abs_max[ABS_MT_POSITION_X] = static_cast<uint32_t>(width);
    info.abs_min[ABS_MT_POSITION_Y] = 0;
    info.abs_max[ABS_MT_POSITION_Y] = static_cast<uint32_t>(height);
    info.abs_min[ABS_MT_TOUCH_MAJOR] = 0;
    info.abs_min[ABS_MT_TOUCH_MINOR] = 15;
    info.abs_min[ABS_MT_SLOT] = 4;
    info.abs_min[ABS_MT_PRESSURE] = 0;
    info.abs_max[ABS_MT_PRESSURE] = 80;
    return info;
}

static device_info make_key_device() {
    device_info info{};
    info.driver_version = 1;
    info.id = {0, 0, 1, 0};
    copy_cstr(info.name, sizeof(info.name), "vkey");
    copy_cstr(info.physical_location, sizeof(info.physical_location), g_key_path);
    copy_cstr(info.unique_id, sizeof(info.unique_id), "<keyboard 0>");
    info.key_bitmask[14] = 0x1c;
    return info;
}

static void event_time(input_event& ev) {
    gettimeofday(&ev.time, nullptr);
}

void input_set_paths(const char* touch_sock, const char* key_sock) {
    strncpy(g_touch_path, touch_sock, sizeof(g_touch_path) - 1);
    strncpy(g_key_path, key_sock, sizeof(g_key_path) - 1);
}

static void pump_queue(std::mutex& mtx, int& fd, std::queue<input_event>& q) {
    std::unique_lock lock(mtx);
    if (fd < 0 || q.empty()) return;
    auto ev = q.front();
    q.pop();
    int client = fd;
    lock.unlock();
    write(client, &ev, sizeof(ev));
}

static void* touch_pump(void*) {
    while (true) {
        pump_queue(g_touch_mtx, g_touch_fd, g_touch_q);
        usleep(1000);
    }
    return nullptr;
}

static void* key_pump(void*) {
    while (true) {
        pump_queue(g_key_mtx, g_key_fd, g_key_q);
        usleep(1000);
    }
    return nullptr;
}

static bool ensure_parent_dir(const char* path) {
    char buf[512];
    strncpy(buf, path, sizeof(buf) - 1);
    buf[sizeof(buf) - 1] = '\0';
    char* slash = strrchr(buf, '/');
    if (!slash) return true;
    *slash = '\0';
    if (buf[0] == '\0') return true;
    mkdir(buf, 0777);
    return true;
}

static void* touch_server(void*) {
    ensure_parent_dir(g_touch_path);
    unlink(g_touch_path);
    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return nullptr;
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, g_touch_path, sizeof(addr.sun_path) - 1);
    if (bind(srv, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        LOGE("touch bind failed %s errno=%d", g_touch_path, errno);
        close(srv);
        return nullptr;
    }
    listen(srv, 4);
    auto device = make_touch_device(g_touch_w, g_touch_h);
    while (true) {
        int client = accept(srv, nullptr, nullptr);
        if (client < 0) continue;
        write(client, &device, sizeof(device));
        {
            std::lock_guard lock(g_touch_mtx);
            g_touch_fd = client;
            while (!g_touch_q.empty()) g_touch_q.pop();
        }
        memset(g_mt_active, 0, sizeof(g_mt_active));
        LOGI("touch client connected %s", g_touch_path);
    }
    return nullptr;
}

static void* key_server(void*) {
    ensure_parent_dir(g_key_path);
    unlink(g_key_path);
    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return nullptr;
    sockaddr_un addr{};
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, g_key_path, sizeof(addr.sun_path) - 1);
    if (bind(srv, reinterpret_cast<sockaddr*>(&addr), sizeof(addr)) < 0) {
        close(srv);
        return nullptr;
    }
    listen(srv, 4);
    auto device = make_key_device();
    while (true) {
        int client = accept(srv, nullptr, nullptr);
        if (client < 0) continue;
        write(client, &device, sizeof(device));
        {
            std::lock_guard lock(g_key_mtx);
            g_key_fd = client;
            while (!g_key_q.empty()) g_key_q.pop();
        }
        LOGI("key client connected %s", g_key_path);
    }
    return nullptr;
}

static void enqueue_touch(int type, int code, int value) {
    std::lock_guard lock(g_touch_mtx);
    if (g_touch_fd < 0) return;
    input_event ev{};
    ev.type = static_cast<uint16_t>(type);
    ev.code = static_cast<uint16_t>(code);
    ev.value = value;
    event_time(ev);
    g_touch_q.push(ev);
}

static void touch_syn() {
    input_event ev{};
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    event_time(ev);
    std::lock_guard lock(g_touch_mtx);
    if (g_touch_fd < 0) return;
    g_touch_q.push(ev);
}

static int clamp_pointer(int pointer_id) {
    if (pointer_id < 0) return 0;
    if (pointer_id >= 5) return 4;
    return pointer_id;
}

void input_start_system(int width, int height) {
    g_touch_w = width > 0 ? width : 720;
    g_touch_h = height > 0 ? height : 1280;
    pthread_t t1, t2, t3, t4;
    pthread_create(&t1, nullptr, touch_server, nullptr);
    pthread_detach(t1);
    pthread_create(&t2, nullptr, touch_pump, nullptr);
    pthread_detach(t2);
    pthread_create(&t3, nullptr, key_server, nullptr);
    pthread_detach(t3);
    pthread_create(&t4, nullptr, key_pump, nullptr);
    pthread_detach(t4);
}

void input_handle_touch(int action, int pointer_id, float x, float y, float pressure) {
    const int down = 0;
    const int up = 1;
    const int move = 2;
    const int cancel = 3;
    const int pointer_down = 5;
    const int pointer_up = 6;
    const int pid = clamp_pointer(pointer_id);
    const int ix = static_cast<int>(x);
    const int iy = static_cast<int>(y);
    const int ip = static_cast<int>(pressure * 80.f);

    switch (action) {
        case down:
        case pointer_down:
            g_mt_active[pid] = 1;
            enqueue_touch(EV_ABS, ABS_MT_SLOT, pid);
            enqueue_touch(EV_ABS, ABS_MT_TRACKING_ID, pid + 1);
            enqueue_touch(EV_KEY, BTN_TOUCH, 108);
            enqueue_touch(EV_KEY, BTN_TOOL_FINGER, 108);
            enqueue_touch(EV_ABS, ABS_MT_POSITION_X, ix);
            enqueue_touch(EV_ABS, ABS_MT_POSITION_Y, iy);
            enqueue_touch(EV_ABS, ABS_MT_PRESSURE, ip);
            touch_syn();
            break;
        case move:
            if (!g_mt_active[pid]) g_mt_active[pid] = 1;
            enqueue_touch(EV_ABS, ABS_MT_SLOT, pid);
            enqueue_touch(EV_ABS, ABS_MT_POSITION_X, ix);
            enqueue_touch(EV_ABS, ABS_MT_POSITION_Y, iy);
            enqueue_touch(EV_ABS, ABS_MT_PRESSURE, ip);
            touch_syn();
            break;
        case up:
            for (int i = 0; i < 5; i++) {
                if (!g_mt_active[i]) continue;
                g_mt_active[i] = 0;
                enqueue_touch(EV_ABS, ABS_MT_SLOT, i);
                enqueue_touch(EV_ABS, ABS_MT_TRACKING_ID, -1);
                touch_syn();
            }
            break;
        case cancel:
        case pointer_up:
            if (!g_mt_active[pid]) return;
            g_mt_active[pid] = 0;
            enqueue_touch(EV_ABS, ABS_MT_SLOT, pid);
            enqueue_touch(EV_ABS, ABS_MT_TRACKING_ID, -1);
            touch_syn();
            break;
        default:
            break;
    }
}

void input_send_keycode(int keycode) {
    (void)keycode;
    std::lock_guard lock(g_key_mtx);
    if (g_key_fd < 0) return;
    input_event ev{};
    ev.type = EV_KEY;
    ev.code = KEY_BACK;
    ev.value = 1;
    event_time(ev);
    g_key_q.push(ev);
    ev.type = EV_SYN;
    ev.code = SYN_REPORT;
    ev.value = 0;
    event_time(ev);
    g_key_q.push(ev);
    ev.type = EV_KEY;
    ev.code = KEY_BACK;
    ev.value = 0;
    event_time(ev);
    g_key_q.push(ev);
}
