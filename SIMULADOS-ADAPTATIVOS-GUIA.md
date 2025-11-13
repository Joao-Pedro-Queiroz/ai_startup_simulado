# 🎯 Simulados Originais ADAPTATIVOS - Guia Completo

## ✅ BACKEND ATUALIZADO!

Sistema agora suporta **simulados adaptativos** com 2 módulos!

---

## 📊 Como Funciona:

### **Estrutura:**
```
SIMULADO ORIGINAL ADAPTATIVO
├── Módulo 1: 22 questões (mesmas para todos)
├── Módulo 2 EASY: 22 questões (se acertar ≤16 no M1)
└── Módulo 2 HARD: 22 questões (se acertar >16 no M1)

Total: 66 questões por simulado
```

### **Threshold:** 16 questões corretas
- **≤16 corretas** no Módulo 1 → Módulo 2 EASY
- **>16 corretas** no Módulo 1 → Módulo 2 HARD

---

## 🔄 Fluxo Completo:

```
1. Usuário inicia simulado original
   ↓
2. Frontend chama: GET /api/simulados/original/{examId}/module1
   Backend → Retorna 22 questões do Módulo 1
   ↓
3. Usuário faz Módulo 1 (22 questões)
   ↓
4. Frontend calcula: quantas acertou (ex: 18)
   ↓
5. Frontend chama: POST /api/simulados/original/{examId}/module2
   Body: { "userId": "xxx", "module1Correct": 18 }
   ↓
6. Backend decide:
   - 18 > 16? SIM → Módulo 2 HARD
   Backend → Retorna 22 questões HARD
   ↓
7. Usuário faz Módulo 2 (22 questões)
   ↓
8. Frontend chama: POST /api/simulados/original/complete
   Body: { 
     "userId": "xxx",
     "examId": "SAT_ORIGINAL_001",
     "score": 85,
     "module1Score": 18,
     "module2Type": "hard"
   }
   ↓
9. Backend salva histórico completo
```

---

## 🌐 Endpoints Atualizados:

### **1. GET /api/simulados/original/{examId}/module1?userId=xxx**
Retorna Módulo 1 (22 questões)

**Response:**
```json
{
  "exam_id": "SAT_ORIGINAL_001",
  "name": "SAT Practice Test #1",
  "is_adaptive": true,
  "metadata": {
    "module_1_questions": 22,
    "threshold": 16,
    ...
  },
  "questions": [ ... 22 questões ... ],
  "module": 1
}
```

---

### **2. POST /api/simulados/original/{examId}/module2**
Retorna Módulo 2 (easy ou hard)

**Request:**
```json
{
  "userId": "64fa2bd6be122ab7a69778a4",
  "examId": "SAT_ORIGINAL_001",
  "module1Correct": 18
}
```

**Response:**
```json
{
  "exam_id": "SAT_ORIGINAL_001",
  "name": "SAT Practice Test #1",
  "module": 2,
  "module_type": "hard",
  "threshold_used": 16,
  "module1_correct": 18,
  "questions": [ ... 22 questões HARD ... ]
}
```

---

### **3. POST /api/simulados/original/complete** (ATUALIZADO)

**Request:**
```json
{
  "userId": "64fa2bd6be122ab7a69778a4",
  "examId": "SAT_ORIGINAL_001",
  "attemptId": "attempt_12345",
  "score": 85,
  "timeTakenMinutes": 65,
  "module1Score": 18,
  "module2Type": "hard"
}
```

---

## 📁 Nova Estrutura JSON:

Cada simulado agora tem **3 seções de questões:**

```json
{
  "exam_id": "SAT_ORIGINAL_001",
  "is_adaptive": true,
  "metadata": {
    "total_questions": 66,
    "module_1_questions": 22,
    "module_2_questions": 22,
    "threshold": 16
  },
  "module_1": [
    // 22 questões (questões 1-22)
    // Mix de dificuldades
  ],
  "module_2_easy": [
    // 22 questões FÁCEIS (questões 23-44)
    // Maioria easy/medium
  ],
  "module_2_hard": [
    // 22 questões DIFÍCEIS (questões 23-44)
    // Maioria medium/hard
  ]
}
```

