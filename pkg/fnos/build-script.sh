#!/bin/bash

# Define variables
ROOT_DIR=$(pwd)
BUILD_DIR="${ROOT_DIR}/pkg/fnos/build"
OUTPUT_DIR="${ROOT_DIR}/pkg/fnos/target"
PACKAGE_NAME="PeerBanHelper"

# Clean up
rm -rf "${BUILD_DIR}"
rm -rf "${OUTPUT_DIR}"
mkdir -p "${BUILD_DIR}"
mkdir -p "${OUTPUT_DIR}"

# Copy files
cp -r pkg/fnos/PeerBanHelper/* "${BUILD_DIR}/"

# Replace placeholders
sed -i "s#@IMAGE@#${IMAGE}#g" "${BUILD_DIR}/app/docker/docker-compose.yaml"
sed -i "s#@PBH_VERSION@#${PBH_VERSION}#g" "${BUILD_DIR}/manifest"

# Create package
cd "${BUILD_DIR}"
zip -r "${OUTPUT_DIR}/${PACKAGE_NAME}_${PBH_VERSION}_x86_64.zip" .
