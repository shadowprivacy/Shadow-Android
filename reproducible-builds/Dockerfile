FROM ubuntu:jammy-20230624@sha256:b060fffe8e1561c9c3e6dea6db487b900100fc26830b9ea2ec966c151ab4c020

COPY docker/ docker/
COPY docker/apt.conf docker/sources.list /etc/apt/

RUN apt update -oAcquire::https::Verify-Peer=false
RUN apt install -oAcquire::https::Verify-Peer=false -y ca-certificates

RUN apt update
RUN apt install -y git openjdk-11-jdk openjdk-8-jdk unzip wget

ENV ANDROID_COMMAND_LINE_TOOLS_FILENAME commandlinetools-linux-7583922_latest.zip
ENV ANDROID_API_LEVELS                  android-30
ENV ANDROID_BUILD_TOOLS_VERSION         30.0.2
ENV ANDROID_NDK_VERSION                 21.4.7075529

ENV ANDROID_HOME /usr/local/android-sdk-linux
ENV PATH         ${PATH}:${ANDROID_HOME}/tools:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/cmdline-tools/bin

RUN cd /usr/local/
RUN wget -q "https://dl.google.com/android/repository/${ANDROID_COMMAND_LINE_TOOLS_FILENAME}"
RUN unzip ${ANDROID_COMMAND_LINE_TOOLS_FILENAME} -d /usr/local/android-sdk-linux
RUN rm ${ANDROID_COMMAND_LINE_TOOLS_FILENAME}

RUN yes | sdkmanager --update --sdk_root="${ANDROID_HOME}"
RUN yes | sdkmanager --sdk_root="${ANDROID_HOME}" "platforms;${ANDROID_API_LEVELS}" "build-tools;${ANDROID_BUILD_TOOLS_VERSION}" "extras;google;m2repository" "extras;android;m2repository" "extras;google;google_play_services" "ndk;${ANDROID_NDK_VERSION}"
RUN update-java-alternatives -s java-1.8.0-openjdk-amd64
RUN yes | ${ANDROID_HOME}/tools/bin/sdkmanager --licenses --sdk_root="${ANDROID_HOME}"
RUN update-java-alternatives -s java-1.11.0-openjdk-amd64

RUN rm -rf ${ANDROID_HOME}/tools

RUN docker/gradlewarmer/gradlew --version