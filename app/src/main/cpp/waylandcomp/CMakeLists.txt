# ── Embedded Wayland compositor (experimental parallel runtime) ──────────────
# Runs a libwayland-server compositor in-process so games launched via Wine's
# winewayland.drv can present into it. Protocol glue is pre-generated in
# generated/ (no host wayland-scanner needed at app build time). Built by the app
# (add_subdirectory from ../CMakeLists.txt) and on its own by standalone/ for
# build-waylandcomp.yml.
add_library(bannerwayland SHARED
        src/compositor.c
        src/vk_present.c
        src/effects_chain.c
        src/vk_loader.c
        src/sc_layer.c
        src/ahb_swapchain.c
        src/driver_probe.c
        src/waylandcomp_jni.c
        src/banner_ext.c
        src/wl_clipboard.c
        src/wl_text_input.c
        src/wl_toplevel_icon.c
        src/wl_color_mgmt.c
        src/hdr_compose.c
        src/blend_pass.c
        generated/color-management-v1-protocol.c
        generated/xdg-shell-protocol.c
        generated/linux-dmabuf-v1-protocol.c
        generated/viewporter-protocol.c
        generated/banner-desktop-v1-protocol.c
        generated/presentation-time-protocol.c
        generated/pointer-constraints-unstable-v1-protocol.c
        generated/relative-pointer-unstable-v1-protocol.c
        generated/wlr-data-control-unstable-v1-protocol.c
        generated/ext-data-control-v1-protocol.c
        generated/text-input-unstable-v3-protocol.c
        generated/xdg-toplevel-icon-v1-protocol.c
        generated/banner-ahb-v1-protocol.c
        src/framegen_bridge.c
        src/framegen_jni.c
)
target_include_directories(bannerwayland PRIVATE
        generated
        prebuilt/include
        src
        # The X11 Vulkan renderer's committed SPIR-V headers (*_frag.h / upscale_vert.h, made by
        # winlator/gen_shaders.sh): the screen-effect chain (src/effects_chain.c) runs the same bytes.
        ${CMAKE_CURRENT_SOURCE_DIR}/../winlator
)

# ── Frame generation (feat/wayland-framegen) ─────────────────────────────────
# The two native engines the X11 renderer runs (LSFG Native: winlator/lsfg, Win-FG Native:
# winlator/winfg) are compiled into this library unmodified and driven by
# src/framegen_engine.cpp through the compositor's own Turnip entry points. They need the
# winlator tree and the vendored DXBC translator (thirdparty/dxbc, for the LSFG cache format),
# which only the app build has: the standalone build (standalone/) gets the stub, which reports
# every engine unavailable and generates nothing.
set(BANNER_FRAMEGEN_LSFG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../winlator/lsfg)
set(BANNER_FRAMEGEN_WINFG_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../winlator/winfg)
if (TARGET dxbc AND EXISTS ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_engine.cpp
        AND EXISTS ${BANNER_FRAMEGEN_WINFG_DIR}/winfg_engine.cpp)
    message(STATUS "bannerwayland: frame-generation engines built in (LSFG Native, Win-FG Native)")
    target_sources(bannerwayland PRIVATE
            src/framegen_engine.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_probe.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_dll.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_dxbc.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_vkd.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_common.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_shaders.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_mipmaps.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_alpha.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_beta.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_gamma.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_delta.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_generate.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_chain.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_pacer.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_engine.cpp
            ${BANNER_FRAMEGEN_LSFG_DIR}/lsfg_governor.cpp
            ${BANNER_FRAMEGEN_WINFG_DIR}/framegen.cpp
            ${BANNER_FRAMEGEN_WINFG_DIR}/winfg_vkd.cpp
            ${BANNER_FRAMEGEN_WINFG_DIR}/winfg_engine.cpp
    )
    target_include_directories(bannerwayland PRIVATE
            ${CMAKE_CURRENT_SOURCE_DIR}/..            # <thirdparty/...> as the winlator target sees it
            ${CMAKE_CURRENT_SOURCE_DIR}/../winlator   # VulkanRendererContext.h (VkTable), lsfg/, winfg/
    )
    target_compile_options(bannerwayland PRIVATE
            $<$<COMPILE_LANGUAGE:CXX>:-std=c++17 -fvisibility=hidden>)
    target_compile_definitions(bannerwayland PRIVATE
            WINFG_UPSTREAM="${WINFG_UPSTREAM}"
            WINFG_CHAIN_HASH="${WINFG_CHAIN_HASH}"
    )
    target_link_libraries(bannerwayland dxbc atomic)
else()
    message(STATUS "bannerwayland: frame-generation engines NOT built in (standalone build): stub")
    target_sources(bannerwayland PRIVATE src/framegen_engine_stub.c)
endif()
add_library(wayland-server SHARED IMPORTED)
set_target_properties(wayland-server PROPERTIES IMPORTED_LOCATION
        ${CMAKE_CURRENT_SOURCE_DIR}/prebuilt/lib/libwayland-server.so)
# Vulkan is loaded at runtime via adrenotools (Turnip), not linked directly — so the
# compositor uses Turnip's driver, which supports dmabuf import (drm_format_modifier).
target_link_libraries(bannerwayland
        wayland-server
        adrenotools
        dl
        log
        android
)
