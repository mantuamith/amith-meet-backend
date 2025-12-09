Library github repo: https://github.com/stef/libopaque/

Prequesites:

 These bindings depend on the following:
	- java-jdk-dev (jdk17)
	- libsodium (libsodium-1.0.19-stable.tar.gz)
	- liboprf (build using build doc guide under /docs/liboprt)

Build Steps:

  1. Run these commands in termal:

	 export LIBOPRF_DIR=/usr/projects/opaque-service/libs/liboprf-0.9.2
	 export LIBOPAQUE_DIR=/usr/projects/opaque-service/libs/libopaque-1.0.1
	 export JAVA_HOME=/usr/lib/jvm/java-1.17.0-openjdk-arm64
	 
  2. Manually copy Java classes from "$LIBOPAQUE_DIR/java/ctrlc" to project JNI package "com/algomeet/opaqueservice/jni" 
     
  3. Manually update "$LIBOPAQUE_DIR/java/jni.c", change Java class packages names to "com/algomeet/opaqueservice/jni" and "com/algomeet/opaqueservice/jni/dto" 
          
  4. Compile libopaque sources:
     
	 cd $LIBOPAQUE_DIR/java
	 
	 rm -f *.o
	 
	 gcc -I/usr/include \
	     -I$LIBOPAQUE_DIR/src \
	     -I$LIBOPAQUE_DIR \
	     -I$LIBOPRF_DIR/src \
	     -I$LIBOPRF_DIR/src/noise_xk/include \
	     -I$LIBOPRF_DIR/src/noise_xk/include/karmel \
	     -I$LIBOPRF_DIR/src/noise_xk/include/karmel/minimal \
	     -I$JAVA_HOME/include \
	     -I$JAVA_HOME/include/linux \
	     -fPIC \
	     -c jni.c ../src/common.c ../src/opaque.c

   5. Link everything into the shared library:

      cd $LIBOPAQUE_DIR/java
      
      cc -shared -o libopaque.so \
  		 jni.o common.o opaque.o \
         $LIBOPRF_DIR/src/liboprf_merged.o \
         $LIBOPRF_DIR/src/noise_xk/src/*.o \
         -L/usr/lib/aarch64-linux-gnu -lsodium
    
    6. Copy libopaque.so to "src/main/resources/native"


	