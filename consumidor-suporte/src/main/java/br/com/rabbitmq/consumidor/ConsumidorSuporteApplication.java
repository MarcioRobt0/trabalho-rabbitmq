package br.com.rabbitmq.consumidor;

import br.com.rabbitmq.consumidor.ai.ClassificadorSuporte;
import br.com.rabbitmq.consumidor.model.Mensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ConsumidorSuporteApplication {

    private static final String RABBITMQ_HOST = getEnv("RABBITMQ_HOST", "rabbitmq");
    private static final int RABBITMQ_PORT = Integer.parseInt(getEnv("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USER = getEnv("RABBITMQ_USER", "guest");
    private static final String RABBITMQ_PASS = getEnv("RABBITMQ_PASS", "guest");

    // Configurações de Exchange, Fila e DLQ
    private static final String MAIN_EXCHANGE = getEnv("MAIN_EXCHANGE", "app.topic");
    private static final String ROUTING_KEY = "support.request";
    private static final String QUEUE_NAME = "queue.support";

    private static final String DLX_NAME = getEnv("DLX_NAME", "app.dlx");
    private static final String DLQ_ROUTING_KEY = "support.dlq";
    private static final String DLQ_NAME = "queue.support.dlq";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println(">>> Consumidor 2: Classificação de Solicitações de Suporte <<<");
        System.out.println("Host RabbitMQ: " + RABBITMQ_HOST + ":" + RABBITMQ_PORT);
        System.out.println("Fila: " + QUEUE_NAME + " | Routing Key: " + ROUTING_KEY);
        System.out.println("DLX: " + DLX_NAME + " | DLQ: " + DLQ_NAME);
        System.out.println("==========================================================");

        // Treina o modelo Smile Naive Bayes na memória
        ClassificadorSuporte classificador = new ClassificadorSuporte();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Consumidor 2] Encerrando com segurança...");
            RUNNING.set(false);
        }));

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(RABBITMQ_HOST);
        factory.setPort(RABBITMQ_PORT);
        factory.setUsername(RABBITMQ_USER);
        factory.setPassword(RABBITMQ_PASS);
        factory.setAutomaticRecoveryEnabled(true);

        Connection connection = conectarComRetry(factory);
        if (connection == null) {
            System.err.println("[Consumidor 2] Não foi possível conectar ao RabbitMQ. Encerrando.");
            System.exit(1);
        }

        try {
            Channel channel = connection.createChannel();

            // 1. Declara a Topic Exchange principal
            channel.exchangeDeclare(MAIN_EXCHANGE, BuiltinExchangeType.TOPIC, true);

            // 2. Declara o Dead Letter Exchange (DLX) e a Dead Letter Queue (DLQ)
            channel.exchangeDeclare(DLX_NAME, BuiltinExchangeType.DIRECT, true);
            channel.queueDeclare(DLQ_NAME, true, false, false, null);
            channel.queueBind(DLQ_NAME, DLX_NAME, DLQ_ROUTING_KEY);
            System.out.println("[Consumidor 2] DLX '" + DLX_NAME + "' e DLQ '" + DLQ_NAME + "' configuradas.");

            // 3. Declara a fila principal configurando o encaminhamento para a DLQ em caso de rejeição (NACK)
            Map<String, Object> queueArgs = new HashMap<>();
            queueArgs.put("x-dead-letter-exchange", DLX_NAME);
            queueArgs.put("x-dead-letter-routing-key", DLQ_ROUTING_KEY);

            channel.queueDeclare(QUEUE_NAME, true, false, false, queueArgs);
            channel.queueBind(QUEUE_NAME, MAIN_EXCHANGE, ROUTING_KEY);
            System.out.println("[Consumidor 2] Fila '" + QUEUE_NAME + "' associada à routing key '" + ROUTING_KEY + "'.");

            // Define QoS prefetch = 1 para processar uma mensagem por vez (permitindo observar o crescimento da fila)
            channel.basicQos(1);

            System.out.println("[Consumidor 2] Aguardando mensagens de suporte técnico...\n");

            // Callback de consumo de mensagem com confirmação manual (manual ACK)
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                long deliveryTag = delivery.getEnvelope().getDeliveryTag();
                String body = new String(delivery.getBody(), StandardCharsets.UTF_8);

                try {
                    // Desserialização do payload JSON
                    Mensagem mensagem = OBJECT_MAPPER.readValue(body, Mensagem.class);
                    String conteudo = mensagem.getConteudo();

                    System.out.println("----------------------------------------------------------");
                    System.out.println("[Consumidor 2] Solicitação recebida:");
                    System.out.println("  ID: " + mensagem.getId());
                    System.out.println("  Timestamp: " + mensagem.getTimestamp());
                    System.out.println("  Tipo: " + mensagem.getTipo());
                    System.out.println("  Conteúdo: \"" + conteudo + "\"");

                    // Processamento lento simulado (1000ms)
                    Thread.sleep(1000);

                    // Validação de teste para a Dead Letter Queue (DLQ)
                    if (conteudo != null && conteudo.contains("ERRO_TESTE")) {
                        throw new RuntimeException("Regra de teste acionada: o conteúdo contém 'ERRO_TESTE'!");
                    }

                    // ==============================================================
                    // [IA DE CLASSIFICAÇÃO COM SMILE - NAIVE BAYES]
                    // Categorias: Problema de acesso, Problema de desempenho, Problema de conexão
                    // ==============================================================
                    String resultado = classificador.classificar(conteudo);
                    System.out.println("Mensagem: [" + conteudo + "] - Classificação: [" + resultado + "]");

                    // Confirmação manual de sucesso (ACK)
                    channel.basicAck(deliveryTag, false);
                    System.out.println("  -> [STATUS] ACK manual confirmado com sucesso.");

                } catch (Exception e) {
                    System.err.println("  -> [ERRO] " + e.getMessage());
                    System.err.println("  -> [STATUS] NACK manual (requeue=false). Mensagem encaminhada para a DLQ!");
                    try {
                        // NACK sem requeue: RabbitMQ roteia automaticamente para a DLQ
                        channel.basicNack(deliveryTag, false, false);
                    } catch (IOException ex) {
                        System.err.println("  -> Falha ao enviar NACK: " + ex.getMessage());
                    }
                }
            };

            // autoAck = false -> Exige confirmação manual obrigatória
            channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> {});

            // Mantém a thread principal ativa enquanto a aplicação estiver em execução
            while (RUNNING.get()) {
                Thread.sleep(1000);
            }

        } catch (Exception e) {
            System.err.println("[Consumidor 2] Erro de execução: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
            } catch (Exception e) {
                System.err.println("[Consumidor 2] Erro ao fechar conexão: " + e.getMessage());
            }
        }
    }

    private static Connection conectarComRetry(ConnectionFactory factory) {
        Connection connection = null;
        int tentativas = 0;
        int maxTentativas = 30;

        while (RUNNING.get() && connection == null && tentativas < maxTentativas) {
            tentativas++;
            try {
                System.out.printf("[Consumidor 2] Tentativa %d/%d de conexão com RabbitMQ em %s:%d...%n",
                        tentativas, maxTentativas, factory.getHost(), factory.getPort());
                connection = factory.newConnection("consumidor-suporte");
                System.out.println("[Consumidor 2] Conectado com sucesso ao RabbitMQ!");
            } catch (Exception e) {
                System.err.printf("[Consumidor 2] RabbitMQ indisponível (%s). Aguardando 3s...%n", e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
        }
        return connection;
    }

    private static String getEnv(String name, String defaultValue) {
        String value = System.getenv(name);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