---

## 📝 O QUE VOCÊ PRECISA CRIAR:

### **Para CADA simulado (5 total):**

#### **Módulo 1 (22 questões):**
- Questões 1-22
- Mix balanceado de dificuldades
- Cobre todos os tópicos
- **Mesmas para todos os usuários**

#### **Módulo 2 Easy (22 questões):**
- Questões 23-44
- **50% easy, 40% medium, 10% hard**
- Para usuários que acertaram ≤16

#### **Módulo 2 Hard (22 questões):**
- Questões 23-44
- **10% easy, 30% medium, 60% hard**
- Para usuários que acertaram >16

---

## 📊 Total de Questões:

```
Módulo 1:      22 questões
Módulo 2 Easy: 22 questões
Módulo 2 Hard: 22 questões
─────────────────────────────
Por simulado:  66 questões

× 5 simulados = 330 QUESTÕES TOTAIS
```

---

## 🎯 Distribuição Recomendada:

### **Módulo 1 (para todos):**
```
Algebra:        5-6 questões (mix)
Advanced Math:  6-7 questões (mix)
Problem Solving: 7-8 questões (mix)
Geometry:       2-3 questões (mix)
```

### **Módulo 2 Easy:**
```
Algebra:        5-6 questões (easy/medium)
Advanced Math:  6-7 questões (easy/medium)
Problem Solving: 7-8 questões (easy/medium)
Geometry:       2-3 questões (easy)
```

### **Módulo 2 Hard:**
```
Algebra:        5-6 questões (medium/hard)
Advanced Math:  6-7 questões (hard)
Problem Solving: 7-8 questões (medium/hard)
Geometry:       2-3 questões (medium/hard)
```

---

## 🔧 Script de Validação Atualizado:

O script `validate_exams.js` agora vai verificar:
- ✅ Tem module_1 com 22 questões
- ✅ Tem module_2_easy com 22 questões
- ✅ Tem module_2_hard com 22 questões
- ✅ Total = 66 questões
- ✅ `is_adaptive: true`
- ✅ `threshold` definido no metadata

---

## 📋 Checklist para Cada Simulado:

- [ ] `exam_id` único (SAT_ORIGINAL_001 a 005)
- [ ] `is_adaptive: true`
- [ ] `metadata.threshold: 16`
- [ ] `metadata.total_questions: 66`
- [ ] `module_1` com 22 questões (1-22)
- [ ] `module_2_easy` com 22 questões (23-44)
- [ ] `module_2_hard` com 22 questões (23-44)
- [ ] Todas as questões com LaTeX
- [ ] Formato JSON válido

---

## 🚀 Próximos Passos:

### **1. Reestruturar o original_exam_001.json**

As 21 questões que você já criou vão para o **Módulo 1**.

Você ainda precisa criar:
- **1 questão** para completar Módulo 1 (total: 22)
- **22 questões** para Módulo 2 Easy
- **22 questões** para Módulo 2 Hard

**Total por simulado:** 66 questões

### **2. Depois validar:**
```bash
cd scripts
npm run validate
```

### **3. Importar:**
```bash
npm run import
```

---

## 📝 Template Atualizado:

Criado em: `original_exam_adaptive_template.json`

Use esse template como base para criar os 5 simulados!

---

## 💡 Dica:

**Comece assim:**
1. Pegue as 21 questões que já criou
2. Adicione 1 questão → Módulo 1 completo (22)
3. Crie 22 questões FÁCEIS → Módulo 2 Easy
4. Crie 22 questões DIFÍCEIS → Módulo 2 Hard
5. Total: 66 questões = 1 simulado completo!

**Depois** replique para os outros 4 simulados.

---

## 🎉 BACKEND PRONTO!

Todas as alterações foram feitas:
- ✅ Model atualizado (suporta módulos)
- ✅ Service atualizado (lógica adaptativa)
- ✅ Controller com novos endpoints
- ✅ DTO para request do módulo 2
- ✅ Histórico rastreia qual módulo 2 foi usado

**Agora só precisa criar os JSONs no novo formato!** 🚀

