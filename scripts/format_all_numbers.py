#!/usr/bin/env python3
"""
Script para formatar TODOS os números em LaTeX nos exames originais.
Formata números que aparecem em texto corrido (question, hint, solution, options).
"""

import json
import re
import sys
from pathlib import Path

def is_number_in_latex(text, pos):
    """Verifica se a posição está dentro de um bloco LaTeX."""
    before = text[:pos]
    
    # Contar aberturas e fechamentos LaTeX antes da posição
    opens = before.count('\\(') + before.count('\\[')
    closes = before.count('\\)') + before.count('\\]')
    
    return opens > closes

def format_numbers_in_text(text):
    """
    Formata números em texto para LaTeX, evitando números já formatados.
    """
    if not text or not isinstance(text, str):
        return text
    
    # Não processar se é um campo JSON estrutural
    if any(keyword in text for keyword in ['question_number', 'correct_option', 'version', 'total_questions', 
                                           'module_1_questions', 'module_2_questions', 'threshold', 
                                           'duration_minutes', 'coords', 'xRange', 'yRange', 'tickStep',
                                           'through', 'label', 'position', 'showGrid', 'showAxes', 'showTicks']):
        return text
    
    # Padrão para encontrar números (inteiros, decimais, com vírgulas)
    # \b garante que é uma palavra completa
    pattern = r'\b\d{1,3}(?:,\d{3})*(?:\.\d+)?\b|\b\d+\.\d+\b|\b\d+\b'
    
    # Encontrar todas as ocorrências
    matches = list(re.finditer(pattern, text))
    
    if not matches:
        return text
    
    # Construir resultado substituindo de trás para frente (para manter índices corretos)
    result = text
    for match in reversed(matches):
        num = match.group(0)
        start, end = match.span()
        
        # Verificar se está dentro de LaTeX
        if is_number_in_latex(text, start):
            continue
        
        # Verificar contexto: não formatar se está logo após \( ou \[
        if start > 0:
            before_char = text[start-2:start] if start >= 2 else text[:start]
            if before_char.endswith('\\(') or before_char.endswith('\\['):
                continue
        
        # Verificar contexto: não formatar se está logo antes de \)
        if end < len(text):
            after_char = text[end:end+2] if end+2 <= len(text) else text[end:]
            if after_char.startswith('\\)') or after_char.startswith('\\]'):
                continue
        
        # Formatar o número
        # Se tem vírgula, usar {,} no LaTeX
        formatted_num = num.replace(',', '{,}')
        result = result[:start] + f'\\({formatted_num}\\)' + result[end:]
    
    return result

def process_question(question):
    """Processa uma questão e formata números."""
    modified = False
    
    # Processar question
    if 'question' in question:
        new_question = format_numbers_in_text(question['question'])
        if new_question != question['question']:
            question['question'] = new_question
            modified = True
    
    # Processar hint
    if 'hint' in question:
        new_hint = format_numbers_in_text(question['hint'])
        if new_hint != question['hint']:
            question['hint'] = new_hint
            modified = True
    
    # Processar solution
    if 'solution' in question and isinstance(question['solution'], list):
        new_solution = []
        for sol_line in question['solution']:
            new_line = format_numbers_in_text(sol_line)
            new_solution.append(new_line)
            if new_line != sol_line:
                modified = True
        question['solution'] = new_solution
    
    # Processar options (mas manter números em arrays LaTeX intactos)
    if 'options' in question and isinstance(question['options'], dict):
        new_options = {}
        for key, value in question['options'].items():
            if isinstance(value, str):
                # Verificar se já está em LaTeX (array, etc)
                if '\\[' in value or '\\begin{' in value:
                    new_options[key] = value
                else:
                    new_val = format_numbers_in_text(value)
                    new_options[key] = new_val
                    if new_val != value:
                        modified = True
            else:
                new_options[key] = value
        question['options'] = new_options
    
    return modified

def process_exam_file(filepath):
    """Processa um arquivo de exame e formata números."""
    print(f"\n{'='*60}")
    print(f"Processando {filepath.name}...")
    print(f"{'='*60}")
    
    with open(filepath, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    total_modified = 0
    
    # Processar cada módulo
    for module_key in ['module_1', 'module_2_easy', 'module_2_hard']:
        if module_key not in data:
            continue
        
        module_modified = 0
        for question in data[module_key]:
            if process_question(question):
                module_modified += 1
                total_modified += 1
        
        if module_modified > 0:
            print(f"  {module_key}: {module_modified} questões modificadas")
    
    if total_modified > 0:
        # Fazer backup
        backup_path = filepath.with_suffix('.json.bak')
        with open(backup_path, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"\n  ✓ Backup criado: {backup_path.name}")
        
        # Salvar arquivo modificado
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"  ✓ Arquivo atualizado: {total_modified} questões modificadas")
    else:
        print(f"  ℹ Nenhuma modificação necessária")
    
    return total_modified

if __name__ == '__main__':
    script_dir = Path(__file__).parent
    seed_data_dir = script_dir.parent / 'src' / 'main' / 'resources' / 'seed_data'
    
    exam_files = sorted(seed_data_dir.glob('original_exam_00[1-4].json'))
    
    if not exam_files:
        print("❌ Nenhum arquivo de exame encontrado!")
        sys.exit(1)
    
    print(f"📝 Encontrados {len(exam_files)} arquivos de exame")
    print(f"📂 Diretório: {seed_data_dir}")
    
    total_all = 0
    for exam_file in exam_files:
        try:
            modified = process_exam_file(exam_file)
            total_all += modified
        except Exception as e:
            print(f"  ❌ Erro ao processar {exam_file.name}: {e}")
            import traceback
            traceback.print_exc()
    
    print(f"\n{'='*60}")
    print(f"✅ Processamento completo!")
    print(f"📊 Total de questões modificadas: {total_all}")
    print(f"{'='*60}\n")

