# Reproducible Builds


## TL;DR

```bash
# Clone the Signal Android source repository
git clone https://github.com/shadowprivacy/Shadow-Android.git && cd Shadow-Android
# Check out the release tag for the version you'd like to compare
git checkout v[the version number]
# Build the Docker image
cd reproducible-builds
docker build -t shadow-android .
# Go back up to the root of the project
cd ..
# Build using the Docker environment
docker run --rm -v $(pwd):/project -w /project shadow-android ./gradlew clean assembleRelease
# Verify the APKs
python3 apkdiff/apkdiff.py build/outputs/apks/project-release-unsigned.apk path/to/ShadowFromPlay.apk
```

***


## Introduction

A reproducible build is achieved by replicating the build environment as a Docker image. You'll need to build the image, run a container instance of it, compile Shadow inside the container and finally compare the resulted APK to the APK that is distributed in the Google Play Store or on our website.

The command line parts in this guide are written for Linux but with some little modifications you can adapt them to macOS (OS X) and Windows. In the following sections we will use `3.15.2` as an example Shadow version. You'll just need to replace all occurrences of `3.15.2` with the version number you are about to verify.

## Setting up directories

First let's create a new directory for this whole reproducible builds project. In your home folder (`~`), create a new directory called `reproducible-shadow`.
```bash
mkdir ~/reproducible-shadow
```

Next create another directory inside `reproducible-shadow` called `apk-from-google-play-store`.

```bash
mkdir ~/reproducible-shadow/apk-from-google-play-store
```

We will use this directory to share APKs between the host OS and the Docker container.


## Getting the Google Play Store version of Shadow APK

To compare the APKs we of course need a version of Shadow from the Google Play Store.

First make sure that the Shadow version you want to verify is installed on your Android device. You'll need `adb` for this part.

Plug your device to your computer and run this command to pull the APK from the device:

```bash
adb pull $(adb shell pm path su.sres.securesms | grep /base.apk | awk -F':' '{print $2}') ~/reproducible-shadow/apk-from-google-play-store/Shadow-$(adb shell dumpsys package su.sres.securesms | grep versionName | awk -F'=' '{print $2}').apk
```

This will pull a file into `~/reproducible-shadow/apk-from-google-play-store/` with the name `Shadow-<version>.apk`

Alternatively, you can do this step-by-step:

```bash
adb shell pm path su.sres.securesms
```

This will output something like:

```bash
package:/data/app/su.sres.securesms-aWRzcGlzcG9wZA==/base.apk
```

The output will tell you where the Shadow APK is located in your device. (In this example the path is `/data/app/su.sres.securesms-aWRzcGlzcG9wZA==/base.apk`)

Now using this information, pull the APK from your device to the `reproducible-shadow/apk-from-google-play-store` directory you created before:
```bash
adb pull \
  /data/app/su.sres.securesms-aWRzcGlzcG9wZA==/base.apk \
  ~/reproducible-shadow/apk-from-google-play-store/Shadow-3.15.2.apk
```

We will use this APK in the final part when we compare it with the self-built APK from GitHub.

## Identifying the ABI

The APKs are being split by ABI, the CPU architecture of the target device. Google Play will serve the correct one to you for your device.

To identify which ABIs the google play APK supports, we can look inside the APK, which is just a zip file:

```bash
unzip -l ~/reproducible-shadow/apk-from-google-play-store/Shadow-*.apk | grep lib/
```

Example:

```
  1214348  00-00-1980 00:00   lib/armeabi-v7a/libconscrypt_jni.so
   151980  00-00-1980 00:00   lib/armeabi-v7a/libcurve25519.so
  4164320  00-00-1980 00:00   lib/armeabi-v7a/libjingle_peerconnection_so.so
    13948  00-00-1980 00:00   lib/armeabi-v7a/libnative-utils.so
  2357812  00-00-1980 00:00   lib/armeabi-v7a/libsqlcipher.so
```

As there is just one sub directory of `lib/` called `armeabi-v7a`, that is your ABI. Make a note of that for later. If you see more than one subdirectory of `lib/`:

