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

# Download and install fnpack
echo "Downloading fnpack..."
curl -s -L -o /usr/local/bin/fnpack https://static2.fnnas.com/fnpack/fnpack-1.2.0-linux-amd64
chmod +x /usr/local/bin/fnpack

# Create package
echo "Building package..."
cd "${BUILD_DIR}"
fnpack build

# Move artifact
mv *.fpk "${OUTPUT_DIR}/"
