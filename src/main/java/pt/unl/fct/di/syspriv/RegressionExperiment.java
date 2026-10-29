package pt.unl.fct.di.syspriv;

import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;
import pt.unl.fct.di.syspriv.kanonymity.KAnonymityTool;
import pt.unl.fct.di.syspriv.util.ConfigLoader;
import pt.unl.fct.di.syspriv.util.LinearRegression;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Experiencia de regressao linear sobre dados de pacientes.
 * Compara dados originais, Mondrian, t-closeness e DP.
 * As metricas sao avaliadas sobre dados originais e NAO sao privadas.
 */
public class RegressionExperiment {
    private static final int YEAR = 2026;
    private static final double MAX_AGE = 120.0;
    private static final double MAX_GLUCOSE = 600.0;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static void main(String[] args) {
        System.out.println("=== EXPERIENCIA DE REGRESSAO LINEAR ===");

        List<AnonymousHealthData> records = createRecords();
        double[] originalAges = ages(records);
        double[] originalGlucose = glucose(records);
        LinearRegression regression = new LinearRegression();

        System.out.println("\n--- CASO 1: Dados originais ---");
        LinearRegression.RegressionResult original = regression.run(originalAges, originalGlucose);
        printResult("Original", original, originalAges, originalGlucose);

        KAnonymityTool<AnonymousHealthData> tool = new KAnonymityTool<>(3, 0.2f);

        System.out.println("\n--- CASO 2: K-Anonymity (k=3) ---");
        try {
            List<AnonymousHealthData> anonymized = tool.runMondrain(records);
            validateAnonymization(
                    "MONDRIAN",
                    records,
                    anonymized,
                    tool,
                    false
            );
            printGeneralization("MONDRIAN", anonymized);
            runGeneralizedRegression("K-Anonymity", anonymized, regression,
                    originalAges, originalGlucose);
        } catch (Exception e) {
            System.out.println("Erro no Caso 2: " + e.getMessage());
        }

        System.out.println("\n--- CASO 3: K-Anonymity + T-Closeness (k=3, t=0.2) ---");
        try {
            List<AnonymousHealthData> anonymized = tool.runTCloseMondrain(records);
            validateAnonymization(
                    "T-CLOSENESS",
                    records,
                    anonymized,
                    tool,
                    true
            );
            printGeneralization("T-CLOSENESS", anonymized);
            runGeneralizedRegression("T-Closeness", anonymized, regression,
                    originalAges, originalGlucose);
        } catch (Exception e) {
            System.out.println("Erro no Caso 3: " + e.getMessage());
        }

        System.out.println("\n--- CASO 4: Differential Privacy (regressao) ---");
        double epsilon = ConfigLoader.getDoubleProperty("dp.epsilon", 5.0);
        if (!Double.isFinite(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException("dp.epsilon deve ser positivo e finito");
        }
        System.out.println("Epsilon configurado: " + epsilon);
        System.out.println("Orcamento dividido entre 5 estatisticas agregadas.");

        RegressionCoefficients dp = privateRegression(originalAges, originalGlucose, epsilon);
        if (dp == null) {
            System.out.println("Regressao DP numericamente indefinida nesta execucao.");
        } else {
            printCoefficients("Differential Privacy", dp.slope, dp.intercept,
                    originalAges, originalGlucose);
        }
        System.out.println("\nNOTA: R2/MSE sao calculados sobre dados originais apenas para avaliacao experimental.");
        System.out.println("Nao devem ser publicados como metricas diferencialmente privadas.");
        evaluateDifferentialPrivacy(
                originalAges,
                originalGlucose,
                original.getSlope(),
                original.getIntercept()
        );
        System.out.println("=== FIM DA EXPERIENCIA ===");
    }

    private static List<AnonymousHealthData> createRecords() {
        Map<Long, Integer> birthYears = new HashMap<>();
        Map<Long, Double> glucoseSums = new HashMap<>();
        Map<Long, Integer> glucoseCounts = new HashMap<>();

        try (
                InputStream patientStream = RegressionExperiment.class
                        .getClassLoader().getResourceAsStream("patient.csv");
                InputStream glucoseStream = RegressionExperiment.class
                        .getClassLoader().getResourceAsStream("updated_glucose.csv")
        ) {
            if (patientStream == null || glucoseStream == null) {
                throw new IllegalStateException(
                        "Ficheiros CSV nao encontrados em src/main/resources");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(patientStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    String[] columns = line.split(",", 3);

                    try {
                        long patientId = Long.parseLong(columns[0].trim());
                        int birthYear = Integer.parseInt(
                                columns[2].trim().substring(0, 4));
                        birthYears.put(patientId, birthYear);
                    } catch (NumberFormatException e) {
                        // Ignora o cabecalho do CSV.
                        if (!line.toLowerCase().contains("id")) {
                            throw e;
                        }
                    }
                }
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(glucoseStream, StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    String[] columns = line.split(",", 4);

                    try {
                        long patientId = Long.parseLong(columns[1].trim());
                        double glucose = Double.parseDouble(columns[2].trim());
                        if (!Double.isFinite(glucose)) {
                            throw new IllegalArgumentException(
                                    "Valor de glicose nao finito para o paciente "
                                            + patientId);
                        }
                        if (!birthYears.containsKey(patientId)) {
                            throw new IllegalStateException(
                                    "Paciente nao encontrado: " + patientId);
                        }

                        glucoseSums.merge(patientId, glucose, Double::sum);
                        glucoseCounts.merge(patientId, 1, Integer::sum);

                    } catch (NumberFormatException e) {
                        if (!line.toLowerCase().contains("patient")) {
                            throw e;
                        }
                    }
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("Erro ao ler os ficheiros CSV", e);
        }

        List<AnonymousHealthData> records = new ArrayList<>();

        for (Long patientId : glucoseSums.keySet().stream().sorted().toList()) {
            double meanGlucose = glucoseSums.get(patientId)
                    / glucoseCounts.get(patientId);

            records.add(new AnonymousHealthData(
                    patientId,
                    birthYears.get(patientId),
                    0,
                    (float) meanGlucose,
                    new Date()
            ));
        }

        System.out.println("Pacientes carregados: " + records.size());
        return records;
    }

    private static double[] ages(List<AnonymousHealthData> records) {
        double[] values = new double[records.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = YEAR - records.get(i).getPatientBirthYear();
        }
        return values;
    }

    private static double[] generalizedAges(List<AnonymousHealthData> records) {
        double[] values = new double[records.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = YEAR - generalizedBirthYear(records.get(i));
        }
        return values;
    }

    private static double generalizedBirthYear(AnonymousHealthData record) {
        Object value = record.getQuasiIdentifiers().get("birthYear");
        if (value == null) {
            throw new IllegalArgumentException("Ano de nascimento em falta");
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        String interval = value.toString().trim();
        String[] parts = interval.split("\\s*-\\s*");
        if (parts.length == 2) {
            double min = Double.parseDouble(parts[0]);
            double max = Double.parseDouble(parts[1]);
            if (min > max) {
                throw new IllegalArgumentException("Intervalo de nascimento invalido: " + interval);
            }
            return (min + max) / 2.0;
        }
        return Double.parseDouble(interval);
    }

    private static double[] glucose(List<AnonymousHealthData> records) {
        double[] values = new double[records.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = records.get(i).getGlucose();
        }
        return values;
    }

    private static void printGeneralization(String label, List<AnonymousHealthData> records) {
        System.out.println("\n=== VALORES APOS " + label + " ===");
        for (AnonymousHealthData record : records) {
            System.out.println("ID: " + record.getId()
                    + " | Ano original: " + record.getPatientBirthYear()
                    + " | Ano generalizado: " + record.getQuasiIdentifiers().get("birthYear")
                    + " | Glicose: " + record.getGlucose());
        }
    }

    private static void runGeneralizedRegression(String name,
                                                 List<AnonymousHealthData> anonymized, LinearRegression regression,
                                                 double[] originalAges, double[] originalGlucose) {
        double[] x = generalizedAges(anonymized);
        double[] y = glucose(anonymized);
        if (x.length < 2 || x.length != y.length) {
            System.out.println(name + ": observacoes insuficientes para regressao.");
            return;
        }
        double min = x[0], max = x[0];
        for (double age : x) {
            min = Math.min(min, age);
            max = Math.max(max, age);
        }
        if (Math.abs(max - min) < 1e-9) {
            System.out.println(name + ": regressao nao estimavel: todas as idades generalizadas sao iguais ("
                    + min + "). R2/MSE da regressao: N/A.");
            return;
        }
        LinearRegression.RegressionResult result = regression.run(x, y);
        printResult(name, result, originalAges, originalGlucose);
    }

    private static void printResult(String name, LinearRegression.RegressionResult r,
                                    double[] originalAges, double[] originalGlucose) {
        printCoefficients(name, r.getSlope(), r.getIntercept(), originalAges, originalGlucose);
        System.out.println("R2 no conjunto de treino: " + r.getRSquare());
        System.out.println("MSE no conjunto de treino: " + r.getMeanSquareError());
    }

    private static void printCoefficients(String name, double slope, double intercept,
                                          double[] ages, double[] glucose) {
        System.out.printf("%s | slope=%.6f | intercept=%.6f%n", name, slope, intercept);
        double mean = 0;
        for (double value : glucose) mean += value;
        mean /= glucose.length;
        double sse = 0, sst = 0;
        for (int i = 0; i < ages.length; i++) {
            double error = glucose[i] - (intercept + slope * ages[i]);
            sse += error * error;
            double deviation = glucose[i] - mean;
            sst += deviation * deviation;
        }
        System.out.printf("R2 (avaliacao nao privada)=%.6f | MSE (avaliacao nao privada)=%.6f%n",
                sst > 0 ? 1 - sse / sst : Double.NaN, sse / ages.length);
    }

    /**
     * Regressao linear com Differential Privacy.
     *
     * Modelo de adjacencia: adicao/remocao de um paciente.
     * Cada paciente contribui com uma observacao agregada.
     *
     * Idade limitada a [0,120] e centrada em 60:
     * x pertence a [-60,60].
     *
     * Glicose limitada a [0,600].
     *
     * Estatisticas privadas:
     * n, sum(x), sum(y), sum(x*x), sum(x*y).
     *
     * Sensibilidades:
     * 1, 60, 600, 3600 e 36000.
     *
     * O orcamento epsilon e dividido igualmente
     * pelas cinco estatisticas.
     *
     * Os coeficientes sao calculados por pos-processamento,
     * utilizando regularizacao Ridge com lambda = 10000.
     */

    private static RegressionCoefficients privateRegression(
            double[] ages,
            double[] glucose,
            double epsilon) {

        if (ages.length != glucose.length || ages.length < 2) {
            throw new IllegalArgumentException(
                    "Dados insuficientes ou incompativeis");
        }

        if (!Double.isFinite(epsilon) || epsilon <= 0.0) {
            throw new IllegalArgumentException(
                    "Epsilon deve ser positivo e finito");
        }

        // Centro publico e fixo da idade.
        final double AGE_CENTER = 60.0;

        // Regularizacao fixa, independente dos dados privados.
        final double LAMBDA = 10000.0;

        // Estatisticas agregadas.
        double n = 0.0;
        double sx = 0.0;
        double sy = 0.0;
        double sxx = 0.0;
        double sxy = 0.0;

        for (int i = 0; i < ages.length; i++) {

            double age = Math.max(
                    0.0, Math.min(MAX_AGE, ages[i]));

            double y = Math.max(
                    0.0, Math.min(MAX_GLUCOSE, glucose[i]));

            // Idade centrada em 60 anos.
            double x = age - AGE_CENTER;

            n++;
            sx += x;
            sy += y;
            sxx += x * x;
            sxy += x * y;
        }

        // Sensibilidades com x em [-60, 60].
        final double MAX_CENTERED_AGE = 60.0;

        // Divisao do orcamento entre 5 estatisticas.
        double share = epsilon / 5.0;

        double noisyN = n
                + laplace(1.0 / share);

        double noisySx = sx
                + laplace(MAX_CENTERED_AGE / share);

        double noisySy = sy
                + laplace(MAX_GLUCOSE / share);

        double noisySxx = sxx
                + laplace(
                MAX_CENTERED_AGE * MAX_CENTERED_AGE / share);

        double noisySxy = sxy
                + laplace(
                MAX_CENTERED_AGE * MAX_GLUCOSE / share);

        // Evita dividir por contagens muito pequenas.
        // O limite e fixo e aplicado apos o ruido.
        double safeN = Math.max(noisyN, 10.0);

        // Estatisticas centradas com ruido.
        double centeredXX =
                noisySxx - (noisySx * noisySx) / safeN;

        double centeredXY =
                noisySxy - (noisySx * noisySy) / safeN;

        // Ridge: denominador sempre positivo.
        double denominator =
                Math.max(0.0, centeredXX) + LAMBDA;

        double slope = centeredXY / denominator;

        // Intercepto na escala da idade centrada.
        double centeredIntercept =
                (noisySy - slope * noisySx) / safeN;

        // Conversao para a idade original.
        double intercept =
                centeredIntercept - slope * AGE_CENTER;

        if (!Double.isFinite(slope)
                || !Double.isFinite(intercept)) {
            return null;
        }

        return new RegressionCoefficients(slope, intercept);
    }


    private static double laplace(double scale) {
        double u = RANDOM.nextDouble() - 0.5;
        return -scale * Math.signum(u) * Math.log1p(-2.0 * Math.abs(u));
    }

    private static void validateAnonymization(
            String label,
            List<AnonymousHealthData> original,
            List<AnonymousHealthData> anonymized,
            KAnonymityTool<AnonymousHealthData> tool,
            boolean checkTCloseness) {

        System.out.println("\n=== VALIDACAO: " + label + " ===");

        boolean countPreserved = original.size() == anonymized.size();

        Map<Map<String, Object>, List<AnonymousHealthData>> groups =
                new HashMap<>();

        for (AnonymousHealthData record : anonymized) {
            Map<String, Object> qi =
                    new HashMap<>(record.getQuasiIdentifiers());

            groups.computeIfAbsent(qi, key -> new ArrayList<>())
                    .add(record);
        }

        int minSize = groups.values().stream()
                .mapToInt(List::size)
                .min()
                .orElse(0);

        int maxSize = groups.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(0);

        boolean kValid = tool.isKAnonymous(anonymized);

        // Confirma que os valores sensiveis nao foram alterados,
        // perdidos ou duplicados.
        List<Double> originalValues = new ArrayList<>();
        List<Double> anonymizedValues = new ArrayList<>();

        for (AnonymousHealthData record : original) {
            originalValues.add(
                    ((Number) record.getSensitiveValue()).doubleValue());
        }

        for (AnonymousHealthData record : anonymized) {
            anonymizedValues.add(
                    ((Number) record.getSensitiveValue()).doubleValue());
        }

        Collections.sort(originalValues);
        Collections.sort(anonymizedValues);

        boolean sensitiveValuesPreserved =
                originalValues.equals(anonymizedValues);

        System.out.println("Registos originais: " + original.size());
        System.out.println("Registos anonimizados: " + anonymized.size());
        System.out.println("Classes de equivalencia: " + groups.size());
        System.out.println("Dimensao minima: " + minSize);
        System.out.println("Dimensao maxima: " + maxSize);
        System.out.println("K-Anonymity valido: " + kValid);
        System.out.println("Quantidade preservada: " + countPreserved);
        System.out.println("Valores sensiveis preservados: "
                + sensitiveValuesPreserved);

        boolean tValid = true;

        if (checkTCloseness) {
            int violations = 0;

            for (List<AnonymousHealthData> group : groups.values()) {
                if (!tool.isTClose(group, anonymized)) {
                    violations++;
                }
            }

            tValid = violations == 0;

            System.out.println("T-Closeness valido: " + tValid);
            System.out.println("Classes que violam t: " + violations);
        }

        boolean passed = countPreserved
                && sensitiveValuesPreserved
                && kValid
                && minSize >= 3
                && tValid;

        System.out.println("RESULTADO FINAL: "
                + (passed ? "PASSOU" : "FALHOU"));

        if (!passed) {
            throw new IllegalStateException(
                    "Validacao de privacidade falhou: " + label);
        }
    }

    private static void evaluateDifferentialPrivacy(
            double[] ages,
            double[] glucose,
            double originalSlope,
            double originalIntercept) {

// Experiencia  para avaliar a utilidade estatistica.
// As repeticoes nao representam uma unica consulta DP.
// A divulgacao conjunta dos resultados exige contabilizar
// a composicao dos orcamentos de privacidade.

        final int repetitions = 100;
        final double[] epsilons = {0.5, 1.0, 5.0};

        double meanGlucose = 0.0;
        for (double value : glucose) {
            meanGlucose += value;
        }
        meanGlucose /= glucose.length;

        double sst = 0.0;
        for (double value : glucose) {
            double deviation = value - meanGlucose;
            sst += deviation * deviation;
        }

        System.out.println("\n=== AVALIACAO EXPERIMENTAL DE DP ===");
        System.out.println("Pacientes: " + ages.length);
        System.out.println("Repeticoes por epsilon: " + repetitions);

        System.out.println(
                "epsilon;validas;invalidas;R2_medio;R2_mediana;" +
                        "MSE_medio;MSE_mediana;MSE_desvio_padrao;" +
                        "MSE_P90;MSE_P95;MSE_maximo;" +
                        "erro_slope_medio;erro_intercept_medio;" +
                        "coeficientes_extremos");

        for (double epsilon : epsilons) {

            List<Double> mseValues = new ArrayList<>();
            List<Double> r2Values = new ArrayList<>();

            double sumSlopeError = 0.0;
            double sumInterceptError = 0.0;

            int invalid = 0;
            int extremeCoefficients = 0;

            for (int i = 0; i < repetitions; i++) {

                RegressionCoefficients result =
                        privateRegression(ages, glucose, epsilon);

                if (result == null) {
                    invalid++;
                    continue;
                }

                double slope = result.slope;
                double intercept = result.intercept;

                double sse = 0.0;

                for (int j = 0; j < ages.length; j++) {
                    double predicted = intercept + slope * ages[j];
                    double error = glucose[j] - predicted;
                    sse += error * error;
                }

                double mse = sse / ages.length;
                double r2 = sst > 0.0
                        ? 1.0 - sse / sst
                        : Double.NaN;

                if (!Double.isFinite(mse)
                        || !Double.isFinite(r2)) {
                    invalid++;
                    continue;
                }

                mseValues.add(mse);
                r2Values.add(r2);

                sumSlopeError += Math.abs(slope - originalSlope);
                sumInterceptError +=
                        Math.abs(intercept - originalIntercept);

                // Limiares de diagnostico, sem excluir resultados.
                if (Math.abs(slope) > 10.0
                        || Math.abs(intercept) > 1000.0) {
                    extremeCoefficients++;
                }
            }

            int valid = mseValues.size();

            if (valid == 0) {
                System.out.println(
                        "epsilon=" + epsilon +
                                ": nenhuma regressao numericamente valida");
                continue;
            }

            Collections.sort(mseValues);
            Collections.sort(r2Values);

            double sumMSE = 0.0;
            double sumR2 = 0.0;

            for (double value : mseValues) {
                sumMSE += value;
            }

            for (double value : r2Values) {
                sumR2 += value;
            }

            double meanMSE = sumMSE / valid;
            double meanR2 = sumR2 / valid;

            double variance = 0.0;
            for (double value : mseValues) {
                double difference = value - meanMSE;
                variance += difference * difference;
            }

            double stdMSE = Math.sqrt(variance / valid);

            double medianMSE = percentile(mseValues, 0.50);
            double p90MSE = percentile(mseValues, 0.90);
            double p95MSE = percentile(mseValues, 0.95);
            double maxMSE = mseValues.get(valid - 1);

            double medianR2 = percentile(r2Values, 0.50);

            double meanSlopeError = sumSlopeError / valid;
            double meanInterceptError =
                    sumInterceptError / valid;

            System.out.printf(
                    java.util.Locale.US,
                    "%.1f;%d;%d;%.6f;%.6f;" +
                            "%.6f;%.6f;%.6f;" +
                            "%.6f;%.6f;%.6f;" +
                            "%.6f;%.6f;%d%n",
                    epsilon,
                    valid,
                    invalid,
                    meanR2,
                    medianR2,
                    meanMSE,
                    medianMSE,
                    stdMSE,
                    p90MSE,
                    p95MSE,
                    maxMSE,
                    meanSlopeError,
                    meanInterceptError,
                    extremeCoefficients
            );
        }

        System.out.println("=== FIM DA AVALIACAO DP ===");
    }

    private static double percentile(
            List<Double> sortedValues,
            double probability) {

        if (sortedValues.isEmpty()) {
            return Double.NaN;
        }

        double position =
                probability * (sortedValues.size() - 1);

        int lower = (int) Math.floor(position);
        int upper = (int) Math.ceil(position);

        double fraction = position - lower;

        return sortedValues.get(lower)
                + fraction * (
                sortedValues.get(upper) - sortedValues.get(lower));
    }

    private static class RegressionCoefficients {
        final double slope;
        final double intercept;

        RegressionCoefficients(double slope, double intercept) {
            this.slope = slope;
            this.intercept = intercept;
        }
    }
}
