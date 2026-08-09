# Auditoria operacional — Simplifica Rural

Atualizada em 08/08/2026.

## Validação de IA — 08/08/2026

- Qwen 3 0,6B: não é adequado como secretária para uma pendência composta. O roteador determinístico absorve os comandos já reconhecidos e o prompt do modelo limita a saída a poucas ações simples; ele não pode produzir um plano de compras, estoque, venda, caixa e agenda em uma única proposta segura.
- LFM 2.5 1,2B Instruct int4: arquivo de aproximadamente 736 MB instalado ao lado do Qwen, sem apagar o modelo leve. No teste real de 08/08 ele carregou no aparelho, respondeu ao motor local e foi interpretado pela ponte JSON com sucesso (48,5 s no primeiro teste a frio).
- Decisão atual: LFM 2.5 1,2B é o modelo padrão de download. Cálculos, validações de estoque/caixa e gravações continuam determinísticos; o modelo é usado apenas para linguagem, contexto e perguntas de esclarecimento.

## Funciona no APK atual

- Navegação principal: Início, Animais, Estoque, Financeiro e Mais.
- Seleção de contexto por organização, fazenda e unidade na camada de domínio.
- Assistente Rural local: download automático por Wi-Fi, LFM 2.5 1,2B como padrão, carregamento real no aparelho e proposta de registro com confirmação obrigatória.
- Normalização de linguagem informal para ovos, leite, ração e despesa. Há testes para erros de escrita, números por extenso e marcadores regionais.
- Ovos: conversão de bandejas para ovos (30 unidades por bandeja), soma de ovos avulsos e persistência do lançamento confirmado.
- Granja em escala: calcula lotes × bandejas × 30, soma ovos avulsos, desconta quebra/descarte e salva o saldo líquido; o rascunho mostra bruto, descarte e líquido.
- Leite: calcula tambores de 50 L por padrão (ou a capacidade dita no comando) e soma litros avulsos.
- Home: total de ovos e dúzias lidos dos eventos confirmados no escopo atual, sem valor fixo para aves.
- Bovinos: cadastro de animal e cálculo de mistura de ração com aviso de estimativa nutricional.
- Backups locais agendados com retenção limitada; provedor de nuvem é extensível.
- Interface: ícones específicos por conteúdo e cards compactados.

## Estrutura pronta, mas ainda sem operação completa

- Aves: lotes, alimentação, saúde, ocorrências, histórico detalhado e relatórios.
- Bovinos: produção diária de leite, reprodução, saúde, histórico e relatórios.
- Suínos: lotes, peso, alimentação, mortalidade, matrizes, reprodução e relatórios.
- Estoque: cadastro, entrada, saída, inventário e cálculo de saldo por item.
- Financeiro: lançamentos de entrada e despesa, compras, vendas, contas a pagar/receber e resultado consolidado.
- Agenda, saúde, produção, relatórios, indicadores, configurações e backup em nuvem possuem navegação, mas algumas telas ainda são genéricas ou exibem dados demonstrativos.
- Captura por microfone usa o reconhecedor de fala do Android por pressionar e soltar; ainda falta a validação manual de permissões e qualidade de transcrição em campo.
- Rotas ainda genéricas: alimentação/saúde/histórico/relatórios de aves; reprodução/saúde/histórico/relatórios de bovinos; peso/alimentação/mortalidade/reprodução/histórico/relatórios de suínos; cadastro/entrada/saída/inventário/histórico detalhado de estoque; contas a pagar/receber, relatórios e histórico financeiro; agenda, saúde, produção, indicadores, configurações e backup em nuvem.

## Regras de segurança aplicadas

- A IA não grava dados sozinha: sempre devolve um rascunho e exige confirmação explícita.
- Valores inferidos de bandejas mostram a conta antes da confirmação.
- Cálculos de nutrição são estimativas e devem ser revisados com análise dos alimentos e profissional responsável.

## Próxima sequência para conclusão funcional

1. Substituir dados demonstrativos de estoque e financeiro por registros persistidos.
2. Conectar cada atalho genérico a formulários específicos e histórico por módulo.
3. Concluir agenda/saúde/reprodução com filtros por fazenda e unidade.
4. Adicionar microfone com transcrição e teste em aparelho real.
5. Adicionar escolha de modelo: leve (Qwen) e qualidade (LFM 1,2B), com verificação de espaço e memória antes do download.
