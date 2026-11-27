# Rural LFM3 para teste

A variante Android usa o pacote br.com.simplificarural.lfm3qa e mantém os dados separados do aplicativo principal. O modelo é baixado pelo app a partir do Hugging Face e verificado por SHA-256.

Para compilar, extraia o pacote oficial Android ARM64 do [llama.cpp b12798](https://github.com/ggml-org/llama.cpp/releases/tag/b12798). Copie os arquivos abaixo para `app/src/main/jniLibs/arm64-v8a/`.

- `llama-server` com o nome `liblfm3_server.so`
- `libggml-base.so`, `libggml.so`, `libggml-cpu-android_armv8.0_1.so` e `libggml-cpu-android_armv8.2_1.so`
- `libllama-common.so`, `libllama-server-impl.so`, `libllama.so` e `libmtmd.so`

Execute `./gradlew testDebugUnitTest assembleDebug` no Linux ou `gradlew.bat testDebugUnitTest assembleDebug` no Windows com o SDK Android instalado.
