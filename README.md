# Advanced Swap

Aplicativo Android em Kotlin e Jetpack Compose para monitorar swap e administrar, somente quando o aparelho comprovar os requisitos, um arquivo de swap privado do app.

## Limites e compatibilidade

- Android não oferece uma API pública para criar, ativar ou remover swap. O sistema normalmente configura ZRAM e deixa o kernel e os serviços do Android decidirem quando páginas serão comprimidas ou gravadas.
- Monitoramento lê `/proc/meminfo` e `/proc/swaps` quando o fabricante permite. Os dados podem estar indisponíveis ou incompletos.
- Alterações exigem Shizuku autorizado e iniciado como root (UID 0), além de suporte observado do kernel, ferramentas `mkswap`/`swapon`/`swapoff`, utilitários de arquivo, `/data` identificado como ext4 e espaço livre suficiente. Qualquer capacidade desconhecida bloqueia a operação.
- Shizuku iniciado pelo ADB usa normalmente UID `shell` (2000): ele permite algumas leituras, mas não equivale a root e não pode ativar swap do kernel.
- Só é gerenciado o arquivo fixo `/data/local/tmp/advanced-swap/swap.img`. O app não edita swap de fabricante nem instala scripts de inicialização. O arquivo não é configurado para persistir após reiniciar.
- Swap ativa é global para o kernel. Não é possível escolher Roblox ou outro app para mover para swap, nem forçar uso em primeiro ou segundo plano. O Android controla isso e as políticas variam por versão, fabricante e kernel.
- Swap em armazenamento flash pode aumentar latência, gravações e desgaste. Cada alteração exige confirmação.

Não há uma matriz confiável de compatibilidade somente por versão Android ou marca: kernels e ROMs do mesmo fabricante variam. A detecção é feita no aparelho, e resultados desconhecidos mantêm o monitoramento, mas bloqueiam alterações.

O app usa `minSdk 23` (Android 6) por compatibilidade com a API do Shizuku. Em aparelhos sem root, Shizuku precisa ser iniciado por ADB; Android 11 ou posterior oferece Depuração sem fio no próprio aparelho, enquanto versões anteriores normalmente precisam de um computador. Mesmo no Android mais recente, a API pública não permite que um app comum administre swap. Root também não remove as restrições específicas do kernel, do filesystem ou do SELinux do fabricante.

Referências:

- [Android: gerenciamento de memória e ZRAM](https://developer.android.com/topic/performance/memory-management)
- [Android: diagnóstico de swap do sistema](https://developer.android.com/topic/performance/memory/guide/system-wide-memory)
- [AOSP: reclaim por processo](https://developer.android.com/topic/performance/memory/guide/reclaim)
- [Shizuku API: diferença entre ADB/shell e root](https://github.com/RikkaApps/Shizuku-API)

## Compilar e instalar

Requisitos: Android Studio com JDK 17, Android SDK Platform 35 e acesso aos repositórios Google Maven e Maven Central.

1. Abra esta pasta no Android Studio e aguarde a sincronização do Gradle.
2. Compile com `./gradlew :app:assembleDebug` (ou use **Build > Make Project**).
3. Instale `app/build/outputs/apk/debug/app-debug.apk` no dispositivo.
4. Monitoramento funciona sem privilégios. Para avaliar alterações, inicie Shizuku como root e autorize o app. O modo ADB/shell mostra o motivo do bloqueio.

Os testes unitários estão em `app/src/test`; execute `./gradlew :app:testDebugUnitTest`.

As operações usam um caminho fixo, validam parâmetros novamente imediatamente antes da execução e registram localmente os resultados recentes. Nenhum comando ou diagnóstico é enviado à rede.
