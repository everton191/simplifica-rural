# Rotas e pendências funcionais

Atualizado em 08/08/2026, por leitura das rotas e telas efetivamente ligadas no APK.

## Rotas com tela funcional específica

- Início, Animais, Aves, registro de ovos e lotes de aves.
- Bovinos, lista/cadastro/detalhe de animal, registro de leite e mistura de ração.
- Suínos, engorda, lotes, matrizes e detalhe de matriz.
- Estoque, detalhe de estoque, Compras e formulário de compra.
- Financeiro, nova entrada, nova despesa e Vendas.
- Agenda, Saúde, Produção, Configurações, Atividades, Assistente Rural e Teste integrado.

## Fluxos com persistência ou execução confirmada

- Produção de ovos e leite; compras de estoque; consumo de estoque; venda; despesa; caixa e contas a receber parcialmente.
- Assistente: proposta antes da gravação, confirmação explícita e conversa pendente para obter preço de compra ou vencimento de venda.
- Teste integrado: recebe um cenário predefinido de seis lançamentos, exibe proposta em popup, permite editar o texto e só então aplicar compras, produção, consumo, venda e despesa.

## Rotas existentes, mas ainda demonstrativas ou incompletas

- Lotes de aves e suínos, detalhe de bovino e matriz: apresentam estrutura visual, porém não têm CRUD e histórico completos.
- Agenda, Saúde e Produção: possuem telas, mas sem agenda persistida, notificações, registros clínicos ou produção consolidada por unidade.
- Financeiro: lançamento e caixa existem, mas contas a pagar/receber, baixa de recebível, conciliação e relatórios não estão completos na interface.
- Estoque: saldo é atualizado pelos fluxos confirmados, mas inventário, ajustes, saída manual e histórico detalhado ainda precisam de telas próprias.
- Relatórios, Indicadores, Histórico geral, Backup e a maior parte das preferências apontam para tela genérica de funcionalidade, não para o módulo final.
- Atalhos rápidos do assistente para peso, uso de ração e nova venda ainda seguem para telas genéricas.

## Limite atual da IA

O LFM foi validado para carregar e responder localmente. A interpretação de um pedido grande de várias operações é implementada hoje no fluxo de **Teste integrado**, com seis operações conhecidas e conferência antes de salvar. O chat comum ainda processa uma pendência por vez; ele não transforma qualquer frase livre de seis operações em uma fila genérica. Isso evita que um modelo grave lançamentos ambíguos, mas o planejador genérico por múltiplas ações ainda é a próxima implementação necessária.

## Ordem recomendada de conclusão

1. Converter os módulos genéricos de estoque, financeiro, agenda e saúde em formulários com persistência e histórico.
2. Implementar o planejador de múltiplas ações do chat com a mesma tela de revisão do Teste integrado.
3. Ligar notificações de contas a receber, cio, retorno veterinário e estoque baixo.
4. Criar relatórios consolidados por fazenda, granja e caixa geral.
