# 📁 Seed Data - Simulados Originais

## 🎯 Instruções

Nesta pasta você deve criar **5 arquivos JSON**, um para cada simulado original.

---

## 📝 Arquivos Necessários:

1. **`original_exam_001.json`** - SAT Practice Test #1 (Easy/Standard)
2. **`original_exam_002.json`** - SAT Practice Test #2 (Medium)
3. **`original_exam_003.json`** - SAT Practice Test #3 (Medium/Hard)
4. **`original_exam_004.json`** - SAT Practice Test #4 (Hard)
5. **`original_exam_005.json`** - SAT Practice Test #5 (Mixed/Realistic)

---

## 📋 Formato de Cada Arquivo:

Use o template em `original_exam_template.json` como base.

### **Estrutura Obrigatória:**

```json
{
  "exam_id": "SAT_ORIGINAL_XXX",  // UNIQUE! (001, 002, 003, 004, 005)
  "version": 1,
  "name": "SAT Practice Test #X",
  "difficulty_level": "standard", // easy/standard/hard
  "is_active": true,
  "metadata": {
    "total_questions": 44,
    "duration_minutes": 70,
    "topics_distribution": {
      "algebra": 11,
      "advanced_math": 13,
      "problem_solving": 15,
      "geometry": 5
    }
  },
  "questions": [
    // ARRAY com 44 questões
  ]
}
```

### **Cada Questão DEVE ter:**

```json
{
  "question_number": 1,          // 1 a 44
  "topic": "algebra",            // algebra/advanced_math/problem_solving/geometry
  "subskill": "linear_equations",
  "difficulty": "easy",          // easy/medium/hard
  "question": "Texto da questão aqui...",
  "options": {                   // Para multiple_choice
    "A": "Opção A",
    "B": "Opção B",
    "C": "Opção C",
    "D": "Opção D"
  },
  "correct_option": "B",         // ou número para free_response
  "hint": "Dica aqui...",
  "solution": [
    "Passo 1: ...",
    "Passo 2: ...",
    "Passo 3: ..."
  ],
  "representation": "",          // SVG/imagem se tiver
  "format": "multiple_choice",   // ou "free_response"
  "structure": "solving_for_x"
}
```

---

## ✅ Checklist para Cada Simulado:

- [ ] Tem `exam_id` único (SAT_ORIGINAL_001 a 005)
- [ ] Tem exatamente **44 questões**
- [ ] Todas as questões estão numeradas (1-44)
- [ ] Distribuição de tópicos correta
- [ ] Todas as questões têm `correct_option`
- [ ] Todas as questões têm `hint` e `solution`
- [ ] Formato JSON válido (sem erros de sintaxe)
- [ ] `is_active: true`

---

## 🔍 Como Validar:

Antes de importar, valide o JSON:

```bash
# No terminal:
node -e "console.log(JSON.parse(require('fs').readFileSync('original_exam_001.json')))"

# Ou use uma ferramenta online:
# https://jsonlint.com/
```

---

## 📊 Distribuição Recomendada:

### **Simulado 001 (Easy):**
- 50% easy, 40% medium, 10% hard

### **Simulado 002 (Medium):**
- 30% easy, 50% medium, 20% hard

### **Simulado 003 (Medium/Hard):**
- 20% easy, 50% medium, 30% hard

### **Simulado 004 (Hard):**
- 10% easy, 40% medium, 50% hard

### **Simulado 005 (Realistic):**
- 25% easy, 50% medium, 25% hard (como SAT real)

---

## 💡 Dicas:

1. **Use questões do `total.json`** que você já tem
2. **Mantenha consistência** no formato
3. **Teste com 1 simulado primeiro** antes de criar todos
4. **Faça backup** antes de importar
5. **Valide cada JSON** antes de adicionar ao MongoDB

---

## ⚠️ IMPORTANTE:

- **NÃO mude o formato** (precisa ser compatível com o código Java)
- **NÃO use exam_ids duplicados** (cada um deve ser único)
- **NÃO esqueça nenhum campo obrigatório**
- **SIM use UTF-8** para caracteres especiais (acentos, símbolos matemáticos)

---

Depois de criar os 5 arquivos, execute o script de import:

```bash
cd ../../../scripts
npm install
node import_original_exams.js
```

