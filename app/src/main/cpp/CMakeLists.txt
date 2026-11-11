cmake_minimum_required(VERSION 3.22.1)

project(Winlator)

# Disable clang-tidy (AGP enables it by default but its include paths
# don't work with NDK cross-compilation for the lsfg-vk dependency)


set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(OpenXR-SDK)
add_subdirectory(patchelf)
add_subdirectory(adrenotools)

# DXVK's DXBC->SPIR-V translator (zlib licence), vendored under thirdparty/dxbc
# from the same subset upstream lsfg-vk uses. Required by native LSFG frame
# generation: the Lossless Scaling shader chain is DXBC in every build that is
# downloadable from Steam, so the precompiled-SPIR-V path alone is not enough.
add_subdirectory(thirdparty/dxbc)
# lsfg-vk is built separately by build-lsfg.yml (standalone cmake)
# and its .so is copied to jniLibs. AGP's cmake integration enables
# clang-tidy which fails on NDK cross-compile of the lsfg-vk headers.
# add_subdirectory(lsfg-vk)

# Add Winlator shared library
add_library(winlator SHARED
        xr/engine.c
        xr/framebuffer.c
        xr/input.c
        xr/main.c
        xr/math.c
        xr/renderer.c
        winlator/drawable.c
        winlator/gpu_image.c
        winlator/sysvshared_memory.c
        winlator/xconnector_epoll.c
        winlator/alsa_client.c
        winlator/vulkan.c
        winlator/perf_turbo.c
        winlator/mali_counters.cpp
        winlator/ring_fence.c
        winlator/renderer/egl.cpp
        winlator/renderer/renderer_jni.cpp
        winlator/renderer/shader.cpp
        winlator/renderer/window.cpp
        winlator/renderer/cursor.cpp
        winlator/renderer/xform.cpp
        winlator/renderer/view_transformation.cpp
        winlator/renderer/surfacedirect.cpp
        winlator/renderer/effectcomposer/effect_composer.cpp
)

target_compile_options(winlator PRIVATE -Wall -Wextra)
target_include_directories(winlator PRIVATE winlator/renderer winlator/renderer/effectcomposer)

target_link_libraries(winlator
        log
        android
        jnigraphics
        openxr_loader
        aaudio
        EGL
        GLESv2
        GLESv3
        adrenotools
        vulkan
)

# DrawableBufferPool native heap (ERL bug report #2): tiny dependency-free lib
# (libc + JNI only) backing the X-server Drawable buffer pool with calloc/free in
# the native heap, bypassing ART's small non-compacting non-moving space which
# fragments under cursor/drawable churn. Own .so so it stays off every other path.
add_library(drawablepool SHARED
        winlator/drawablepool.c
)

target_compile_options(drawablepool PRIVATE -Wall -Wextra)

target_link_libraries(drawablepool
        log
)

# Add Fake Input library for controller emulation (loaded via LD_PRELOAD)
add_library(fakeinput SHARED
        winlator/fakeinput.cpp
)

target_compile_options(fakeinput PRIVATE -Wall -Wextra -fvisibility=hidden)

# HERMES, the controller-input transport reader (Rust, hermes/): a static library with a plain
# C ABI (hermes/include/hermes.h), built by CI before Gradle runs (hermes/ci/build.sh from
# .github/workflows/_build.yml). It depends on libc/libdl/pthread only and is linked with
# --exclude-libs so none of its symbols is exported from the preloaded library.
set(HERMES_DIR ${CMAKE_CURRENT_SOURCE_DIR}/hermes)
set(HERMES_GUEST_LIB ${HERMES_DIR}/target/aarch64-linux-android/release/libhermes_guest.a)
if(NOT EXISTS ${HERMES_GUEST_LIB})
    message(FATAL_ERROR "hermes: ${HERMES_GUEST_LIB} is missing. Build it first: "
            "NDK_BIN=<ndk>/toolchains/llvm/prebuilt/linux-x86_64/bin hermes/ci/build.sh android")
endif()
add_library(hermes_guest STATIC IMPORTED)
set_target_properties(hermes_guest PROPERTIES
        IMPORTED_LOCATION ${HERMES_GUEST_LIB}
        INTERFACE_INCLUDE_DIRECTORIES ${HERMES_DIR}/include
)

target_link_libraries(fakeinput
        log
        dl
        hermes_guest
)
target_link_options(fakeinput PRIVATE "-Wl,--exclude-libs,libhermes_guest.a")

