# Reproducible Builds


## TL;DR

```bash
# Clone the Shadow-Android source repository
git clone https://github.com/shadowprivacy/Shadow-Android.git && cd Shadow-Android
# Check out the release tag for the version you'd like to compare
git checkout v[the version number]
# Build the Docker image
cd reproducible-builds
docker build -t shadow-android .
# Go back up to the root of the project
cd ..
# Build using the Docker environment
docker run --rm -v $(pwd):/project -w /project shadow-android ./gradlew clean assembleWebsiteProdRelease
# Verify the APKs
python3 apkdiff/apkdiff.py build/outputs/apks/project-release-unsigned.apk path/to/ShadowFromSomewhere.apk
```

***


## Introduction

A reproducible build is achieved by replicating the build environment as a Docker image. You'll need to build the image, run a container instance of it, compile Shadow inside the container and finally compare the resulted APK to the APK that is distributed on our website.

The command line parts in this guide are written for Linux but with some little modifications you can adapt them to macOS (OS X) and Windows. In the following sections we will use `1.15.0` as an example Shadow version. You'll just need to replace all occurrences of `1.15.0` with the version number you are about to verify.

## Setting up directories

First let's create a new directory for this whole reproducible builds project. In your home folder (`~`), create a new directory called `reproducible-shadow`.
```bash
mkdir ~/reproducible-shadow
```

Next create another directory inside `reproducible-shadow` called `apk-from-somewhere`.

```bash
mkdir ~/reproducible-shadow/apk-from-somewhere
```

We will use this directory to share APKs between the host OS and the Docker container.

## Getting the Shadow APK

Simply download the client APK file from the following webpage:

https://shadowprivacy.com/download/

and place it into the `apk-from-somewhere` folder.

## Installing Docker

Install Docker by following the instructions for your platform at https://docs.docker.com/engine/installation/

Your platform might also have its own preferred way of installing Docker. E.g. Ubuntu has its own Docker package (`docker.io`) if you do not want to follow Docker's instructions.

In the following sections we will assume that your Docker installation works without issues. So after installing, please make sure that everything is running smoothly before continuing.


## Building a Docker image for Shadow
First, you need to pull down the source for Shadow-Android, which contains everything you need to build the project, including the `Dockerfile`. The `Dockerfile` contains instructions on how to automatically build a Docker image for Shadow. It's located in the `reproducible-builds` directory of the repository. To get it, go into the `reproducible-shadow` directory and clone the project:

```
git clone https://github.com/shadowprivacy/Shadow-Android.git shadow-source
```

Then, checkout the specific version you're trying to build:

```
git checkout --quiet v1.15.0
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
docker run --rm -v $(pwd):/project -w /project shadow-android ./gradlew clean assembleWebsiteProdRelease
```

### Checking if the APKs match

So now we can compare the APKs using the `apkdiff.py` tool.

The above build step produced several APKs, one for each supported ABI and one universal one. You will need to determine the correct APK to compare.

Currently, the only released ABI is `universal`. In the future it will also include other options, such as `armeabi-v7a`.

Once you have determined the ABI, add an `abi` environment variable. For example, suppose we determine that `armeabi-v7a` is the ABI of interest:

```bash
export abi=armeabi-v7a
```

And run the diff script to compare (updating the filenames for your specific version):

```bash
python3 reproducible-builds/apkdiff/apkdiff.py \
        app/build/outputs/apk/websiteProd/release/*website-prod-$abi-release-unsigned*.apk \
        ../apk-from-somewhere/Shadow-website-universal-release-1.15.0.apk
```
Output:
```
APKs match!
```

If you get `APKs match!`, you have successfully verified that the release obtained from elsewhere matches with your own self-built version of Shadow.

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