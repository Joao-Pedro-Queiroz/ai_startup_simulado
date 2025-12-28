const fs = require('fs');
const path = require('path');

const seedPath = path.join(__dirname, '../src/main/resources/seed_data');
const examFiles = [
  'original_exam_011.json',
  'original_exam_012.json',
  'original_exam_013.json',
  'original_exam_014.json'
];

console.log('═══════════════════════════════════════════════');
console.log('🔍 Validando Simulados Originais');
console.log('═══════════════════════════════════════════════\n');

let totalErrors = 0;
let totalWarnings = 0;

examFiles.forEach((filename, index) => {
  const filePath = path.join(seedPath, filename);
  
  console.log(`\n📝 Validando ${filename}...`);
  console.log('─────────────────────────────────────────────');
  
  if (!fs.existsSync(filePath)) {
    console.log(`❌ ERRO: Arquivo não encontrado!`);
    totalErrors++;
    return;
  }
  
  try {
    const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    
    // Validações obrigatórias
    const errors = [];
    const warnings = [];
    
    // Validar exam_id
    if (!data.exam_id) {
      errors.push('exam_id não definido');
    } else if (!/^SAT_ORIGINAL_\d{3}$/.test(data.exam_id)) {
      warnings.push(`exam_id "${data.exam_id}" não segue padrão SAT_ORIGINAL_XXX`);
    }
    
    // Validar name
    if (!data.name) {
      errors.push('name não definido');
    }
    
    // Validar is_active
    if (data.is_active !== true && data.is_active !== false) {
      errors.push('is_active deve ser true ou false');
    }
    
    // Validar metadata
    if (!data.metadata) {
      errors.push('metadata não definido');
    } else {
      if (!data.metadata.total_questions) {
        errors.push('metadata.total_questions não definido');
      }
      if (!data.metadata.duration_minutes) {
        warnings.push('metadata.duration_minutes não definido');
      }
    }
    
    // Validar estrutura (adaptativo vs fixo)
    const isAdaptive = data.is_adaptive === true;
    
    if (isAdaptive) {
      console.log(`🔄 Modo: ADAPTATIVO`);
      
      // Validar módulos
      if (!data.module_1 || !Array.isArray(data.module_1)) {
        errors.push('module_1 deve ser um array');
      } else {
        console.log(`📊 Módulo 1: ${data.module_1.length} questões`);
        if (data.module_1.length !== 22) {
          errors.push(`Módulo 1: esperava 22 questões, encontrou ${data.module_1.length}`);
        }
      }
      
      if (!data.module_2_easy || !Array.isArray(data.module_2_easy)) {
        errors.push('module_2_easy deve ser um array');
      } else {
        console.log(`📊 Módulo 2 Easy: ${data.module_2_easy.length} questões`);
        if (data.module_2_easy.length !== 22) {
          errors.push(`Módulo 2 Easy: esperava 22 questões, encontrou ${data.module_2_easy.length}`);
        }
      }
      
      if (!data.module_2_hard || !Array.isArray(data.module_2_hard)) {
        errors.push('module_2_hard deve ser um array');
      } else {
        console.log(`📊 Módulo 2 Hard: ${data.module_2_hard.length} questões`);
        if (data.module_2_hard.length !== 22) {
          errors.push(`Módulo 2 Hard: esperava 22 questões, encontrou ${data.module_2_hard.length}`);
        }
      }
      
      // Validar threshold
      if (!data.metadata || !data.metadata.threshold) {
        warnings.push('metadata.threshold não definido (padrão: 16)');
      }
      
      // Validar questões de cada módulo
      const allModules = [
        { name: 'Módulo 1', questions: data.module_1 || [] },
        { name: 'Módulo 2 Easy', questions: data.module_2_easy || [] },
        { name: 'Módulo 2 Hard', questions: data.module_2_hard || [] }
      ];
      
      allModules.forEach(module => {
        module.questions.forEach((q, idx) => {
          const qErrors = [];
          
          if (!q.topic) qErrors.push('topic');
          if (!q.subskill) qErrors.push('subskill');
          if (!q.difficulty) qErrors.push('difficulty');
          if (!q.question) qErrors.push('question');
          if (!q.correct_option) qErrors.push('correct_option');
          if (!q.format) qErrors.push('format');
          
          if (q.format === 'multiple_choice' && !q.options) {
            qErrors.push('options');
          }
          
          if (qErrors.length > 0) {
            errors.push(`${module.name} - Q${idx + 1}: faltando ${qErrors.join(', ')}`);
          }
        });
      });
      
    } else {
      // Modo FIXO (antigo - compatibilidade)
      console.log(`📋 Modo: FIXO (44 questões)`);
      
      if (!data.questions || !Array.isArray(data.questions)) {
        errors.push('questions deve ser um array');
      } else {
        const questionCount = data.questions.length;
        console.log(`📊 Total de questões: ${questionCount}`);
        
        if (questionCount !== 44) {
          errors.push(`Esperava 44 questões, encontrou ${questionCount}`);
        }
      }
    }
    
    // Mostrar resultados
    console.log(`\n📋 Resumo:`);
    console.log(`   exam_id: ${data.exam_id || 'N/A'}`);
    console.log(`   Nome: ${data.name || 'N/A'}`);
    console.log(`   Ativo: ${data.is_active ? 'Sim' : 'Não'}`);
    console.log(`   Dificuldade: ${data.difficulty_level || 'N/A'}`);
    
    if (errors.length === 0 && warnings.length === 0) {
      console.log('\n✅ VÁLIDO - Sem erros ou avisos!');
    } else {
      if (errors.length > 0) {
        console.log(`\n❌ ERROS (${errors.length}):`);
        errors.forEach(err => console.log(`   • ${err}`));
        totalErrors += errors.length;
      }
      if (warnings.length > 0) {
        console.log(`\n⚠️  AVISOS (${warnings.length}):`);
        warnings.forEach(warn => console.log(`   • ${warn}`));
        totalWarnings += warnings.length;
      }
    }
    
  } catch (error) {
    console.log(`❌ ERRO: JSON inválido - ${error.message}`);
    totalErrors++;
  }
});

console.log('\n═══════════════════════════════════════════════');
console.log('📊 Resumo Final da Validação');
console.log('═══════════════════════════════════════════════');
console.log(`Arquivos validados: ${examFiles.length}`);
console.log(`Total de erros: ${totalErrors}`);
console.log(`Total de avisos: ${totalWarnings}`);
console.log('═══════════════════════════════════════════════\n');

if (totalErrors === 0) {
  console.log('🎉 Todos os simulados estão válidos!');
  console.log('✅ Pronto para importar para o MongoDB');
  console.log('');
  console.log('Execute:');
  console.log('  npm run import');
  console.log('');
} else {
  console.log('❌ Corrija os erros antes de importar!');
  process.exit(1);
}

