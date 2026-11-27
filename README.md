# Simplifica Rural

Aplicativo Android offline-first para pequenas propriedades com aves, bovinos e caixa.

[Baixar Simplifica Rural 0.1.3 com LFM 3](https://github.com/everton191/simplifica-rural/releases/latest)

## LFM 3 no Android

Após cadastrar a propriedade, escolha **Instalar LFM 3**. O modelo tem cerca de 1,1 GB. Acompanhe o progresso na tela Início e na notificação do Android, se autorizada. Os lançamentos simples continuam disponíveis durante o download.

O APK com LFM 3 usa um pacote Android próprio. Os dados de instalações anteriores ficam no aplicativo anterior. O código da versão com LFM 3 está na branch [teste/lfm3](https://github.com/everton191/simplifica-rural/tree/teste/lfm3).

## Abrir

Abra esta pasta no Android Studio, sincronize o Gradle e execute em aparelho físico Android. A inferência local não é validada em emuladores.
## Backup

Ao abrir o aplicativo, um backup local é criado e um agendamento periódico é mantido para novas cópias aproximadamente a cada hora. Os arquivos são instantâneos JSON versionados, guardados no armazenamento privado do aplicativo e com retenção das três cópias mais recentes; a próxima substitui a mais antiga.

Google Drive e OneDrive são suportados pela arquitetura, mas o envio à nuvem só será ativado após cadastrar as credenciais OAuth do aplicativo e o usuário conectar a própria conta. Isso evita usar login ou senha do ChatGPT/Google/Microsoft diretamente no app.

## Gestão rural estruturada

A base local registra compras, vendas, receitas avulsas, despesas classificadas, consumo e ajustes de estoque, produção de ovos e leite, pesagens de suínos e partos de matrizes. Os cálculos geram receita, custo operacional, mão de obra familiar, depreciação, custo financeiro, geração de caixa, lucro, margem e saldo por unidade, fazenda ou caixa geral. Também estão previstos os indicadores de ganho médio diário, leitões desmamados por parto e mortalidade pré-desmame.

## Bovinos e alimentação

O módulo local de bovinos permite cadastrar cada vaca por nome, brinco, peso e estágio de lactação; registrar ordenhas; acompanhar o total diário e a média individual. A mistura de ração aceita ingredientes em quilogramas — por exemplo, soja, farelo de algodão e farelo de trigo — e calcula matéria seca, proteína bruta, NDT e energia estimada. A recomendação de concentrado e silagem é somente uma estimativa configurável e deve ser revisada com análise dos alimentos e orientação de nutricionista animal.

Os perfis de nutrição também são separados para aves de postura, suínos em crescimento, terminação e matrizes, caprinos, ovinos, equinos e piscicultura. Cada perfil avalia a mistura com as próprias faixas e explica os ajustes possíveis. Para aves, suínos, equinos e peixes, o aplicativo alerta obrigatoriamente para núcleo, premix ou fórmula da espécie/fase, pois a proteína média não garante vitaminas, minerais e aminoácidos adequados.
