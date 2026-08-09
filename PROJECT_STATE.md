# Simplifica Rural — Estado do projeto

Objetivo: aplicativo Android offline-first para aves, bovinos, suínos, caixa e assistente de IA.

Tecnologia: Kotlin, Jetpack Compose, Material 3, WorkManager e LiteRT/MediaPipe preparado. Fundação modular com domínio, contratos de repositório, dados locais, backup e ações de IA.

Concluído: base Android; camada de IA local; download sob demanda do modelo; confirmação de comandos; escopo por organização, fazenda e granja/unidade; tipos para aves, bovinos, suínos de engorda e matrizes/parideiras; registros locais de compra, venda, despesas, estoque, ovos, leite, pesagem e partos; cálculos de custos, margem, lucro, caixa e indicadores suínos; agregação preparada para Caixa Geral de todas as fazendas da conta.

Bovinos: cadastro local individual por nome/brinco/peso/fase; ordenhas por vaca e médias; ingredientes e misturas por quilograma; análise estimada de matéria seca, proteína bruta, NDT e energia; estimativa configurável de concentrado e silagem. Não é prescrição nutricional e deve usar análise de alimentos e validação de nutricionista animal.

Interface: ainda não criada por decisão do usuário.

Arquitetura: Propriedade/Fazenda/Unidade como escopo obrigatório; módulos de animais, produção, estoque, financeiro, saúde, reprodução e agenda; ações de IA passam por validação e confirmação. Detalhes em `ARCHITECTURE.md`.

Backup: cópia local JSON imediata e periódica, com tentativa a cada hora pelo WorkManager e retenção das três cópias mais recentes, substituindo a mais antiga. A arquitetura de Drive/OneDrive está separada; o upload será ativado quando existirem credenciais OAuth próprias e a conta do usuário for conectada.

Próxima tarefa: criar somente quando autorizado a seleção de Fazenda e Granja, antes das telas operacionais.
