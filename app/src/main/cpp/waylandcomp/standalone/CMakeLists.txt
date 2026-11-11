# Builds only libbannerwayland.so (plus the static adrenotools it links), for quick
# compositor iterations without a full app build. See .github/workflows/build-waylandcomp.yml.
cmake_minimum_required(VERSION 3.22.1)
project(bannerwayland_standalone C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/../../adrenotools adrenotools)
add_subdirectory(${CMAKE_CURRENT_SOURCE_DIR}/.. waylandcomp)
