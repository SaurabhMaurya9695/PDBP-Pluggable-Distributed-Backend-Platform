#!/bin/bash
# Build all plugins and copy JARs into work/

set -e

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
PLUGINS_DIR="$ROOT_DIR/plugins"
WORK_DIR="$ROOT_DIR/work"

mkdir -p "$WORK_DIR"

echo "🔨 Building plugins..."
echo "----------------------"

PLUGIN_DIRS=$(find "$PLUGINS_DIR" -name pom.xml -type f | sed 's|/pom.xml||' | sort)

if [ -z "$PLUGIN_DIRS" ]; then
  echo "❌ No plugins found"
  exit 1
fi

PLUGIN_COUNT=$(echo "$PLUGIN_DIRS" | wc -l | tr -d ' ')
echo "📦 Found $PLUGIN_COUNT plugin(s)"

BUILT=0
FAILED=0
FAILED_PLUGINS=""

extract_artifact_id() {
  sed '/<parent>/,/<\/parent>/d' pom.xml \
    | grep "<artifactId>" \
    | head -1 \
    | sed 's/.*<artifactId>\([^<]*\)<\/artifactId>.*/\1/' \
    | xargs
}

for dir in $PLUGIN_DIRS; do
  plugin=$(basename "$dir")
  echo ""
  echo "➡️  Building $plugin"

  cd "$dir"

  if mvn clean package -DskipTests -q; then
    jar=$(find target -name "*.jar" \
      ! -name "*-sources.jar" \
      ! -name "*-javadoc.jar" \
      | head -1)

    if [ -z "$jar" ]; then
      echo "⚠️  No JAR produced"
      FAILED=$((FAILED + 1))
      FAILED_PLUGINS="$FAILED_PLUGINS\n - $plugin (no jar)"
    else
      artifact_id=$(extract_artifact_id)
      [ -z "$artifact_id" ] && artifact_id="$plugin"

      cp "$jar" "$WORK_DIR/$artifact_id.jar"
      echo "✅ Success → work/$artifact_id.jar"
      BUILT=$((BUILT + 1))
    fi
  else
    echo "❌ Build failed"
    FAILED=$((FAILED + 1))
    FAILED_PLUGINS="$FAILED_PLUGINS\n - $plugin"
  fi

  cd "$ROOT_DIR"
done

echo ""
echo "======================"
echo "📊 Build Summary"
echo "======================"
echo "✅ Built : $BUILT"
echo "❌ Failed: $FAILED"

if [ $FAILED -gt 0 ]; then
  echo ""
  echo "Failed plugins:"
  echo -e "$FAILED_PLUGINS"
  exit 1
fi

echo ""
echo "📦 Generated JARs:"
ls -lh "$WORK_DIR"/*.jar

echo ""
echo "🎉 All plugins built successfully!"
