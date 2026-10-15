package br.com.rabbitmq.consumidor.ai;

import smile.classification.DiscreteNaiveBayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classificador de Suporte utilizando Naive Bayes da biblioteca Smile (com.github.haifengl:smile-core).
 * Classifica textos de suporte em:
 * - 'Problema de acesso'
 * - 'Problema de desempenho'
 * - 'Problema de conexão'
 */
public class ClassificadorSuporte {

    public static final String ACESSO = "Problema de acesso";
    public static final String DESEMPENHO = "Problema de desempenho";
    public static final String CONEXAO = "Problema de conexão";

    private static final String[] CLASSES = {ACESSO, DESEMPENHO, CONEXAO};

    private final Map<String, Integer> vocabulario = new HashMap<>();
    private final DiscreteNaiveBayes modelo;

    public ClassificadorSuporte() {
        System.out.println("[IA - Suporte] Treinando modelo Naive Bayes (Smile) na memória...");

        // Amostras hardcoded de treino para cada categoria de suporte técnico
        List<AmostraTreino> amostras = new ArrayList<>();

        // Classe 0: Problema de acesso
        amostras.add(new AmostraTreino("nao consigo fazer login no portal corporativo credenciais invalidas", 0));
        amostras.add(new AmostraTreino("minha conta foi bloqueada apos tentar recuperar senha aplicativo", 0));
        amostras.add(new AmostraTreino("nao recebo token de autenticacao de dois fatores no email", 0));
        amostras.add(new AmostraTreino("erro 403 forbidden ao tentar acessar painel administrativo", 0));
        amostras.add(new AmostraTreino("acesso negado minha sessao expira imediatamente efetuar login", 0));
        amostras.add(new AmostraTreino("login senha usuario autenticacao acesso permissao bloqueado entrar", 0));

        // Classe 1: Problema de desempenho
        amostras.add(new AmostraTreino("o sistema esta extremamente lento e travando durante relatorios", 1));
        amostras.add(new AmostraTreino("alta latencia nas consultas banco de dados demora 30 segundos abrir", 1));
        amostras.add(new AmostraTreino("a pagina congela quando tento exportar listagem clientes pdf", 1));
        amostras.add(new AmostraTreino("consumo excessivo de memoria torna navegacao insuportavelmente lenta", 1));
        amostras.add(new AmostraTreino("lentidao critica ao salvar alteracoes no formulario de cadastro", 1));
        amostras.add(new AmostraTreino("lento travando latencia demora congelamento freeze performance congelando", 1));

        // Classe 2: Problema de conexão
        amostras.add(new AmostraTreino("falha na conexao de rede connection timeout tentar sincronizar", 2));
        amostras.add(new AmostraTreino("a aplicacao perde sinal servidor frequentemente conexao recusada", 2));
        amostras.add(new AmostraTreino("erro de timeout ao comunicar com api gateway externo pagamentos", 2));
        amostras.add(new AmostraTreino("conexao perdida com banco de dados durante finalizacao checkout", 2));
        amostras.add(new AmostraTreino("network unreachable os servicos nao conseguem trocar rede", 2));
        amostras.add(new AmostraTreino("conexao rede timeout sinal internet desconectado socket unreachable", 2));

        // 1. Constrói o vocabulário (mapeamento palavra -> índice do vetor)
        for (AmostraTreino amostra : amostras) {
            for (String palavra : tokenizar(amostra.texto)) {
                vocabulario.putIfAbsent(palavra, vocabulario.size());
            }
        }

        int p = vocabulario.size(); // Número de termos no vocabulário (features)
        int k = 3;                  // 3 categorias de suporte

        // 2. Cria o modelo Multinomial Naive Bayes do Smile
        this.modelo = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, k, p);

        // 3. Treina o modelo com as amostras vetorizadas
        for (AmostraTreino amostra : amostras) {
            int[] vetor = vetorizar(amostra.texto);
            modelo.update(vetor, amostra.classe);
        }

        System.out.printf("[IA - Suporte] Modelo treinado com sucesso! (%d amostras, vocabulário de %d termos)%n",
                amostras.size(), p);
    }

    /**
     * Classifica uma solicitação de suporte em:
     * 'Problema de acesso', 'Problema de desempenho' ou 'Problema de conexão'.
     */
    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return ACESSO;
        }
        int[] vetor = vetorizar(texto);
        int classePredita = modelo.predict(vetor);
        return CLASSES[classePredita];
    }

    private int[] vetorizar(String texto) {
        int[] vetor = new int[vocabulario.size()];
        for (String palavra : tokenizar(texto)) {
            Integer idx = vocabulario.get(palavra);
            if (idx != null) {
                vetor[idx]++;
            }
        }
        return vetor;
    }

    private String[] tokenizar(String texto) {
        return texto.toLowerCase()
                .replaceAll("[^a-z0-9áéíóúâêîôûãõç]", " ")
                .trim()
                .split("\\s+");
    }

    private static class AmostraTreino {
        String texto;
        int classe;

        AmostraTreino(String texto, int classe) {
            this.texto = texto;
            this.classe = classe;
        }
    }
}
