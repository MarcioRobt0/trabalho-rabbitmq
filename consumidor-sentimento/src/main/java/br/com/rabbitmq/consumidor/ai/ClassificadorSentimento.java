package br.com.rabbitmq.consumidor.ai;

import smile.classification.DiscreteNaiveBayes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Classificador de Sentimento utilizando Naive Bayes da biblioteca Smile (com.github.haifengl:smile-core).
 * Classifica textos de avaliações em: 'Positivo', 'Negativo' ou 'Neutro'.
 */
public class ClassificadorSentimento {

    public static final String POSITIVO = "Positivo";
    public static final String NEGATIVO = "Negativo";
    public static final String NEUTRO = "Neutro";

    private static final String[] CLASSES = {POSITIVO, NEGATIVO, NEUTRO};

    private final Map<String, Integer> vocabulario = new HashMap<>();
    private final DiscreteNaiveBayes modelo;

    public ClassificadorSentimento() {
        System.out.println("[IA - Sentimento] Treinando modelo Naive Bayes (Smile) na memória...");

        // Amostras hardcoded de treino para cada uma das classes de sentimento
        List<AmostraTreino> amostras = new ArrayList<>();

        // Classe 0: Positivo
        amostras.add(new AmostraTreino("o produto superou todas as minhas expectativas excelente qualidade e entrega rapida", 0));
        amostras.add(new AmostraTreino("maravilhoso funciona perfeitamente acabamento de primeira linha recomendo", 0));
        amostras.add(new AmostraTreino("muito satisfeito com a compra recomendo a todos valeu cada centavo", 0));
        amostras.add(new AmostraTreino("produto fantastico cumpre tudo o que promete com maestria facilidade", 0));
        amostras.add(new AmostraTreino("adorei design muito bonito bateria duravel desempenho incrivel", 0));
        amostras.add(new AmostraTreino("otimo excelente bom perfeito aprovado maravilhoso satisfeito", 0));

        // Classe 1: Negativo
        amostras.add(new AmostraTreino("pessima qualidade quebrou no primeiro dia de uso nao recomendo", 1));
        amostras.add(new AmostraTreino("muito insatisfeito produto veio com defeito e nao funciona", 1));
        amostras.add(new AmostraTreino("nao gostei material fragil acabamento muito ruim", 1));
        amostras.add(new AmostraTreino("propaganda enganosa produto horrivel completamente diferente das fotos", 1));
        amostras.add(new AmostraTreino("decepcionante parou de funcionar em menos de uma semana estragou", 1));
        amostras.add(new AmostraTreino("ruim pessimo defeito quebrado insatisfeito horrivel lixo", 1));

        // Classe 2: Neutro
        amostras.add(new AmostraTreino("o produto e razoavel atende o basico mas nada alem do esperado", 2));
        amostras.add(new AmostraTreino("entrega no prazo produto mediano condizente com o valor pago", 2));
        amostras.add(new AmostraTreino("nem bom nem ruim funciona para tarefas simples do dia a dia", 2));
        amostras.add(new AmostraTreino("produto comum dentro dos padroes normais sem nenhum destaque", 2));
        amostras.add(new AmostraTreino("cumpre o que promete de forma simples embalagem veio normal", 2));
        amostras.add(new AmostraTreino("razoavel mediano comum simples basico normal dentro do padrao", 2));

        // 1. Constrói o vocabulário (mapeamento palavra -> índice do vetor)
        for (AmostraTreino amostra : amostras) {
            for (String palavra : tokenizar(amostra.texto)) {
                vocabulario.putIfAbsent(palavra, vocabulario.size());
            }
        }

        int p = vocabulario.size(); // Número de termos no vocabulário (features)
        int k = 3;                  // 3 classes (Positivo, Negativo, Neutro)

        // 2. Cria o modelo Multinomial Naive Bayes do Smile
        this.modelo = new DiscreteNaiveBayes(DiscreteNaiveBayes.Model.MULTINOMIAL, k, p);

        // 3. Treina o modelo com as amostras vetorizadas
        for (AmostraTreino amostra : amostras) {
            int[] vetor = vetorizar(amostra.texto);
            modelo.update(vetor, amostra.classe);
        }

        System.out.printf("[IA - Sentimento] Modelo treinado com sucesso! (%d amostras, vocabulário de %d termos)%n",
                amostras.size(), p);
    }

    /**
     * Classifica um texto de avaliação como 'Positivo', 'Negativo' ou 'Neutro'.
     */
    public String classificar(String texto) {
        if (texto == null || texto.isBlank()) {
            return NEUTRO;
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
