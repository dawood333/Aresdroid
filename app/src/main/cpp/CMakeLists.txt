cmake_minimum_required(VERSION 3.22.1)
project(aresdroid_native LANGUAGES CXX)
add_library(aresdroid SHARED native-lib.cpp)
target_compile_features(aresdroid PRIVATE cxx_std_17)
target_compile_options(aresdroid PRIVATE -Wall -Wextra -Werror)

# Standalone root-only executable copied to /data/local/tmp for an isolated
# mount-namespace pivot_root smoke test. It never touches the host namespace.
add_executable(aresdroid-pivot-lab pivot-lab.cpp)
target_compile_features(aresdroid-pivot-lab PRIVATE cxx_std_17)
target_compile_options(aresdroid-pivot-lab PRIVATE -Wall -Wextra -Werror)
