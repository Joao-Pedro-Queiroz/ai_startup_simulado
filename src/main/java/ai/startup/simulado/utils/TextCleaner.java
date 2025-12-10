package ai.startup.simulado.utils;

import java.util.Map;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.HashMap;

/**
 * Utilitário para limpar duplicações de LaTeX e equações matemáticas em textos de questões.
 * Remove duplicações como:
 * - "5x = 3(y - z)5x = 3(y - z)" -> "5x = 3(y - z)"
 * - "\(x=3\)\(x=3\)" -> "\(x=3\)"
 * - "g(x) = 3x² • 2-3g(x) = 3x2 • 2-3" -> "g(x) = 3x² • 2-3"
 */
public class TextCleaner {

    /**
     * Limpa duplicações em um texto de questão, opção, hint ou solução.
     */
    public static String cleanText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }

        String cleaned = text.trim();

        // 0. NOVO: Remove duplicações completas de equações (ex: "5x = 3(y - z)5x = 3(y - z)")
        // Primeiro, normaliza diferentes tipos de hífen e espaços
        cleaned = cleaned.replaceAll("[–−]", "-"); // Normaliza diferentes tipos de hífen
        // Detecta padrões como: número/variável seguido de "=" seguido de expressão, repetido
        cleaned = cleaned.replaceAll("(\\d*[xyzabcw]\\s*=\\s*[^=]{5,}?)\\1+", "$1");
        // Também captura casos com espaços: "5x = 3(y - z) 5x = 3(y - z)"
        cleaned = cleaned.replaceAll("(\\d*[xyzabcw]\\s*=\\s*[^=]{5,}?)\\s+\\1+", "$1");
        // Captura casos onde há LaTeX no meio
        cleaned = removeCompleteEquationDuplicates(cleaned);

        // 1. Remove duplicações de números e valores simples (ex: $80$80, 15%15%, 3⁻²3⁻²)
        cleaned = cleaned.replaceAll("(\\$?\\d+[⁻²³¹⁰⁴⁵⁶⁷⁸⁹%]?)\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\d+[⁻²³¹⁰⁴⁵⁶⁷⁸⁹%]?)\\s*\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\d+[⁻²³¹⁰⁴⁵⁶⁷⁸⁹]+)\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\$\\d+)\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\d+%)\\1+", "$1");

        // 2. Remove duplicações de blocos LaTeX completos: \(...\) \(...\)
        cleaned = cleaned.replaceAll("(\\\\\\([^)]*?\\\\\\))\\s*\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\\\\\[[^\\]]*?\\\\])\\s*\\1+", "$1");
        cleaned = cleaned.replaceAll("(\\\\\\([^)]{1,50}?\\\\\\))\\1+", "$1");

        // 3. Remove padrão específico: "x = ... * x = ..." (com asterisco no meio)
        cleaned = cleaned.replaceAll("([xyzabcw]\\s*=\\s*[^\\\\*]{5,}?)\\s*\\*\\s*[xyzabcw]\\s*=\\s*[^\\\\]+?", "$1");

        // 4. Remove duplicações de expressões matemáticas: "x = 3(y - z)x = 3(y - z)"
        // Aplica múltiplas vezes para pegar todas as duplicações
        for (int i = 0; i < 5; i++) {
            cleaned = removeDuplicateExpressions(cleaned);
        }

        // 5. Remove duplicações onde há LaTeX seguido do mesmo texto sem LaTeX
        cleaned = removeLatexTextDuplicates(cleaned);

        // 6. Remove duplicações onde há texto seguido do mesmo texto em LaTeX
        cleaned = removeTextLatexDuplicates(cleaned);

        // 7. Análise de metade do texto - detecta duplicações grandes
        cleaned = removeLargeDuplicates(cleaned);

        return cleaned.trim();
    }

    /**
     * Remove duplicações completas de equações (ex: "5x = 3(y - z)5x = 3(y - z)")
     */
    private static String removeCompleteEquationDuplicates(String text) {
        Pattern pattern = Pattern.compile("([xyzabcw]\\s*=\\s*[^=]{3,}?)([xyzabcw]\\s*=\\s*[^=]{3,}?)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String p1 = matcher.group(1);
            String p2 = matcher.group(2);
            String fullMatch = matcher.group(0);

            // Normaliza para comparação
            String norm1 = p1.replaceAll("\\\\[()\\[\\]{}]", "").replaceAll("\\s+", "").toLowerCase();
            String norm2 = p2.replaceAll("\\\\[()\\[\\]{}]", "").replaceAll("\\s+", "").toLowerCase();
            
            // Remove caracteres LaTeX e matemáticos para comparação
            // Também normaliza números que podem estar em formatos diferentes (ex: "53" vs "5" seguido de "3")
            String clean1 = norm1.replaceAll("[(){}[\\]\\\\]", "").replaceAll("[+\\-*/=]", "").replaceAll("frac", "")
                    .replaceAll("\\d+", "N"); // Normaliza números para "N"
            String clean2 = norm2.replaceAll("[(){}[\\]\\\\]", "").replaceAll("[+\\-*/=]", "").replaceAll("frac", "")
                    .replaceAll("\\d+", "N"); // Normaliza números para "N"
            
            // Se são muito similares (70%+), é duplicação
            if (clean1.length() >= 5 && clean2.length() >= 5) {
                int minLen = Math.min(clean1.length(), clean2.length());
                int matches = 0;
                for (int i = 0; i < minLen; i++) {
                    if (clean1.charAt(i) == clean2.charAt(i)) {
                        matches++;
                    }
                }
                if (matches * 1.0 / minLen > 0.7) {
                    // Prefere versão com LaTeX
                    if (p1.contains("\\(") || p1.contains("\\[") || p1.contains("frac")) {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p1));
                    } else if (p2.contains("\\(") || p2.contains("\\[") || p2.contains("frac")) {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p2));
                    } else {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p1));
                    }
                    continue;
                }
            }

            matcher.appendReplacement(sb, fullMatch);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Remove duplicações de expressões matemáticas (ex: "x = 3(y - z)x = 3(y - z)")
     */
    private static String removeDuplicateExpressions(String text) {
        // Padrão: variável = expressão seguida de variável = expressão similar
        Pattern pattern = Pattern.compile("([xyzabcw]\\s*=\\s*[^\\\\]{3,}?)([xyzabcw]\\s*=\\s*[^\\\\]{3,}?)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String p1 = matcher.group(1);
            String p2 = matcher.group(2);

            // Normaliza ambas as partes removendo espaços e convertendo para minúsculas
            String norm1 = p1.replaceAll("\\s+", "").toLowerCase();
            String norm2 = p2.replaceAll("\\s+", "").toLowerCase();

            // Remove caracteres especiais para comparação mais flexível
            String clean1 = norm1.replaceAll("[(){}[\\]]", "").replaceAll("[+\\-*/]", "");
            String clean2 = norm2.replaceAll("[(){}[\\]]", "").replaceAll("[+\\-*/]", "");

            // Compara os primeiros caracteres (mínimo 8 para evitar falsos positivos)
            if (clean1.length() >= 8 && clean2.length() >= 8) {
                int compareLen = Math.min(20, Math.min(clean1.length(), clean2.length()));
                int matches = 0;

                // Conta quantos caracteres são iguais no início
                for (int j = 0; j < compareLen; j++) {
                    if (clean1.charAt(j) == clean2.charAt(j)) {
                        matches++;
                    }
                }

                // Se mais de 70% dos caracteres iniciais são iguais, considera duplicação
                if (matches * 1.0 / compareLen > 0.7) {
                    // Prefere a versão com LaTeX se houver
                    if (p1.contains("\\(") || p1.contains("\\[")) {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p1));
                    } else if (p2.contains("\\(") || p2.contains("\\[")) {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p2));
                    } else {
                        // Caso contrário, retorna a primeira
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(p1));
                    }
                    continue;
                }
            }

            // Se não for duplicação, mantém o match original
            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Remove duplicações onde há LaTeX seguido do mesmo texto sem LaTeX
     */
    private static String removeLatexTextDuplicates(String text) {
        Pattern pattern = Pattern.compile("(\\\\\\([^)]*?\\\\\\))\\s*([^\\\\]{1,50}?)");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String latexBlock = matcher.group(1);
            String textAfter = matcher.group(2);

            String latexContent = latexBlock.replaceAll("^\\\\\\(", "").replaceAll("\\\\\\)$", "");
            String latexNorm = latexContent
                    .replace("\\", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("^", "")
                    .replace("_", "")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            String textNorm = textAfter
                    .replace("⁻", "-").replace("²", "2").replace("³", "3")
                    .replace("¹", "1").replace("⁰", "0").replace("⁴", "4")
                    .replace("⁵", "5").replace("⁶", "6").replace("⁷", "7")
                    .replace("⁸", "8").replace("⁹", "9")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            if (latexNorm.length() > 2 && textNorm.length() > 2) {
                int compareLen = Math.min(10, Math.min(latexNorm.length(), textNorm.length()));
                if (latexNorm.substring(0, compareLen).equals(textNorm.substring(0, compareLen))) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(latexBlock));
                    continue;
                }
            }

            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);

        pattern = Pattern.compile("(\\\\\\[[^\\]]*?\\\\])\\s*([^\\\\]{1,50}?)");
        matcher = pattern.matcher(sb.toString());
        sb = new StringBuffer();

        while (matcher.find()) {
            String latexBlock = matcher.group(1);
            String textAfter = matcher.group(2);

            String latexContent = latexBlock.replaceAll("^\\\\\\[", "").replaceAll("\\\\]$", "");
            String latexNorm = latexContent
                    .replace("\\", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("^", "")
                    .replace("_", "")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            String textNorm = textAfter
                    .replace("⁻", "-").replace("²", "2").replace("³", "3")
                    .replace("¹", "1").replace("⁰", "0").replace("⁴", "4")
                    .replace("⁵", "5").replace("⁶", "6").replace("⁷", "7")
                    .replace("⁸", "8").replace("⁹", "9")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            if (latexNorm.length() > 2 && textNorm.length() > 2) {
                int compareLen = Math.min(10, Math.min(latexNorm.length(), textNorm.length()));
                if (latexNorm.substring(0, compareLen).equals(textNorm.substring(0, compareLen))) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(latexBlock));
                    continue;
                }
            }

            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Remove duplicações onde há texto seguido do mesmo texto em LaTeX
     */
    private static String removeTextLatexDuplicates(String text) {
        Pattern pattern = Pattern.compile("([^\\\\]{1,50}?)\\s*(\\\\\\([^)]*?\\\\\\))");
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String textBefore = matcher.group(1);
            String latexBlock = matcher.group(2);

            String textNorm = textBefore
                    .replace("⁻", "-").replace("²", "2").replace("³", "3")
                    .replace("¹", "1").replace("⁰", "0").replace("⁴", "4")
                    .replace("⁵", "5").replace("⁶", "6").replace("⁷", "7")
                    .replace("⁸", "8").replace("⁹", "9")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            String latexContent = latexBlock.replaceAll("^\\\\\\(", "").replaceAll("\\\\\\)$", "");
            String latexNorm = latexContent
                    .replace("\\", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("^", "")
                    .replace("_", "")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            if (textNorm.length() > 2 && latexNorm.length() > 2) {
                int compareLen = Math.min(10, Math.min(textNorm.length(), latexNorm.length()));
                if (textNorm.substring(0, compareLen).equals(latexNorm.substring(0, compareLen))) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(latexBlock));
                    continue;
                }
            }

            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);

        pattern = Pattern.compile("([^\\\\]{1,50}?)\\s*(\\\\\\[[^\\]]*?\\\\])");
        matcher = pattern.matcher(sb.toString());
        sb = new StringBuffer();

        while (matcher.find()) {
            String textBefore = matcher.group(1);
            String latexBlock = matcher.group(2);

            String textNorm = textBefore
                    .replace("⁻", "-").replace("²", "2").replace("³", "3")
                    .replace("¹", "1").replace("⁰", "0").replace("⁴", "4")
                    .replace("⁵", "5").replace("⁶", "6").replace("⁷", "7")
                    .replace("⁸", "8").replace("⁹", "9")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            String latexContent = latexBlock.replaceAll("^\\\\\\[", "").replaceAll("\\\\]$", "");
            String latexNorm = latexContent
                    .replace("\\", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace("^", "")
                    .replace("_", "")
                    .replaceAll("\\s+", "")
                    .toLowerCase();

            if (textNorm.length() > 2 && latexNorm.length() > 2) {
                int compareLen = Math.min(10, Math.min(textNorm.length(), latexNorm.length()));
                if (textNorm.substring(0, compareLen).equals(latexNorm.substring(0, compareLen))) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(latexBlock));
                    continue;
                }
            }

            matcher.appendReplacement(sb, matcher.group(0));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * Análise de metade do texto - detecta duplicações grandes
     */
    private static String removeLargeDuplicates(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 15) {
            return text;
        }

        int midPoint = normalized.length() / 2;
        String firstHalf = normalized.substring(0, midPoint);
        String secondHalf = normalized.substring(midPoint);

        if (firstHalf.length() > 8 && secondHalf.length() > 8) {
            String firstClean = firstHalf
                    .replace("\\(", "").replace("\\)", "")
                    .replace("\\[", "").replace("\\]", "")
                    .replaceAll("\\s+", "");
            String secondClean = secondHalf
                    .replace("\\(", "").replace("\\)", "")
                    .replace("\\[", "").replace("\\]", "")
                    .replaceAll("\\s+", "");

            int compareLen = Math.min(20, Math.min(firstClean.length(), secondClean.length()));
            if (compareLen > 8 && firstClean.substring(0, compareLen).equals(secondClean.substring(0, compareLen))) {
                boolean firstHasLatex = firstHalf.contains("\\(") || firstHalf.contains("\\[");
                boolean secondHasLatex = secondHalf.contains("\\(") || secondHalf.contains("\\[");

                if (firstHasLatex && !secondHasLatex) {
                    return firstHalf.trim();
                } else if (!firstHasLatex && secondHasLatex) {
                    int latexStart = secondHalf.indexOf("\\");
                    if (latexStart > 0) {
                        return text.substring(0, midPoint + latexStart).trim();
                    }
                }

                int originalMidPoint = text.length() / 2;
                return text.substring(0, originalMidPoint).trim();
            }
        }

        return text;
    }

    /**
     * Limpa todas as opções de uma questão.
     */
    public static Map<String, String> cleanOptions(Map<String, String> options) {
        if (options == null) {
            return null;
        }

        Map<String, String> cleaned = new HashMap<>();
        for (Map.Entry<String, String> entry : options.entrySet()) {
            cleaned.put(entry.getKey(), cleanText(entry.getValue()));
        }
        return cleaned;
    }

    /**
     * Limpa uma lista de strings (usado para solutions).
     */
    public static List<String> cleanStringList(List<String> list) {
        if (list == null) {
            return null;
        }

        return list.stream()
                .map(TextCleaner::cleanText)
                .toList();
    }
}

