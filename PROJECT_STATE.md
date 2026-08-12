# Simplifica Rural — Estado do projeto

Objetivo: aplicativo Android offline-first para aves, bovinos, suínos, caixa e assistente de IA.

Tecnologia: Kotlin, Jetpack Compose, Material 3, WorkManager e LiteRT/MediaPipe preparado. Fundação modular com domínio, contratos de repositório, dados locais, backup e ações de IA.

Concluído: base Android; camada de IA local; download sob demanda do modelo; confirmação de comandos; escopo por organização, fazenda e granja/unidade; tipos para aves, bovinos, suínos de engorda e matrizes/parideiras; registros locais de compra, venda, despesas, estoque, ovos, leite, pesagem e partos; cálculos de custos, margem, lucro, caixa e indicadores suínos; agregação preparada para Caixa Geral de todas as fazendas da conta.

Bovinos: cadastro local individual por nome/brinco/peso/fase; ordenhas por vaca e médias; ingredientes e misturas por quilograma; análise estimada de matéria seca, proteína bruta, NDT e energia; estimativa configurável de concentrado e silagem. Não é prescrição nutricional e deve usar análise de alimentos e validação de nutricionista animal.

Interface: Home compacta, módulos de aves, bovinos, suínos, estoque, financeiro, agenda, saúde, produção e secretária. Histórico geral, backup local e sobre possuem telas próprias; os formulários mantêm data e hora automáticas para novos registros.

Arquitetura: Propriedade/Fazenda/Unidade como escopo obrigatório; módulos de animais, produção, estoque, financeiro, saúde, reprodução e agenda; ações de IA passam por validação e confirmação. Detalhes em `ARCHITECTURE.md`.

Backup: cópia local JSON imediata e periódica, com tentativa a cada hora pelo WorkManager e retenção das três cópias mais recentes, substituindo a mais antiga. A arquitetura de Drive/OneDrive está separada; o upload será ativado quando existirem credenciais OAuth próprias e a conta do usuário for conectada.

Fluxos da secretária: vendas de ovos sem preço pedem o preço antes de qualquer baixa no estoque; compra de ração por sacos pede o peso de cada saco e depois o preço. Todo lançamento operacional continua dependente de confirmação explícita.

Validação 0.1.2: build, testes unitários, instalação, conversa geral, envio pelo teclado e memória recente foram aprovados no aparelho ASUS_I005DA. Gesto de voz com várias pausas e fluxos que dependem de animais, estoque ou rede específicos continuam na lista de testes manuais por ambiente.