```
  1214348  00-00-1980 00:00   lib/armeabi-v7a/libconscrypt_jni.so
   151980  00-00-1980 00:00   lib/armeabi-v7a/libcurve25519.so
  4164320  00-00-1980 00:00   lib/armeabi-v7a/libjingle_peerconnection_so.so
    13948  00-00-1980 00:00   lib/armeabi-v7a/libnative-utils.so
  2357812  00-00-1980 00:00   lib/armeabi-v7a/libsqlcipher.so
  2111376  00-00-1980 00:00   lib/x86/libconscrypt_jni.so
   201056  00-00-1980 00:00   lib/x86/libcurve25519.so
  7303888  00-00-1980 00:00   lib/x86/libjingle_peerconnection_so.so
     5596  00-00-1980 00:00   lib/x86/libnative-utils.so
  3977636  00-00-1980 00:00   lib/x86/libsqlcipher.so
```

Then that means you have the `universal` APK.

## Installing Docker

Install Docker by following the instructions for your platform at https://docs.docker.com/engine/installation/

Your platform might also have its own preferred way of installing Docker. E.g. Ubuntu has its own Docker package (`docker.io`) if you do not want to follow Docker's instructions.

In the following sections we will assume that your Docker installation works without issues. So after installing, please make sure that everything is running smoothly before continuing.


## Building a Docker image for Shadow
First, you need to pull down the source for Shadow-Android, which contains everything you need to build the project, including the `Dockerfile`. The `Dockerfile` contains instructions on how to automatically build a Docker image for Shadow. It's located in the `reproducible-builds` directory of the repository. To get it, clone the project:

```
git clone https://github.com/shadowprivacy/Shadow-Android.git shadow-source
```

Then, checkout the specific version you're trying to build:

```
git checkout --quiet v5.0.0
```

Then, to build it, go into the `reproducible-builds` directory:
```
cd ~/reproducible-shadow/shadow-source/reproducible-builds
```

...and run the docker build command:

```
docker build -t shadow-android .
```

(Note that there is a dot at the end of that command!)

Wait a few years for the build to finish... :construction_worker:

(Depending on your computer and network connection, this may take several minutes.)

:calendar: :sleeping:

After the build has finished, you may wish to list all your Docker images to see that it's really there:

```
docker images
```

Output should look something like this:

```
REPOSITORY          TAG                 IMAGE ID            CREATED             VIRTUAL SIZE
shadow-android      latest              c6b84450b896        46 seconds ago      2.94 GB
```


## Compiling Shadow inside a container

Next we compile Shadow.

First go to the directory where the source code is: `reproducible-shadow/shadow-source`:

```
cd ~/reproducible-shadow/shadow-source
```

To build with the docker image you just built (`shadow-android`), run:

```
docker run --rm -v $(pwd):/project -w /project shadow-android ./gradlew clean assemblePlayRelease
```

This will take a few minutes :sleeping:


### Checking if the APKs match

So now we can compare the APKs using the `apkdiff.py` tool.

The above build step produced several APKs, one for each supported ABI and one universal one. You will need to determine the correct APK to compare.

Currently, the most common ABI is `armeabi-v7a`. Other options at this time include `x86` and `universal`. In the future it will also include 64-bit options, such as `x86_64` and `arm64-v8a`.

See [Identifying the ABI](#identifying-the-abi) above if you don't know the ABI of your play store APK.

Once you have determined the ABI, add an `abi` environment variable. For example, suppose we determine that `armeabi-v7a` is the ABI google play has served:

```bash
export abi=armeabi-v7a
```

And run the diff script to compare (updating the filenames for your specific version):

```bash
python3 reproducible-builds/apkdiff/apkdiff.py \
        build/outputs/apk/play/release/*play-$abi-release-unsigned*.apk \
        ../apk-from-google-play-store/Shadow-5.0.0.apk
```
Output:
```
APKs match!
```

If you get `APKs match!`, you have successfully verified that the Google Play release matches with your own self-built version of Shadow.

If you get `APKs don't match!`, you did something wrong in the previous steps. See the [Troubleshooting section](#troubleshooting) for more info.


## Comparing next time

If the build environment (i.e. `Dockerfile`) has not changed, you don't need to build the image again to verify a newer APK. You can just [run the container again](#compiling-shadow-inside-a-container).


## Troubleshooting

Some common issues why things may not work:
- the Android packages in the Docker image are outdated and compiling Shadow fails
- you built the Docker image with a wrong version of the `Dockerfile`
- you didn't checkout the correct Shadow version tag with Git before compiling
- the ABI you selected is not the correct ABI, particularly if you see an error along the lines of `Sorted manifests don't match, lib/x86/libcurve25519.so vs lib/armeabi-v7a/libcurve25519.so`.
- this guide is outdated
- if you run into this issue: https://issuetracker.google.com/issues/110237303 try to add `resources.arsc` to the list of ignored files and compare again