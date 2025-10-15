#!/bin/sh
set -e

# Če niso nastavljene, uporabi /tmp (Render Free nima /data diska)
: "${PIGEON_AUDIO_FILE_PATH:=/tmp/audio/}"
: "${SPRING_DATASOURCE_URL:=jdbc:sqlite:/tmp/pigeon-pod.db}"

export PIGEON_AUDIO_FILE_PATH
export SPRING_DATASOURCE_URL

# Ustvari mape
mkdir -p "$PIGEON_AUDIO_FILE_PATH"

echo "✅ PigeonPod Render Free entrypoint:"
echo "  SPRING_DATASOURCE_URL=$SPRING_DATASOURCE_URL"
echo "  PIGEON_AUDIO_FILE_PATH=$PIGEON_AUDIO_FILE_PATH"

# Predaj izvajanje originalnemu ukazu (CMD iz image-a)
exec "$@"
