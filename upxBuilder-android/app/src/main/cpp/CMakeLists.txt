cmake_minimum_required(VERSION 3.22.1)
project(upxanalyzer CXX)

add_library(upxanalyzer SHARED analyzer.cpp)

target_compile_features(upxanalyzer PRIVATE cxx_std_17)
target_compile_options(upxanalyzer PRIVATE -Wall -Wextra -O2)
