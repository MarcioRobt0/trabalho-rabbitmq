#!/usr/bin/env bash

# ==============================================================================
# Script de Demonstração para Teste da Dead Letter Queue (DLQ)
# Envia mensagens contendo a palavra-chave 'ERRO_TESTE' para acionar o NACK manual
# e demonstrar o roteamento para as filas DLQ no RabbitMQ.
# ==============================================================================

set -e

RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"
RABBITMQ_PORT="${RABBITMQ_PORT:-15672}"
USER="${RABBITMQ_USER:-guest}"
PASS="${RABBITMQ_PASS:-guest}"
EXCHANGE="app.topic"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}==============================================================================${NC}"
echo -e "${BLUE}>>> Disparo de Mensagem de Teste para Dead Letter Queue (DLQ) <<<${NC}"
echo -e "${BLUE}==============================================================================${NC}"

# 1. Enviar mensagem com erro para Consumidor 1 (Avaliações)
echo -e "\n${YELLOW}[1/2] Publicando mensagem com 'ERRO_TESTE' na routing key 'review.product'...${NC}"
PAYLOAD_REVIEW='{"id":"test-dlq-review-'$(date +%s)'","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","tipo":"AVALIACAO","conteudo":"Produto excelente, porem contem ERRO_TESTE para simular falha"}'

curl -s -u "$USER:$PASS" -H "content-type:application/json" \
  -X POST "http://$RABBITMQ_HOST:$RABBITMQ_PORT/api/exchanges/%2F/$EXCHANGE/publish" \
  -d "{\"properties\":{},\"routing_key\":\"review.product\",\"payload\":$(echo "$PAYLOAD_REVIEW" | jq -R .),\"payload_encoding\":\"string\"}" > /dev/null

echo -e "${GREEN}✓ Mensagem enviada para 'review.product'!${NC}"

# 2. Enviar mensagem com erro para Consumidor 2 (Suporte)
echo -e "\n${YELLOW}[2/2] Publicando mensagem com 'ERRO_TESTE' na routing key 'support.request'...${NC}"
PAYLOAD_SUPPORT='{"id":"test-dlq-support-'$(date +%s)'","timestamp":"'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'","tipo":"SUPORTE","conteudo":"Erro de conexao com banco de dados contendo ERRO_TESTE proposital"}'

curl -s -u "$USER:$PASS" -H "content-type:application/json" \
  -X POST "http://$RABBITMQ_HOST:$RABBITMQ_PORT/api/exchanges/%2F/$EXCHANGE/publish" \
  -d "{\"properties\":{},\"routing_key\":\"support.request\",\"payload\":$(echo "$PAYLOAD_SUPPORT" | jq -R .),\"payload_encoding\":\"string\"}" > /dev/null

echo -e "${GREEN}✓ Mensagem enviada para 'support.request'!${NC}"

echo -e "\n${BLUE}==============================================================================${NC}"
echo -e "${GREEN}Mensagens de erro enviadas com sucesso!${NC}"
echo -e "Acompanhe os logs dos consumidores para ver o NACK e o roteamento para a DLQ:"
echo -e "  ${YELLOW}docker logs -f consumidor-sentimento${NC}"
echo -e "  ${YELLOW}docker logs -f consumidor-suporte${NC}"
echo -e "E confira o número de mensagens nas filas DLQ no painel: http://localhost:15672/#/queues"
echo -e "${BLUE}==============================================================================${NC}\n"
