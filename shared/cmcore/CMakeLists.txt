# The shared core as a static library, plus its tests.
#
#   Linux / macOS / CI:  cmake -S shared/cmcore -B build/cmcore && cmake --build build/cmcore
#                        && ctest --test-dir build/cmcore --output-on-failure
#   Android:             app/src/main/cpp/CMakeLists.txt pulls this in with add_subdirectory()
#   iOS:                 add src/*.cpp + include/ to the Xcode target and link libz
cmake_minimum_required(VERSION 3.16)
project(cmcore CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(cmcore STATIC src/json.cpp src/reveal_game.cpp src/vector_art.cpp)
target_include_directories(cmcore PUBLIC include)
# Linked into a shared library on Android, so it must be position independent.
set_target_properties(cmcore PROPERTIES POSITION_INDEPENDENT_CODE ON)
if(CMAKE_CXX_COMPILER_ID MATCHES "GNU|Clang")
    target_compile_options(cmcore PRIVATE -Wall -Wextra)
endif()

if(ANDROID)
    target_link_libraries(cmcore PUBLIC z)  # the NDK's system zlib
else()
    find_package(ZLIB REQUIRED)
    target_link_libraries(cmcore PUBLIC ZLIB::ZLIB)

    enable_testing()
    add_executable(core_test tests/core_test.cpp)
    target_link_libraries(core_test PRIVATE cmcore)
    add_test(NAME core_test COMMAND core_test)
endif()
