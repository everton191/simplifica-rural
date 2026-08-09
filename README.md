# Simplifica Rural

Aplicativo Android offline-first para pequenas propriedades com aves, bovinos e caixa.

## IA local

Ao abrir o Assistente Rural, o aplicativo agenda o download automático por Wi-Fi do modelo Gemma 3 1B INT4 no formato LiteRT (`.task`). O download é de aproximadamente 556 MB e permanece no armazenamento privado do aplicativo.

O modelo somente interpreta mensagens em ações estruturadas. O aplicativo valida, mostra uma prévia e só executará alterações quando o usuário confirmar.

O modelo escolhido é o Gemma 3 1B LiteRT porque possui artefato Android pronto para o runtime MediaPipe. A interface `LocalAiEngine` permite acrescentar LFM2.5/llama.cpp depois sem alterar ações ou regras de negócio.

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
