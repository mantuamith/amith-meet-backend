FROM eclipse-temurin:17-jdk

# Install dependencies
RUN apt-get update && \
    apt-get install -y \
    build-essential \
        wget \
        curl \
        ca-certificates \
        pkg-config \
        && rm -rf /var/lib/apt/lists/*
# Set environment variable for libsodium version
ENV LIBSODIUM_VERSION=1.0.19

# Download, build, and install libsodium
RUN wget https://download.libsodium.org/libsodium/releases/libsodium-${LIBSODIUM_VERSION}-stable.tar.gz && \
    tar xvf libsodium-${LIBSODIUM_VERSION}-stable.tar.gz && \
    cd libsodium-stable && \
    ./configure && \
    make && \
    make install && \
    cd .. && \
    rm -rf libsodium-stable* && \
    ldconfig
		
# Set working directory
WORKDIR /app

# Copy your Spring Boot jar
COPY target/*.jar app.jar

# Ensure LD_LIBRARY_PATH includes libsodium
ENV LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu

# Run the app
ENTRYPOINT ["java", "-jar", "app.jar", "--spring.profiles.active=prod"]