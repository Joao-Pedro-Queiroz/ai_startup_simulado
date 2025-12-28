# 🔧 Scripts - Simulados Originais

## 📋 Scripts Disponíveis:

### **1. validate_exams.js**
Valida os arquivos JSON antes de importar.

**Uso:**
```bash
npm run validate
```

**O que faz:**
- ✅ Verifica se todos os 5 arquivos existem
- ✅ Valida formato JSON
- ✅ Verifica campos obrigatórios
- ✅ Valida estrutura das questões
- ✅ Conta número de questões (deve ser 44)
- ✅ Verifica exam_ids únicos

---

### **2. import_original_exams.js**
Importa os simulados para o MongoDB.

**Uso:**
```bash
# 1. Configure a variável de ambiente
export mongo_felps="sua_uri_mongodb"

# 2. Execute o import
npm run import
```

**O que faz:**
- 🔌 Conecta ao MongoDB
- 📊 Verifica simulados existentes
- 📝 Importa os 5 arquivos JSON
- 🔄 Atualiza se já existir (por exam_id)
- ✅ Mostra resumo final

---

## 🚀 Passo a Passo:

### **1. Criar os simulados JSON**

Vá para: `../src/main/resources/seed_data/`

Crie os arquivos:
- `original_exam_011.json` (44 questões)
- `original_exam_012.json` (44 questões)
- `original_exam_013.json` (44 questões)
- `original_exam_014.json` (44 questões)

Use `original_exam_template.json` como base!

---

### **2. Instalar dependências**

```bash
cd /Users/luisepessoabastos/Documents/BrainWin/ai_startup_simulado/scripts
npm install
```

---

### **3. Validar os JSONs**

```bash
npm run validate
```

Se houver erros, corrija antes de importar!

---

### **4. Importar para o MongoDB**

```bash
# Configure a URI do MongoDB
export mongo_felps="sua_uri_mongodb"

# Execute o import
npm run import
```

---

## ✅ Resultado Esperado:

Ao rodar `npm run import`, você verá:

```
═══════════════════════════════════════════════
🚀 Import de Simulados Originais para MongoDB
═══════════════════════════════════════════════

📊 Database: SatQuestions
📁 Collection: original_exams

🔌 Conectando ao MongoDB...
✅ Conectado com sucesso!

📊 Simulados existentes: 0

📝 Processando original_exam_011.json...
   📊 exam_id: SAT_ORIGINAL_011
   📚 Questões: 44
   🎯 Nível: standard
   ➕ Inserindo novo simulado...
   ✅ SAT_ORIGINAL_011 inserido!

... (repete para 012-014) ...

═══════════════════════════════════════════════
🎉 Import Concluído!
═══════════════════════════════════════════════
✅ Importados: 4
⏭️  Pulados: 0
📊 Total na collection: 4
═══════════════════════════════════════════════

🎯 Perfeito! Todos os 4 simulados estão no MongoDB!
```

---

## 🔧 Solução de Problemas:

### ❌ "mongo_felps não configurada"
```bash
export mongo_felps="sua_uri_mongodb"
```

### ❌ "Arquivo não encontrado"
- Verifique se criou os JSONs em `seed_data/`
- Nomes devem ser exatamente: `original_exam_011.json`, etc.

### ❌ "JSON inválido"
- Valide o JSON em https://jsonlint.com/
- Verifique vírgulas, chaves, aspas

### ❌ "Conexão recusada"
- Verifique se MongoDB URI está correta
- Teste conexão: `mongo "$mongo_felps"`

---

## 🎯 Após Importar:

Teste os endpoints do backend:

```bash
# 1. Rodar o backend
cd ../
mvn spring-boot:run

# 2. Testar em outro terminal
curl http://localhost:8082/api/simulados/original/stats
```

Deve retornar: `{"total_active_exams": 4, ...}`

---

## 📚 Documentação Adicional:

Leia o README.md na pasta `seed_data/` para:
- Formato detalhado dos JSONs
- Checklist de validação
- Dicas de criação
- Distribuição recomendada de dificuldades

