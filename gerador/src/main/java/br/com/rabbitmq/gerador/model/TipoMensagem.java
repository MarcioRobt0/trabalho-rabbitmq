package br.com.rabbitmq.gerador.model;

/**
 * Enum que representa os dois tipos de mensagem exigidos pelo trabalho:
 * - AVALIACAO: Comentários de produtos (positivo, negativo, neutro)
 * - SUPORTE: Solicitações de suporte técnico (acesso, desempenho, conexão)
 */
public enum TipoMensagem {
    AVALIACAO,
    SUPORTE
}
