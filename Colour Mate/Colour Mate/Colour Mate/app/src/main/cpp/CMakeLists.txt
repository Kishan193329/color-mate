cmake_minimum_required(VERSION 3.22.1)
project("colorbynumber")

# The game core shared with the iOS app lives at the repo root, outside the Android project.
set(CMCORE_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../../shared/cmcore")
if(NOT EXISTS "${CMCORE_DIR}/src/reveal_game.cpp")
    message(FATAL_ERROR "shared/cmcore not found at ${CMCORE_DIR} - build from the full repo tree")
endif()

# A short binary dir: listing the sources here directly made object paths mirror the absolute
# source path, which is too long for Windows.
add_subdirectory("${CMCORE_DIR}" cmcore)

add_library(${CMAKE_PROJECT_NAME} SHARED
        colorbynumber.cpp
        reveal_core_jni.cpp)

target_compile_features(${CMAKE_PROJECT_NAME} PRIVATE cxx_std_17)

target_link_libraries(${CMAKE_PROJECT_NAME}
        cmcore
        android
        log)

# ✅ 16KB page size compatibility (important for newer devices)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,-z,max-page-size=16384"
)
