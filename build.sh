#!/data/data/com.agycli/files/usr/bin/bash
set -e

echo "🏝️  Building Dynamic Island APK..."

PROJECT_DIR="$HOME/DynamicIsland"
SRC_DIR="$PROJECT_DIR/app/src/main"
BUILD_DIR="$PROJECT_DIR/build"
GEN_DIR="$BUILD_DIR/gen"
OBJ_DIR="$BUILD_DIR/obj"
BIN_DIR="$BUILD_DIR/bin"
RES_DIR="$BUILD_DIR/res"
ANDROID_JAR="$PREFIX/share/java/android.jar"
FRAMEWORK="/system/framework/framework-res.apk"
JAVA_HOME="$PREFIX/lib/jvm/java-17-openjdk"
R8_JAR="$HOME/r8.jar"

export PATH="$JAVA_HOME/bin:$PATH"
export LD_LIBRARY_PATH="$PREFIX/lib:$LD_LIBRARY_PATH"

if [ ! -f "$R8_JAR" ]; then
    echo "⬇️  Downloading R8/D8 compiler tool..."
    curl -sL "https://maven.google.com/com/android/tools/r8/8.2.42/r8-8.2.42.jar" -o "$R8_JAR"
fi

rm -rf "$BUILD_DIR"
mkdir -p "$GEN_DIR" "$OBJ_DIR" "$BIN_DIR" "$RES_DIR"

echo "📦 Step 1: Compiling resources with aapt2..."
find "$SRC_DIR/res" -type f \( -name "*.xml" -o -name "*.png" -o -name "*.jpg" \) | while read resfile; do
    aapt2 compile -o "$RES_DIR/" "$resfile" 2>&1
done

echo "📦 Step 2: Linking resources..."
FLAT_FILES=$(find "$RES_DIR" -name "*.flat" -type f | tr '\n' ' ')
aapt2 link \
    --auto-add-overlay \
    -I "$FRAMEWORK" \
    --manifest "$SRC_DIR/AndroidManifest.xml" \
    --java "$GEN_DIR" \
    -o "$BIN_DIR/resources.apk" \
    --min-sdk-version 26 \
    --target-sdk-version 33 \
    --version-code 1 \
    --version-name "1.0" \
    $FLAT_FILES

echo "☕ Step 3: Compiling Java..."
find "$SRC_DIR/java" "$GEN_DIR" -name "*.java" > "$BUILD_DIR/sources.txt"
echo "   $(wc -l < "$BUILD_DIR/sources.txt") Java files"

javac \
    -source 1.8 -target 1.8 \
    -classpath "$ANDROID_JAR" \
    -d "$OBJ_DIR" \
    @"$BUILD_DIR/sources.txt"

echo "📱 Step 4: Creating DEX with D8..."
CLASS_FILES=$(find "$OBJ_DIR" -name "*.class" | tr '\n' ' ')
java -cp "$R8_JAR" com.android.tools.r8.D8 \
    --lib "$FRAMEWORK" \
    --lib "$ANDROID_JAR" \
    --min-api 26 \
    --output "$BIN_DIR" \
    $CLASS_FILES

echo "📦 Step 5: Packaging APK..."
cp "$BIN_DIR/resources.apk" "$BIN_DIR/dynamic-island-unsigned.apk"
cd "$BIN_DIR"
jar uf dynamic-island-unsigned.apk classes.dex

echo "🔐 Step 6: Signing APK..."
KEYSTORE="$PROJECT_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v \
        -keystore "$KEYSTORE" \
        -alias dynamicisland \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass android -keypass android \
        -dname "CN=DynamicIsland,OU=Dev,O=Dev,L=X,ST=X,C=US"
fi

apksigner sign \
    --ks "$KEYSTORE" \
    --ks-key-alias dynamicisland \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "$BIN_DIR/DynamicIsland.apk" \
    "$BIN_DIR/dynamic-island-unsigned.apk"

cp "$BIN_DIR/DynamicIsland.apk" "$HOME/DynamicIsland.apk"
SIZE=$(ls -lh "$HOME/DynamicIsland.apk" | awk '{print $5}')

echo ""
echo "═══════════════════════════════════════"
echo "  ✅ BUILD SUCCESSFUL!"
echo "  📦 APK Size: $SIZE"
echo "  📍 Location: $HOME/DynamicIsland.apk"
echo "═══════════════════════════════════════"
