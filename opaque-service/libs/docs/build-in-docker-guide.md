docker run --rm -it \
  -v $(pwd):/build \
  ubuntu:22.04 bash


-------------------- Install JDK

Inside the container:

apt update
apt install -y build-essential openjdk-17-jdk


------------------- install GIT

apt install -y git

git --version

--------------------- copy files to docker
docker cp <local_path> <container_id>:<destination_path>

docker cp . ae5f07ab1a79ad091e4c635cff549a08713566758e063a99bdf86f8b12777d47:/usr/projects/opaque-service

--------------------- Install make

apt install -y make

apt install -y build-essential

-------------------- Install Libsodium
apt install -y wget

wget https://download.libsodium.org/libsodium/releases/libsodium-1.0.19-stable.tar.gz

tar xvf libsodium-1.0.19-stable.tar.gz
cd libsodium-stable

./configure
make
make install
ldconfig

Then verify:

ls /usr/local/lib | grep libsodium
ls /usr/local/include | grep sodium.h
pkg-config --modversion libsodium


After this, you can rebuild liboprf:

export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:$PKG_CONFIG_PATH
export LD_LIBRARY_PATH=/usr/local/lib:$LD_LIBRARY_PATH

-------------------- Install pkgconf

1. Install pkgconf (and optionally pkg-config)
apt-get update
apt-get install -y pkgconf


Or, if you prefer pkg-config (most systems have it):

apt-get install -y pkg-config


Check that it works:

pkgconf --version
# or
pkg-config --version

2. Ensure libsodium is visible to pkgconf

If you installed libsodium from source, make sure PKG_CONFIG_PATH includes its .pc file:

export PKG_CONFIG_PATH=/usr/local/lib/pkgconfig:$PKG_CONFIG_PATH


Check that pkgconf sees it:

pkgconf --modversion libsodium
# or
pkg-config --modversion libsodium


It should print 1.0.19.


------------------- Verify pkg-config can find libsodium
pkg-config --cflags --libs libsodium


Expected output example:

-I/usr/local/include -L/usr/local/lib -lsodium 

------------------- build liboprt
------------------- build libopaque

------------------- Copy from docker to local

docker cp <container_id>:/path/in/container/file ./localfile

docker cp ae5f07ab1a79ad091e4c635cff549a08713566758e063a99bdf86f8b12777d47:/usr/projects/opaque-service/libs/libopaque-1.0.1/java/libopaque.so /Users/ariellepasana/git/algomeet-backend/opaque-service/src/main/resources/native