add_library(vulkan_renderer SHARED
        winlator/vulkan_jni.cpp
        winlator/stream_jni.cpp
        winlator/VulkanRendererContext.cpp
        winlator/VulkanRendererScanout.cpp
        winlator/lsfg/lsfg_probe.cpp
        winlator/lsfg/lsfg_dll.cpp
        winlator/lsfg/lsfg_dxbc.cpp
        winlator/lsfg/lsfg_vkd.cpp
        winlator/lsfg/lsfg_common.cpp
        winlator/lsfg/lsfg_shaders.cpp
        winlator/lsfg/lsfg_mipmaps.cpp
        winlator/lsfg/lsfg_alpha.cpp
        winlator/lsfg/lsfg_beta.cpp
        winlator/lsfg/lsfg_gamma.cpp
        winlator/lsfg/lsfg_delta.cpp
        winlator/lsfg/lsfg_generate.cpp
        winlator/lsfg/lsfg_chain.cpp
        winlator/lsfg/lsfg_pacer.cpp
        winlator/lsfg/lsfg_engine.cpp
        winlator/lsfg/lsfg_governor.cpp
        winlator/lsfg/lsfg_jni.cpp
        winlator/winfg/framegen.cpp
        winlator/winfg/winfg_vkd.cpp
        winlator/winfg/winfg_engine.cpp
        scanout/ScanoutContext.cpp
)

# --- win-fg chain provenance -------------------------------------------------
# The win-fg chain is COPIED into this tree from github.com/The412Banner/win-fg
# rather than linked, so nothing about it self-identifies: a device log could
# say which Bannerlator commit it came from, but not which win-fg. The layer
# build solves this by stamping `git describe` into libwin_fg.so; the native
# path needs its own answer.
#
# Two halves, deliberately:
#   WINFG_UPSTREAM   the upstream tag the sources were copied from. Hand-set,
#                    so UPDATE IT when you re-copy the chain.
#   WINFG_CHAIN_HASH a hash over the chain sources themselves, computed here at
#                    configure time. This one cannot rot - edit any chain file
#                    and it changes, including a local edit that never went
#                    upstream, which is exactly the case a hand-set tag hides.
set(WINFG_UPSTREAM "v0.3.0")
set(WINFG_CHAIN_SRC
        winlator/winfg/framegen.cpp
        winlator/winfg/framegen.hpp
        winlator/winfg/record_impl.inc
        winlator/winfg/config.hpp
        winlator/winfg/embedded_shaders.hpp
        winlator/winfg/winfg_engine.cpp
)
set(WINFG_HASH_INPUT "")
foreach(f ${WINFG_CHAIN_SRC})
    file(SHA256 "${CMAKE_CURRENT_SOURCE_DIR}/${f}" _h)
    string(APPEND WINFG_HASH_INPUT "${_h}")
endforeach()
string(SHA256 WINFG_CHAIN_HASH_FULL "${WINFG_HASH_INPUT}")
string(SUBSTRING "${WINFG_CHAIN_HASH_FULL}" 0 12 WINFG_CHAIN_HASH)
message(STATUS "win-fg chain: ${WINFG_UPSTREAM} (src ${WINFG_CHAIN_HASH})")

target_compile_definitions(vulkan_renderer PRIVATE
        WINFG_UPSTREAM="${WINFG_UPSTREAM}"
        WINFG_CHAIN_HASH="${WINFG_CHAIN_HASH}"
)

target_compile_options(vulkan_renderer PRIVATE
        -Wall -Wextra -fvisibility=hidden -std=c++17
)

target_link_libraries(vulkan_renderer
        log
        android
        vulkan
        adrenotools
        dxbc
        dl
        atomic
)

# pasink — tiny PulseAudio client (dlopen-based) to suspend/resume the guest audio sink and recover
# the output route after backgrounding / HDMI route changes. No pulse link deps (pure dlopen/dlsym).
add_library(pasink SHARED
        pasink/pasink.c
)

target_link_libraries(pasink
        log
        dl
)

# steamctrl — optional Steam Controller support (2015 + 2026 models, Bluetooth LE / USB). A small
# JNI bridge over SDL3's gamepad API; SDL3 comes from the vendored official release AAR through
# prefab (buildFeatures.prefab). Linking SDL3::SDL3 is also what packages libSDL3.so into the APK
# (the AAR has no jni/ folder). Only SDL's HIDAPI Steam drivers are enabled at runtime, so SDL opens
# no other controller — see steamctrl/steam_controller_bridge.cpp.
find_package(SDL3 REQUIRED CONFIG)

add_library(steamctrl SHARED
        steamctrl/steam_controller_bridge.cpp
)

target_link_libraries(steamctrl
        SDL3::SDL3
        log
)

# Embedded Wayland compositor (experimental parallel runtime) — see waylandcomp/CMakeLists.txt.
add_subdirectory(waylandcomp)
