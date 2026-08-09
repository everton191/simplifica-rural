# Arquitetura-base — Simplifica Rural

O aplicativo é centrado na propriedade: Organização → Fazenda → Unidade/Setor. Todo registro possui esse escopo, o que permite trabalhar com várias fazendas, granjas, currais, pocilgas, pastos e depósitos sem misturar dados.

## Camadas

- `domain/`: regras, entidades e cálculos sem dependência de tela.
- `data/repository/`: contratos para dados locais hoje e Room/sincronização depois.
- `data/local/`: destino da próxima migração do armazenamento temporário para Room.
- `backup/`: backup local e conexão futura com nuvem.
- `ai/`: interpretação; `ai/actions/` valida e só executa ações confirmadas.
- `ui/`: será criada quando autorizada, consumindo os serviços e repositórios; não contém regras financeiras.

## Módulos do domínio

- `core`: atividades e módulos habilitados pela propriedade.
- `animals`: animais individuais e lotes, extensíveis para outras espécies.
- `production`: produção, ganho de peso, nascimentos, desmame e mortalidade.
- `inventory`: itens e movimentações de estoque.
- `management` e `financial`: compras, vendas, despesas, custos, caixa, margem e lucro.
- `health`, `reproduction` e `agenda`: eventos e pendências programáveis.
- `actions`: comandos estruturados, validados antes de gravar.

## Regra de integridade

Uma compra ou venda é uma única ação de negócio: a implementação final em Room deve gravar estoque e financeiro na mesma transação. A IA nunca escreve em armazenamento local diretamente; ela monta uma `RuralAction`, exibe o resumo e só o executor confirma.

## Próxima evolução técnica

Migrar `SharedPreferences` para Room, implementar os repositórios locais e então construir as telas modulares. Esse caminho preserva a base atual sem bloquear o uso offline.
