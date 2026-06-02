cmake_minimum_required(VERSION 3.22.1)
project(nogitsune)

set(CMAKE_CXX_STANDARD 17)

add_library(nogitsune SHARED
    src/main/cpp/android_binder.cpp
    src/main/cpp/native_globals.cpp
    src/main/cpp/proxy.cpp
    src/main/cpp/input.cpp
    src/main/cpp/renderer.cpp
)

find_library(log-lib log)
find_library(android-lib android)

target_include_directories(nogitsune PRIVATE
    src/main/cpp
    ${ANDROID_NDK}/sysroot/usr/include
)

target_link_libraries(nogitsune
    ${log-lib}
    ${android-lib}
    dl
)

target_link_options(nogitsune PRIVATE
    -Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/libnogitsune.map
)
