# 🚀 TESTE RÁPIDO - SIMULADOS ORIGINAIS

## ⚡ PASSOS RÁPIDOS (5 MINUTOS)

### **1. Importar SAT_ORIGINAL_001 para MongoDB**

```bash
cd /Users/luisepessoabastos/Documents/BrainWin/ai_startup_simulado/scripts
npm run import
```

**Output esperado:**
```
✅ Importando original_exam_001.json...
✅ SAT_ORIGINAL_001 importado com sucesso!
Total de exames no banco: 1
```

---

### **2. Iniciar Backend (SimuladoApplication)**

**Opção A - Via IntelliJ/Eclipse:**
- Abra `ai_startup_simulado/src/main/java/ai/startup/simulado/SimuladoApplication.java`
- Clique em "Run" ▶️

**Opção B - Via Terminal:**
```bash
cd /Users/luisepessoabastos/Documents/BrainWin/ai_startup_simulado
mvn spring-boot:run
```

**Output esperado:**
```
Started SimuladoApplication in X seconds
Tomcat started on port(s): 8082 (http)
```

---

### **3. Testar no Frontend**

1. **Abra o dashboard:** http://localhost:5173/
2. **Faça login** (se não estiver logado)
3. **Clique no botão "Original"** (azul)
4. **Confirme** o gasto de 5 wins

**O que deve acontecer:**
- ✅ Sistema debita 5 wins
- ✅ Navega para `/simulado/original`
- ✅ Carrega as 22 questões do Módulo 1
- ✅ Mostra a primeira questão

---

## 🔍 COMO SABER SE FUNCIONOU?

### **✅ Logs do Backend (Terminal do SimuladoApplication):**

```
[OriginalExam] Buscando próximo simulado original para userId: <seu_user_id>
[OriginalExam] Simulados já completados: []
[OriginalExam] Total de simulados ativos no sistema: 1
[OriginalExam] Simulados disponíveis para este usuário: [SAT_ORIGINAL_001]
[OriginalExam] 🎯 Próximo simulado selecionado: SAT_ORIGINAL_001
[OriginalExam] ✅ Retornando simulado SAT_ORIGINAL_001 com 22 questões no Módulo 1
```

### **✅ Frontend (Console do navegador):**

```
[RunnerPage] Modo detectado: original
[RunnerPage] Iniciando simulado original...
[RunnerPage] ✅ Simulado criado! ID: 67...
[RunnerPage] Questões recebidas: 22
```

### **✅ No MongoDB (opcional):**

```bash
mongo
use SatQuestions

# Ver o simulado importado
db.original_exams.findOne({exam_id: "SAT_ORIGINAL_001"})

# Ver histórico do usuário
db.user_exam_history.findOne({userId: "<seu_user_id>"})
```

---

## 🎯 TESTE COMPLETO DO FLUXO ADAPTATIVO

### **1. Responder Módulo 1 (22 questões)**
- Acerte **menos de 16**: receberá Módulo 2 EASY
- Acerte **mais de 16**: receberá Módulo 2 HARD

### **2. Sistema determina Módulo 2 automaticamente**
O frontend deve fazer:
```http
POST /api/simulados/original/SAT_ORIGINAL_001/module2
Body: {
  "userId": "...",
  "module1Correct": 18
}
```

**Resposta esperada (se acertou 18 > 16):**
```json
{
  "module_type": "hard",
  "questions": [ /* 22 questões hard */ ],
  "threshold_used": 16,
  "module1_correct": 18
}
```

### **3. Completar simulado**
Ao finalizar, o frontend chama:
```http
POST /api/simulados/original/complete
Body: {
  "userId": "...",
  "examId": "SAT_ORIGINAL_001",
  "score": 85,
  "timeTaken": 45,
  "attemptId": "...",
  "module1Score": 18,
  "module2Type": "hard"
}
```

---

## 🐛 PROBLEMAS COMUNS

### **❌ Erro: "Connection refused to localhost:8082"**
**Solução:** Backend não está rodando
```bash
cd ai_startup_simulado
mvn spring-boot:run
```

---

### **❌ Erro: "Simulado SAT_ORIGINAL_001 não encontrado"**
**Solução:** Não foi importado para o MongoDB
```bash
cd scripts
npm run import
```

---

### **❌ Erro: "Saldo insuficiente de wins"**
**Solução:** Usuário tem menos de 5 wins

**Adicione wins manualmente no MongoDB:**
```javascript
mongo
use SatUsuarios
db.users.updateOne(
  {_id: ObjectId("<seu_user_id>")},
  {$set: {wins: 100}}
)
```

---

### **❌ Erro: "Há um simulado em aberto"**
**Solução:** Finalize o simulado aberto ou delete

**Via API:**
```http
DELETE /simulados/<id_do_simulado_aberto>
```

**Via MongoDB:**
```javascript
mongo
use SatSimulados
db.simulados.updateOne(
  {_id: ObjectId("<simulado_id>")},
  {$set: {status: "FINALIZADO"}}
)
```

---

## 📊 VERIFICAR SE TUDO ESTÁ OK

### **1. MongoDB tem o simulado:**
```bash
mongo
use SatQuestions
db.original_exams.count({exam_id: "SAT_ORIGINAL_001"})
# Deve retornar: 1
```

### **2. Backend está rodando:**
```bash
curl http://localhost:8082/actuator/health
# Deve retornar: {"status":"UP"}
```

### **3. Frontend está conectado:**
```javascript
// Console do navegador
console.log(localStorage.getItem('jwt'))
// Deve ter um token JWT
```

---

## ✅ CHECKLIST FINAL

- [ ] MongoDB está rodando (`mongod` em uma aba do terminal)
- [ ] SAT_ORIGINAL_001 foi importado (`npm run import`)
- [ ] Backend rodando na porta 8082 (`mvn spring-boot:run`)
- [ ] Frontend rodando na porta 5173 (`npm run dev`)
- [ ] Fiz login no sistema
- [ ] Tenho pelo menos 5 wins
- [ ] Cliquei em "Original"
- [ ] Vi as 22 questões do Módulo 1
- [ ] **SUCESSO!** 🎉

---

## 🎊 SE DEU CERTO

Você verá:
1. ✅ Questões formatadas em LaTeX
2. ✅ Contador de questões: "1 / 22"
3. ✅ Botões de navegação (Anterior/Próximo)
4. ✅ Opções múltiplas (A, B, C, D)
5. ✅ Botões de Hint e Solution funcionando
6. ✅ Timer rodando no topo

**PARABÉNS! ESTÁ FUNCIONANDO!** 🚀🎉

---

## 🔥 PRÓXIMOS TESTES

1. Complete o Módulo 1
2. Veja se o sistema carrega o Módulo 2 correto (easy/hard)
3. Complete o simulado inteiro
4. Clique em "Original" novamente
5. Verifique se ainda pega o SAT_ORIGINAL_001 (porque você só tem 1 importado)
6. Depois de completar, importe o 002 e teste se o sistema pega o próximo

---

## 💡 DICA PRO

Para testar múltiplos simulados rapidamente:

```bash
# Criar simulados 002, 003, 004, 005
# (você pode copiar o 001 e mudar o exam_id)
cp src/main/resources/seed_data/original_exam_001.json \
   src/main/resources/seed_data/original_exam_002.json

# Editar o exam_id dentro do JSON
sed -i 's/SAT_ORIGINAL_001/SAT_ORIGINAL_002/g' \
    src/main/resources/seed_data/original_exam_002.json

# Importar todos
npm run import
```

---

**BOA SORTE!** 🍀
Se der qualquer erro, me mande os logs do backend! 📋


