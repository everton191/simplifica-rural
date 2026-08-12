# Simplifica Rural

Aplicativo Android offline-first para pequenas propriedades com aves, bovinos e caixa.

Versão atual: **0.1.2** (`versionCode 3`).

## IA local compartilhada

Ao abrir o aplicativo, o Simplifica Rural consulta o provedor da família Simplifica e solicita automaticamente o Gemma 4 E2B. O modelo é baixado uma única vez, validado e compartilhado com Simplifica Tec e Simplifica 3D.

O download só é liberado em aparelho ARM64 com memória próxima de 6 GB ou mais e pelo menos 3,1 GB livres. Se o aparelho não atender aos requisitos, o Rural mostra o motivo e mantém as demais funções disponíveis sem tentar baixar o modelo.

O modelo interpreta mensagens em ações estruturadas. O aplicativo valida, mostra uma prévia e só executa alterações quando o usuário confirma.

Perguntas comuns são respondidas como uma conversa normal. As 12 mensagens mais recentes são enviadas ao modelo e até 12 pares de conversa ficam guardados por propriedade para preservar o contexto após reabrir o aplicativo.

Consulte [Validação 0.1.2](docs/VALIDACAO-0.1.2.md) para os testes executados e os cenários que dependem do ambiente.

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
