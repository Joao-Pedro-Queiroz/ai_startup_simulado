const { MongoClient } = require('mongodb');
const fs = require('fs');
const path = require('path');

// Configuração
const MONGO_URI = process.env.mongo_felps || process.env.MONGO_URI;
const DB_NAME = 'SatQuestions'; // Nome do seu database
const COLLECTION_NAME = 'original_exams';

if (!MONGO_URI) {
  console.error('❌ ERRO: Variável de ambiente mongo_felps não configurada!');
  console.log('');
  console.log('Configure com:');
  console.log('  export mongo_felps="sua_uri_mongodb"');
  console.log('');
  process.exit(1);
}

async function importExams() {
  const client = new MongoClient(MONGO_URI);
  
  try {
    console.log('═══════════════════════════════════════════════');
    console.log('🚀 Import de Simulados Originais para MongoDB');
    console.log('═══════════════════════════════════════════════');
    console.log('');
    console.log(`📊 Database: ${DB_NAME}`);
    console.log(`📁 Collection: ${COLLECTION_NAME}`);
    console.log('');
    
    console.log('🔌 Conectando ao MongoDB...');
    await client.connect();
    console.log('✅ Conectado com sucesso!\n');
    
    const db = client.db(DB_NAME);
    const collection = db.collection(COLLECTION_NAME);
    
    // Verificar quantos já existem
    const existingCount = await collection.countDocuments();
    console.log(`📊 Simulados existentes: ${existingCount}`);
    
    if (existingCount > 0) {
      console.log('⚠️  ATENÇÃO: Já existem simulados na collection!');
      console.log('   Os simulados serão SUBSTITUÍDOS se tiverem o mesmo exam_id');
      console.log('');
    }
    
    // Importar os simulados disponíveis
    const seedPath = path.join(__dirname, '../src/main/resources/seed_data');
    const examFiles = [
      'original_exam_001.json',
      'original_exam_011.json',
      'original_exam_012.json',
      'original_exam_013.json',
      'original_exam_014.json'
    ];
    
    let imported = 0;
    let skipped = 0;
    
    for (const filename of examFiles) {
      const filePath = path.join(seedPath, filename);
      
      if (!fs.existsSync(filePath)) {
        console.log(`⏭️  Pulando ${filename} (arquivo não encontrado)`);
        skipped++;
        continue;
      }
      
      console.log(`\n📝 Processando ${filename}...`);
      
      try {
        const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
        
        // Validações básicas
        if (!data.exam_id) {
          console.log(`   ❌ ERRO: exam_id não definido em ${filename}`);
          skipped++;
          continue;
        }
        
        // Verificar estrutura (adaptativa OU fixa)
        const isAdaptive = data.is_adaptive === true;
        const hasModules = data.module_1 && data.module_2_easy && data.module_2_hard;
        const hasQuestions = data.questions && data.questions.length > 0;
        
        if (!hasModules && !hasQuestions) {
          console.log(`   ❌ ERRO: Nenhuma questão encontrada em ${filename}`);
          console.log(`   💡 Esperado: module_1/module_2_easy/module_2_hard OU questions[]`);
          skipped++;
          continue;
        }
        
        console.log(`   📊 exam_id: ${data.exam_id}`);
        
        if (isAdaptive && hasModules) {
          const m1 = data.module_1 ? data.module_1.length : 0;
          const m2e = data.module_2_easy ? data.module_2_easy.length : 0;
          const m2h = data.module_2_hard ? data.module_2_hard.length : 0;
          const total = m1 + m2e + m2h;
          console.log(`   🧩 Tipo: ADAPTATIVO`);
          console.log(`   📚 Módulo 1: ${m1} questões`);
          console.log(`   📘 Módulo 2 Easy: ${m2e} questões`);
          console.log(`   📕 Módulo 2 Hard: ${m2h} questões`);
          console.log(`   🎯 Total: ${total} questões`);
          console.log(`   ⚡ Threshold: ${data.metadata?.threshold || 'N/A'}`);
        } else {
          console.log(`   🧩 Tipo: FIXO`);
          console.log(`   📚 Questões: ${data.questions.length}`);
          console.log(`   🎯 Nível: ${data.difficulty_level || 'N/A'}`);
        }
        
        // Adicionar timestamp e is_active se não existirem
        if (!data.created_at) {
          data.created_at = new Date();
        }
        if (data.is_active === undefined) {
          data.is_active = true;
        }
        
        // Verificar se já existe
        const existing = await collection.findOne({ exam_id: data.exam_id });
        
        if (existing) {
          console.log(`   🔄 Atualizando simulado existente...`);
          await collection.replaceOne({ exam_id: data.exam_id }, data);
          console.log(`   ✅ ${data.exam_id} atualizado!`);
        } else {
          console.log(`   ➕ Inserindo novo simulado...`);
          await collection.insertOne(data);
          console.log(`   ✅ ${data.exam_id} inserido!`);
        }
        
        imported++;
        
      } catch (error) {
        console.log(`   ❌ ERRO ao processar ${filename}:`, error.message);
        skipped++;
      }
    }
    
    // Verificar total final
    const finalCount = await collection.countDocuments();
    
    console.log('');
    console.log('═══════════════════════════════════════════════');
    console.log('🎉 Import Concluído!');
    console.log('═══════════════════════════════════════════════');
    console.log(`✅ Importados: ${imported}`);
    console.log(`⏭️  Pulados: ${skipped}`);
    console.log(`📊 Total na collection: ${finalCount}`);
    console.log('═══════════════════════════════════════════════');
    console.log('');
    
    const expected = examFiles.length;
    if (finalCount === expected) {
      console.log(`🎯 Perfeito! Todos os ${expected} simulados estão no MongoDB!`);
    } else if (finalCount < expected) {
      console.log(`⚠️  Atenção: Esperava ${expected} simulados, mas encontrou ${finalCount}`);
      console.log('   Verifique se todos os arquivos JSON foram criados');
    } else {
      console.log(`⚠️  Atenção: Encontrou ${finalCount} simulados (esperava ${expected})`);
      console.log('   Pode ter simulados duplicados ou extras');
    }
    
    console.log('');
    console.log('🚀 Próximo passo: Testar os endpoints da API!');
    console.log('');
    
  } catch (error) {
    console.error('');
    console.error('═══════════════════════════════════════════════');
    console.error('❌ ERRO DURANTE IMPORT');
    console.error('═══════════════════════════════════════════════');
    console.error(error);
    console.error('═══════════════════════════════════════════════');
    console.error('');
    process.exit(1);
  } finally {
    await client.close();
    console.log('🔌 Conexão com MongoDB fechada.');
  }
}

// Executar
importExams().catch(console.error);

