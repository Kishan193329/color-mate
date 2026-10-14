cmake_minimum_required(VERSION 3.22.1)
project("colorbynumber")

add_library(${CMAKE_PROJECT_NAME} SHARED
        colorbynumber.cpp)

target_link_libraries(${CMAKE_PROJECT_NAME}
        android
        log)

# ✅ 16KB page size compatibility (important for newer devices)
target_link_options(${CMAKE_PROJECT_NAME} PRIVATE
        "-Wl,-z,max-page-size=16384"
)
