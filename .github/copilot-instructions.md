# PeerBanHelper Development Instructions

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

PeerBanHelper is a Java-based BitTorrent client protection application with a Vue.js web interface. It automatically bans unwelcome, leeching, and abnormal BT clients with support for custom rules and GeoIP-based blocking.

## Prerequisites and Setup

Install required dependencies in this exact order:

1. **Install Java 21 using SDKMAN**:
   ```bash
   curl -s "https://get.sdkman.io" | bash
   source "/home/runner/.sdkman/bin/sdkman-init.sh"
   sdk install java 21.0.4-tem
   ```
   - Java 21 is REQUIRED. The application is compiled on Java 21 and designed to run on Java 23.
   - Do NOT use Java 17 or other versions - the build will fail or have runtime issues.

2. **Install pnpm globally**:
   ```bash
   npm install -g pnpm
   ```
   - pnpm >=9.0.0 is required for the WebUI build (specified in package.json engines).
   - Node.js >=20.0.0 is required.

## Building the Application

ALWAYS source the SDKMAN environment before any build commands:
```bash
source "/home/runner/.sdkman/bin/sdkman-init.sh"
```

### Complete Build Process (Recommended)

Use the provided build script for the complete build:
```bash
./build.sh
```
- **NEVER CANCEL**: Build takes 45-60 seconds. NEVER CANCEL. Set timeout to 120+ seconds.
- This script handles both WebUI and Maven builds automatically.
- Creates a complete executable JAR with all dependencies.

### Manual Build Steps (if needed)

If you need to build components separately:

1. **WebUI Build** (must be done first):
   ```bash
   cd webui
   pnpm install  # Takes ~5 seconds
   pnpm run build  # Takes ~35 seconds. NEVER CANCEL. Set timeout to 120+ seconds.
   cd ..
   cp -r webui/dist src/main/resources/static
   ```

2. **Maven Build** (after WebUI is built):
   ```bash
   mvn -B clean package --file pom.xml
   ```
   - **NEVER CANCEL**: Build takes 2-3 minutes. NEVER CANCEL. Set timeout to 300+ seconds.
   - Downloads many dependencies on first run.
   - Creates `target/PeerBanHelper.jar` and `target/libraries/` directory.

## Testing and Validation

### No Unit Tests Available
- `mvn test` reports "No tests to run" - this project does not have unit tests.
- `pnpm run test` is not available in the WebUI.

### Linting
Run WebUI linting to validate code quality:
```bash
cd webui
pnpm run lint  # Takes ~10 seconds
```
- ALWAYS run linting before committing changes.
- Fix any errors reported by prettier and eslint.

### Manual Validation Scenarios

ALWAYS validate the application manually after making changes:

1. **Start the Application**:
   ```bash
   source "/home/runner/.sdkman/bin/sdkman-init.sh"
   java -Dpbh.nogui=true -jar target/PeerBanHelper.jar
   ```
   - Application starts on port 9898 by default.
   - Takes ~6-10 seconds to start up completely.
   - Downloads GeoIP databases on first run (this is normal).

2. **Test Web Interface**:
   ```bash
   curl -I http://localhost:9898/
   # Should return HTTP/1.1 200 OK
   ```

3. **Test API Endpoint**:
   ```bash
   curl -s http://localhost:9898/api/general/status
   # Should return JSON with initialization message for fresh installs
   ```

4. **Complete User Scenario**:
   - Navigate to `http://localhost:9898` in a browser.
   - Complete the OOBE (Out of Box Experience) initialization wizard.
   - Add a BitTorrent client configuration.
   - Verify the dashboard loads and shows statistics.

## Docker Build and Testing

### Docker Build
```bash
docker build -f Dockerfile . --tag peerbanhelper-test
```
- **NEVER CANCEL**: Build takes 3-4 minutes. NEVER CANCEL. Set timeout to 600+ seconds.
- Uses multi-stage build: WebUI build, Maven build, then runtime container.

### Docker Testing
```bash
docker run --rm -p 9899:9898 peerbanhelper-test
# Test with: curl -I http://localhost:9899/
```

## Key Project Structure

### Backend (Java/Maven)
- **Main class**: `com.ghostchu.peerbanhelper.MainJumpLoader`
- **Source**: `src/main/java/` (444 Java source files)
- **Resources**: `src/main/resources/` (contains config, translations, static files)
- **Build output**: `target/PeerBanHelper.jar` + `target/libraries/`
- **No test source directory** - this project has no unit tests

### Frontend (Vue.js/TypeScript)
- **Source**: `webui/src/`
- **Package manager**: pnpm (required, npm is not supported)
- **Build tool**: Vite
- **Output**: `webui/dist/` → copied to `src/main/resources/static/`

### Configuration Files
- **Maven**: `pom.xml` (Java 21, 444 source files, extensive dependencies)
- **WebUI**: `webui/package.json` (Vue 3, TypeScript, ArcoDesign UI library)
- **Docker**: `Dockerfile` (multi-stage with Node.js and Maven)
- **Build script**: `build.sh` (automates WebUI + Maven build)

## Common Issues and Solutions

### Build Failures
- **Missing Java 21**: Ensure you've sourced SDKMAN and installed Java 21.0.4-tem
- **pnpm not found**: Install pnpm globally with `npm install -g pnpm`
- **WebUI build before Maven**: Always build WebUI first, Maven depends on the static files

### Runtime Issues
- **Port conflicts**: Default port 9898, use `-Dpbh.port=8080` to change
- **GeoIP download failures**: Normal on first run, requires internet connection
- **Initialization required**: Fresh installs need OOBE setup via WebUI

## Development Workflow

1. **Make Code Changes**: Edit Java/TypeScript source files
2. **Build**: Run `./build.sh` to build everything
3. **Lint**: Run `cd webui && pnpm run lint` for frontend changes
4. **Test**: Start application and validate functionality manually
5. **Validate**: Test both direct JAR and Docker container deployment

## Command Reference

### Essential Commands (with timeouts)
```bash
# Complete build (timeout: 120s)
./build.sh

# WebUI only (timeout: 120s)
cd webui && pnpm install && pnpm run build

# Maven only (timeout: 300s)
source "/home/runner/.sdkman/bin/sdkman-init.sh" && mvn -B clean package --file pom.xml

# Docker build (timeout: 600s)
docker build -f Dockerfile . --tag peerbanhelper-test

# Start application for testing
source "/home/runner/.sdkman/bin/sdkman-init.sh" && java -Dpbh.nogui=true -jar target/PeerBanHelper.jar
```

### Validation Commands
```bash
# Lint WebUI
cd webui && pnpm run lint

# Test web interface
curl -I http://localhost:9898/

# Test API
curl -s http://localhost:9898/api/general/status
```

## Critical Reminders

- **ALWAYS** source SDKMAN before Java/Maven commands: `source "/home/runner/.sdkman/bin/sdkman-init.sh"`
- **NEVER CANCEL** long-running builds - they are expected to take several minutes
- **ALWAYS** build WebUI before Maven when building manually
- **ALWAYS** run linting before committing frontend changes
- **ALWAYS** manually validate application functionality after changes
- **Set appropriate timeouts** (120s+ for builds, 300s+ for Maven, 600s+ for Docker)