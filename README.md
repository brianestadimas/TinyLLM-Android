# ChatLLM / TinyLLM on Android


**ChatLLM** is an Android application based on the multimodal LLM inference engine [**mllm**](https://github.com/UbiquitousLearning/mllm). It supports text and image conversations offline.

---

[GitHub Repo LINK](https://github.com/brianestadimas/TinyLLM-Android)
[![Star this Repo](https://img.shields.io/github/stars/brianestadimas/TinyLLM-Android.svg?style=social&label=Star)](https://github.com/brianestadimas/TinyLLM-Android/stargazers)
---

## Preview

Here are some previews of the ChatLLM app in action:

<div style="display: flex; flex-wrap: wrap; gap: 10px;">
    <img src="preview/image1.jpg" alt="Preview 1" width="24%" />
    <img src="preview/image2.jpg" alt="Preview 2" width="24%" />
    <img src="preview/image3.jpg" alt="Preview 3" width="24%" />
    <img src="preview/image4.jpg" alt="Preview 4" width="24%" />
</div>

---

## Download APK
[Download the Release APK](https://drive.google.com/file/d/1YeTo7uHGYNoEKGhmvzDpBiKOpWn4f7mA/view?usp=drive_link)

## Supported Models and Functions  

<table>
  <tr>
    <th>Model</th>
    <th style="text-align:center;">Chat</th>
    <th style="text-align:center;">Image Chat</th>
  </tr>
  <tr>
    <td>PhoneLM 1.5B</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">❌</td>
  </tr>
  <tr>
    <td>Qwen1.5 1.8B</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">❌</td>
  </tr>
  <tr>
    <td>SmolLM 1.7B</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">❌</td>
  </tr>
  <tr>
    <td>OpenELM 1.1B (Removed)</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">❌</td>
  </tr>
  <tr>
    <td>Phi-3-Vision 3.8B</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">✔️</td>
  </tr>
  <tr>
    <td>Phi-3-Vision Finetuned 3.8B</td>
    <td style="text-align:center;">✔️</td>
    <td style="text-align:center;">✔️</td>
  </tr>
</table>

The model can be found in repository [Huggingface](https://huggingface.co/brianestadimas). It will be automatically downloaded when loading the model if it not found in phone download storage.

---

## How to Use on Android

1. Install and open the `ChatLLM.apk`, give permission to manage files.
2. Select the models in the settings menu.
3. Use the **Image Reader** or **Chat** options.
4. Wait for the model to be downloaded before starting conversations.

---

## How to Run on Android Studio

### Step 1: Download the Library
[Download `libmllm_lib.a`](https://drive.google.com/file/d/1YeTo7uHGYNoEKGhmvzDpBiKOpWn4f7mA/view?usp=drive_link).

### Step 2: Place the Library
Put the downloaded `libmllm_lib.a` file into the following directory:
```bash
app/src/main/cpp/libs
```

### Step 3: Build and Run on Android Studio

## How to Build Manually

### Build JNI Lib
Get mllm codes:
```bash
git clone https://github.com/UbiquitousLearning/mllm
cd mllm
```

Build mllm_lib:
```bash
mkdir ../build-arm-app
cd ../build-arm-app

cmake .. \
-DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK/build/cmake/android.toolchain.cmake \
-DCMAKE_BUILD_TYPE=Release \
-DANDROID_ABI="arm64-v8a" \
-DANDROID_NATIVE_API_LEVEL=android-28  \
-DNATIVE_LIBRARY_OUTPUT=. -DNATIVE_INCLUDE_OUTPUT=. $1 $2 $3 \
-DARM=ON \
-DAPK=ON \
-DQNN=ON \
-DDEBUG=OFF \
-DTEST=OFF \
-DQUANT=OFF \
-DQNN_VALIDATE_NODE=ON \
-DMLLM_BUILD_XNNPACK_BACKEND=OFF

make mllm_lib -j$(nproc)
```

---
Copy mllm_lib to ChatBotApp:
```bash
cp ./libmllm_lib.a ChatBotApp/app/src/main/cpp/libs/
```

> **Note**  
> ChatLLM credits the [MLLM Engine](https://github.com/UbiquitousLearning/mllm) and SaltedFish.

