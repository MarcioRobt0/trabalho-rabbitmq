package br.com.rabbitmq.gerador;

import br.com.rabbitmq.gerador.model.Mensagem;
import br.com.rabbitmq.gerador.model.TipoMensagem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class GeradorApplication {

    // Configurações do RabbitMQ obtidas por variáveis de ambiente ou valores padrão
    private static final String RABBITMQ_HOST = getEnv("RABBITMQ_HOST", "rabbitmq");
    private static final int RABBITMQ_PORT = Integer.parseInt(getEnv("RABBITMQ_PORT", "5672"));
    private static final String RABBITMQ_USER = getEnv("RABBITMQ_USER", "guest");
    private static final String RABBITMQ_PASS = getEnv("RABBITMQ_PASS", "guest");
    private static final String EXCHANGE_NAME = getEnv("EXCHANGE_NAME", "app.topic");

    // Intervalo de envio em milissegundos (180ms ~ 5.55 mensagens/segundo, atendendo ao mínimo de 5 msg/s)
    private static final long MESSAGE_INTERVAL_MS = Long.parseLong(getEnv("MESSAGE_INTERVAL_MS", "180"));

    // Routing keys exigidas pela especificação do trabalho
    private static final String ROUTING_KEY_REVIEW = "review.product";
    private static final String ROUTING_KEY_SUPPORT = "support.request";

    // Frases de exemplo para Avaliações de Produtos (Positivas, Negativas e Neutras)
    private static final String[] FRASES_AVALIACAO = {
            // Positivas
            "O produto superou todas as minhas expectativas, excelente qualidade e entrega super rápida!",
            "Maravilhoso! Funciona perfeitamente e o acabamento é de primeira linha.",
            "Muito satisfeito com a compra, recomendo a todos! Valeu cada centavo.",
            "Produto fantástico, cumpre tudo o que promete com maestria e facilidade de uso.",
            "Adorei! Design muito bonito, bateria durável e desempenho incrível.",
            // Negativas
            "Péssima qualidade, quebrou no primeiro dia de uso. Não recomendo a ninguém.",
            "Muito insatisfeito, o produto veio com defeito e o atendimento pós-venda não responde.",
            "Não gostei. Material muito frágil e acabamento de baixíssima qualidade.",
            "Propaganda enganosa, o produto é horrível e completamente diferente das fotos anunciadas.",
            "Decepcionante, parou de funcionar em menos de uma semana de uso moderado.",
            // Neutras
            "O produto é razoável, atende o básico mas nada além do esperado.",
            "Entrega no prazo. O produto é mediano, condizente com o valor pago na promoção.",
            "Nem bom nem ruim, funciona para tarefas simples do dia a dia sem destaques.",
            "Produto comum, dentro dos padrões normais de mercado sem diferenciais.",
            "Cumpre o que promete de forma simples, mas a embalagem externa veio um pouco amassada."
    };

    // Frases de exemplo para Solicitações de Suporte (Acesso, Desempenho e Conexão)
    private static final String[] FRASES_SUPORTE = {
            // Problema de acesso
            "Não consigo fazer login no portal corporativo, recebo erro de credenciais inválidas.",
            "Minha conta foi bloqueada após tentar recuperar a senha pelo aplicativo móvel.",
            "Não recebo o token de autenticação de dois fatores no meu e-mail cadastrado.",
            "Erro 403 Forbidden ao tentar acessar o painel administrativo da plataforma.",
            "Acesso negado: minha sessão expira imediatamente após efetuar o login no sistema.",
            // Problema de desempenho
            "O sistema está extremamente lento e travando durante o carregamento de relatórios gerenciais.",
            "Alta latência nas consultas ao banco de dados, demora mais de 30 segundos para abrir a página.",
            "A página congela quando tento exportar a listagem de clientes e pedidos em PDF.",
            "Consumo excessivo de memória tornando a navegação no painel web insuportavelmente lenta.",
            "Lentidão crítica ao salvar alterações no formulário de cadastro de fornecedores.",
            // Problema de conexão
            "Falha na conexão de rede: Connection timeout ao tentar sincronizar os dados da filial.",
            "A aplicação perde o sinal com o servidor central frequentemente exibindo conexão recusada.",
            "Erro de timeout ao comunicar com o gateway externo de pagamentos online.",
            "Conexão perdida com o banco de dados principal durante a finalização do checkout.",
            "Network unreachable: os microserviços não conseguem trocar mensagens via rede interna."
    };

    private static final Random RANDOM = new Random();
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(true);

    public static void main(String[] args) {
        System.out.println("==========================================================");
        System.out.println(">>> Iniciando Gerador de Mensagens RabbitMQ <<<");
        System.out.println("Host RabbitMQ: " + RABBITMQ_HOST + ":" + RABBITMQ_PORT);
        System.out.println("Exchange Topic: " + EXCHANGE_NAME);
        System.out.println("Intervalo de envio: " + MESSAGE_INTERVAL_MS + " ms (~" + String.format("%.2f", 1000.0 / MESSAGE_INTERVAL_MS) + " msg/s)");
        System.out.println("==========================================================");

        // Tratamento de interrupção graciosa (SIGINT / SIGTERM / docker stop)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Gerador] Sinal de parada recebido. Encerrando gerador com segurança...");
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
            System.err.println("[Gerador] Não foi possível conectar ao RabbitMQ. Encerrando.");
            System.exit(1);
        }

        try (Channel channel = connection.createChannel()) {
            // Declaração do Topic Exchange como durável (durable: true)
            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, true);
            System.out.println("[Gerador] Topic Exchange '" + EXCHANGE_NAME + "' declarado com sucesso.");
            System.out.println("[Gerador] Iniciando envio contínuo de mensagens...\n");

            long totalEnviadas = 0;
            long inicioTempo = System.currentTimeMillis();

            while (RUNNING.get()) {
                // Alterna aleatoriamente entre AVALIACAO e SUPORTE (50% de chance para cada)
                boolean isAvaliacao = RANDOM.nextBoolean();

                TipoMensagem tipo;
                String routingKey;
                String conteudo;

                if (isAvaliacao) {
                    tipo = TipoMensagem.AVALIACAO;
                    routingKey = ROUTING_KEY_REVIEW;
                    conteudo = FRASES_AVALIACAO[RANDOM.nextInt(FRASES_AVALIACAO.length)];
                } else {
                    tipo = TipoMensagem.SUPORTE;
                    routingKey = ROUTING_KEY_SUPPORT;
                    conteudo = FRASES_SUPORTE[RANDOM.nextInt(FRASES_SUPORTE.length)];
                }

                Mensagem mensagem = new Mensagem(
                        UUID.randomUUID().toString(),
                        Instant.now().toString(),
                        tipo,
                        conteudo
                );

                byte[] payload = OBJECT_MAPPER.writeValueAsBytes(mensagem);

                // Publica a mensagem no Topic Exchange com as propriedades de persistência
                channel.basicPublish(
                        EXCHANGE_NAME,
                        routingKey,
                        MessageProperties.PERSISTENT_TEXT_PLAIN,
                        payload
                );

                totalEnviadas++;
                System.out.printf("[Gerador #%d] [%s] -> Key: %-15s | ID: %s | Conteudo: \"%s\"%n",
                        totalEnviadas, tipo, routingKey, mensagem.getId(),
                        conteudo.length() > 60 ? conteudo.substring(0, 57) + "..." : conteudo);

                // Exibe estatísticas de vazão a cada 50 mensagens
                if (totalEnviadas % 50 == 0) {
                    long decorridoSegundos = Math.max(1, (System.currentTimeMillis() - inicioTempo) / 1000);
                    double taxaAtual = (double) totalEnviadas / decorridoSegundos;
                    System.out.printf("--- [Estatísticas] Total enviadas: %d | Tempo: %ds | Taxa média: %.2f msg/s ---%n",
                            totalEnviadas, decorridoSegundos, taxaAtual);
                }

                try {
                    Thread.sleep(MESSAGE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            System.out.println("[Gerador] Loop de geração finalizado. Total de mensagens geradas: " + totalEnviadas);
        } catch (Exception e) {
            System.err.println("[Gerador] Erro durante a publicação de mensagens: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (connection != null && connection.isOpen()) {
                    connection.close();
                }
            } catch (Exception e) {
                System.err.println("[Gerador] Erro ao fechar conexão com RabbitMQ: " + e.getMessage());
            }
        }
    }

    /**
     * Tenta se conectar ao RabbitMQ com retry exponencial/intervalado para aguardar a
     * inicialização do container do RabbitMQ.
     */
    private static Connection conectarComRetry(ConnectionFactory factory) {
        Connection connection = null;
        int tentativas = 0;
        int maxTentativas = 30;

        while (RUNNING.get() && connection == null && tentativas < maxTentativas) {
            tentativas++;
            try {
                System.out.printf("[Gerador] Tentativa %d/%d de conexão com RabbitMQ em %s:%d...%n",
                        tentativas, maxTentativas, factory.getHost(), factory.getPort());
                connection = factory.newConnection("gerador-service");
                System.out.println("[Gerador] Conectado com sucesso ao RabbitMQ!");
            } catch (Exception e) {
                System.err.printf("[Gerador] RabbitMQ indisponível (%s). Aguardando 3s para próxima tentativa...%n", e.getMessage());
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
