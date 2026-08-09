# Compatibilidade de IA local

Validado em 09/08/2026.

## Modelo atual

- LFM 2.5 1,2B Instruct int4 (`.litertlm`).
- Download HTTPS verificado: 200, 736.015.744 bytes.
- SHA-256 de referência: `A28B5C59AC204E2E51C1F98D2D6DB6982F0E12DA59A268FE498EDCB33237E906`.
- O download confirma tamanho e SHA-256 antes de promover o arquivo para a pasta permanente.

## Política de dispositivos

O aplicativo não escolhe modelo só pelo nome comercial do chip. Ele coleta ABI, memória disponível, espaço disponível e suporte real do backend antes de oferecer um modelo.

| Perfil | Requisito | Motor inicial | Conduta |
|---|---|---|---|
| Compatível | Android 8+, `arm64-v8a`, espaço livre e RAM suficientes | CPU | LFM 1,2B int4 |
| Aceleração GPU | GPU aprovada pela verificação do LiteRT | GPU quando o modelo/backend permitir | Testar e comparar com CPU; voltar para CPU em falha |
| Snapdragon avançado | Delegate Qualcomm/QNN e modelo compatível | NPU/HTP opcional | Não usar como requisito do produto |
| Samsung Exynos, MediaTek, Tensor/Pixel e outros | Detectar backend real em cada aparelho | CPU/GPU quando disponível | CPU é o fallback garantido |
| Insuficiente | pouca RAM, pouco espaço ou arquitetura não suportada | sem modelo grande | comandos determinísticos e orientação para modelo leve |

O motor atual do Rural usa CPU como referência universal. NPU e GPU são otimizações por aparelho; não se deve selecionar um modelo apenas porque o aparelho é Samsung ou Snapdragon.

## Backup e restauração

O backup operacional não inclui o modelo de 736 MB. Ele deve incluir um manifesto de IA: identificador, URL HTTPS, tamanho e SHA-256. Na restauração, o aplicativo verifica se o motor local já possui o mesmo arquivo; se não possuir, baixa novamente em Wi-Fi e valida o SHA-256. Isso evita backups enormes e arquivos duplicados.

Quando o Simplifica IA for o motor central, somente ele guardará o modelo. Rural, 3D e próximos aplicativos guardarão seus dados próprios e uma declaração de dependência do motor, sem cópias do arquivo.
