#!/bin/bash

# List of all Spring Boot services
ALL_SERVICES=("auth-service" "chat-service" "contact-service" "meeting-service" "user-service")

# GitHub organization for GHCR
GITHUB_ORG="algoframe-private-limited"

# Defaults
PUSH_IMAGES=false
BUILD_IMAGES=true
SELECTED_SERVICES=("${ALL_SERVICES[@]}")

# Help text
show_help() {
  echo ""
  echo "Usage: $0 [options]"
  echo ""
  echo "Options:"
  echo "  --push               Push Docker images to GitHub Container Registry"
  echo "  --build-only         Only build images (default if neither --push-only nor --build-only is specified)"
  echo "  --push-only          Only push existing images (skip build)"
  echo "  --service <name>     Target a specific service"
  echo "  --all                Target all services (default)"
  echo "  --help               Show this help message"
  echo ""
  echo "Examples:"
  echo "  ./build.sh                       # Build all services locally"
  echo "  ./build.sh --push                # Build and push all services"
  echo "  ./build.sh --push-only           # Push previously built images (no rebuild)"
  echo "  ./build.sh --service chat-service  # Build chat-service only"
  echo "  ./build.sh --push --service chat-service  # Build and push chat-service"
  echo ""
}

# Argument parsing
while [[ $# -gt 0 ]]; do
  case $1 in
    --push)
      PUSH_IMAGES=true
      shift
      ;;
    --build-only)
      BUILD_IMAGES=true
      PUSH_IMAGES=false
      shift
      ;;
    --push-only)
      BUILD_IMAGES=false
      PUSH_IMAGES=true
      shift
      ;;
    --service)
      if [[ -n $2 ]]; then
        SELECTED_SERVICES=("$2")
        shift 2
      else
        echo "Warning: Missing service name after --service"
        exit 1
      fi
      ;;
    --all)
      SELECTED_SERVICES=("${ALL_SERVICES[@]}")
      shift
      ;;
    --help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      show_help
      exit 1
      ;;
  esac
done

# Main logic
for SERVICE in "${SELECTED_SERVICES[@]}"; do
  echo "----------------------------"
  echo " Processing: $SERVICE"
  echo "----------------------------"

  # Check service directory exists
  if [[ ! -d "$SERVICE" ]]; then
    echo "ERROR: Service directory '$SERVICE' not found. Skipping."
    continue
  fi

  cd "$SERVICE" || { echo "ERROR: Failed to enter $SERVICE"; exit 1; }

  if [ "$BUILD_IMAGES" = true ]; then
    echo "Building JAR..."
    mvn clean package -DskipTests || { echo "ERROR: Maven build failed for $SERVICE"; exit 1; }

    LOCAL_IMAGE="$SERVICE:latest"
    echo "Building Docker image: $LOCAL_IMAGE"
    docker build -t "$LOCAL_IMAGE" .
  fi

  if [ "$PUSH_IMAGES" = true ]; then
    REMOTE_IMAGE="ghcr.io/$GITHUB_ORG/$SERVICE:latest"
    LOCAL_IMAGE="$SERVICE:latest"

    echo "Tagging and pushing: $REMOTE_IMAGE"
    docker tag "$LOCAL_IMAGE" "$REMOTE_IMAGE"
    docker push "$REMOTE_IMAGE" || { echo "ERROR: Failed to push $SERVICE"; exit 1; }
  fi

  cd ..
done

echo ""
echo "Completed: ${BUILD_IMAGES:+Build }${PUSH_IMAGES:+Push }for selected service(s)."
